package com.deshko.paymentservice.config;

import com.deshko.paymentservice.kafka.PaymentEventProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    NewTopic createPaymentTopic() {
        return TopicBuilder.name(PaymentEventProducer.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
