package com.deshko.paymentservice.service;

import com.deshko.paymentservice.client.RandomNumberClient;
import com.deshko.paymentservice.dto.PaymentRequestDto;
import com.deshko.paymentservice.dto.PaymentResponseDto;
import com.deshko.paymentservice.dto.PaymentSumResponseDto;
import com.deshko.paymentservice.entity.Payment;
import com.deshko.paymentservice.entity.PaymentStatus;
import com.deshko.paymentservice.exception.InvalidRequestException;
import com.deshko.paymentservice.exception.RandomNumberUnavailableException;
import com.deshko.paymentservice.kafka.PaymentEventProducer;
import com.deshko.paymentservice.mapper.PaymentMapper;
import com.deshko.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    @Mock PaymentRepository paymentRepository;
    @Mock RandomNumberClient randomNumberClient;
    @Mock PaymentEventProducer paymentEventProducer;
    @Spy PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);
    @InjectMocks PaymentService paymentService;

    private void stubSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId("p-1");
            p.setTimestamp(NOW);
            return p;
        });
    }

    @ParameterizedTest
    @CsvSource({"2,SUCCESS", "42,SUCCESS", "1,FAILED", "99,FAILED"})
    void createPayment_statusDependsOnNumberParity(int number, PaymentStatus expected) {
        when(randomNumberClient.getRandomNumber()).thenReturn(number);
        stubSave();

        PaymentResponseDto result = paymentService.createPayment(
                5L, new PaymentRequestDto(10L, new BigDecimal("100.50")));

        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.userId()).isEqualTo(5L);
        assertThat(result.orderId()).isEqualTo(10L);
        assertThat(result.paymentAmount()).isEqualByComparingTo("100.50");
        assertThat(result.id()).isEqualTo("p-1");
    }

    @Test
    void createPayment_sendsEventWithSavedPayment() {
        when(randomNumberClient.getRandomNumber()).thenReturn(2);
        stubSave();

        paymentService.createPayment(5L, new PaymentRequestDto(10L, BigDecimal.TEN));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentEventProducer).sendCreatePayment(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("p-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void createPayment_randomApiUnavailable_nothingSavedNoEvent() {
        when(randomNumberClient.getRandomNumber())
                .thenThrow(new RandomNumberUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> paymentService.createPayment(
                5L, new PaymentRequestDto(10L, BigDecimal.TEN)))
                .isInstanceOf(RandomNumberUnavailableException.class);

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    void getPayments_noFilters_throws() {
        assertThatThrownBy(() -> paymentService.getPayments(null, null, null))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void getPayments_delegatesToRepositoryAndMaps() {
        Payment p = Payment.builder().id("p-1").userId(1L).orderId(2L)
                .status(PaymentStatus.FAILED).paymentAmount(BigDecimal.ONE).timestamp(NOW).build();
        when(paymentRepository.findByFilter(1L, null, PaymentStatus.FAILED))
                .thenReturn(List.of(p));

        List<PaymentResponseDto> result = paymentService.getPayments(1L, null, PaymentStatus.FAILED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void getTotalSumForUser_returnsRepositoryValue() {
        Instant from = NOW.minusSeconds(3600);
        when(paymentRepository.sumByUserIdAndPeriod(5L, from, NOW))
                .thenReturn(new BigDecimal("30.00"));

        PaymentSumResponseDto result = paymentService.getTotalSumForUser(5L, from, NOW);

        assertThat(result.total()).isEqualByComparingTo("30.00");
        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(NOW);
    }

    @Test
    void getTotalSumForUser_nullUserId_throws() {
        assertThatThrownBy(() ->
                paymentService.getTotalSumForUser(null, NOW.minusSeconds(1), NOW))
                .isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void totals_invalidPeriod_throws(boolean equalBounds) {
        Instant from = NOW;
        Instant to = equalBounds ? NOW : NOW.minusSeconds(1);

        assertThatThrownBy(() -> paymentService.getTotalSumForAllUsers(from, to))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> paymentService.getTotalSumForUser(5L, from, to))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void getTotalSumForAllUsers_returnsRepositoryValue() {
        Instant from = NOW.minusSeconds(3600);
        when(paymentRepository.sumByPeriod(from, NOW)).thenReturn(new BigDecimal("99.99"));

        assertThat(paymentService.getTotalSumForAllUsers(from, NOW).total())
                .isEqualByComparingTo("99.99");
    }
}
