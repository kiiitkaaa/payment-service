package com.deshko.paymentservice.kafka;

import com.deshko.paymentservice.dto.CreatePaymentEvent;
import com.deshko.paymentservice.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {
    public static final String TOPIC = "create-payment";
    private final KafkaTemplate<String, CreatePaymentEvent> kafkaTemplate;

    public void sendCreatePayment(Payment payment) {
        CreatePaymentEvent event = new CreatePaymentEvent(
                "CREATE_PAYMENT",
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getStatus(),
                payment.getPaymentAmount(),
                payment.getTimestamp());

        kafkaTemplate.send(TOPIC, String.valueOf(payment.getOrderId()), event)
                .whenComplete((_, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send CREATE_PAYMENT for payment {}", payment.getId(), ex);
                    }
                });
    }
}
