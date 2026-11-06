package com.deshko.paymentservice.kafka;

import com.deshko.paymentservice.dto.CreatePaymentEvent;
import com.deshko.paymentservice.entity.Payment;
import com.deshko.paymentservice.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@ExtendWith(MockitoExtension.class)
class PaymentEventProducerTest {
    @Mock KafkaTemplate<String, CreatePaymentEvent> kafkaTemplate;
    @InjectMocks PaymentEventProducer producer;

    private Payment payment() {
        return Payment.builder().id("p-1").orderId(10L).userId(5L)
                .status(PaymentStatus.SUCCESS).paymentAmount(new BigDecimal("12.30"))
                .timestamp(Instant.parse("2026-11-05T10:00:00Z")).build();
    }

    @Test
    void sendCreatePayment_sendsEventKeyedByOrderId() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.sendCreatePayment(payment());

        ArgumentCaptor<CreatePaymentEvent> captor = ArgumentCaptor.forClass(CreatePaymentEvent.class);
        verify(kafkaTemplate).send(eq("create-payment"), eq("10"), captor.capture());
        CreatePaymentEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("CREATE_PAYMENT");
        assertThat(event.paymentId()).isEqualTo("p-1");
        assertThat(event.orderId()).isEqualTo(10L);
        assertThat(event.userId()).isEqualTo(5L);
        assertThat(event.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(event.paymentAmount()).isEqualByComparingTo("12.30");
    }

    @Test
    void sendCreatePayment_kafkaFailure_doesNotThrow() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatCode(() -> producer.sendCreatePayment(payment())).doesNotThrowAnyException();
    }
}
