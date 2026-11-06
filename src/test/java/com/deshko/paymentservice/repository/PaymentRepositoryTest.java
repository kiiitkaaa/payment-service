package com.deshko.paymentservice.repository;

import com.deshko.paymentservice.config.MongoTestContainerConfig;
import com.deshko.paymentservice.entity.Payment;
import com.deshko.paymentservice.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

@DataMongoTest
@Import(MongoTestContainerConfig.class)
class PaymentRepositoryTest {
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

    @Autowired PaymentRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private Payment save(Long userId, Long orderId, PaymentStatus status, String amount, Instant ts) {
        return repository.save(Payment.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId).orderId(orderId).status(status)
                .paymentAmount(new BigDecimal(amount)).timestamp(ts).build());
    }

    @Test
    void findByFilter_byEachFilterAndCombination() {
        save(1L, 10L, PaymentStatus.SUCCESS, "10.00", FROM);
        save(1L, 11L, PaymentStatus.FAILED, "20.00", FROM);
        save(2L, 10L, PaymentStatus.SUCCESS, "30.00", FROM);

        assertThat(repository.findByFilter(1L, null, null)).hasSize(2);
        assertThat(repository.findByFilter(null, 10L, null)).hasSize(2);
        assertThat(repository.findByFilter(null, null, PaymentStatus.FAILED)).hasSize(1);
        assertThat(repository.findByFilter(1L, 10L, PaymentStatus.SUCCESS)).hasSize(1);
        assertThat(repository.findByFilter(3L, null, null)).isEmpty();
    }

    @Test
    void sumByUserIdAndPeriod_countsOnlyOwnSuccessfulPaymentsInsidePeriod() {
        Instant inside = FROM.plusSeconds(3600);
        save(1L, 10L, PaymentStatus.SUCCESS, "10.10", inside);
        save(1L, 11L, PaymentStatus.SUCCESS, "20.20", inside);
        save(1L, 12L, PaymentStatus.FAILED, "500.00", inside);
        save(2L, 13L, PaymentStatus.SUCCESS, "700.00", inside);
        save(1L, 14L, PaymentStatus.SUCCESS, "900.00", FROM.minusSeconds(1));

        assertThat(repository.sumByUserIdAndPeriod(1L, FROM, TO)).isEqualByComparingTo("30.30");
    }

    @Test
    void sumByPeriod_periodIsHalfOpen() {
        save(1L, 10L, PaymentStatus.SUCCESS, "1.00", FROM);
        save(2L, 11L, PaymentStatus.SUCCESS, "2.00", TO);
        save(3L, 12L, PaymentStatus.SUCCESS, "4.00", FROM.plusSeconds(1));

        assertThat(repository.sumByPeriod(FROM, TO)).isEqualByComparingTo("5.00");
    }

    @Test
    void sums_noPayments_returnZero() {
        assertThat(repository.sumByPeriod(FROM, TO)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(repository.sumByUserIdAndPeriod(1L, FROM, TO)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sum_keepsDecimalPrecision() {
        save(1L, 10L, PaymentStatus.SUCCESS, "0.10", FROM);
        save(1L, 11L, PaymentStatus.SUCCESS, "0.20", FROM);

        assertThat(repository.sumByUserIdAndPeriod(1L, FROM, TO)).isEqualByComparingTo("0.30");
    }
}
