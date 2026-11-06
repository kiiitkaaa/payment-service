package com.deshko.paymentservice;

import com.deshko.paymentservice.config.KafkaTestContainerConfig;
import com.deshko.paymentservice.config.MongoTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({MongoTestContainerConfig.class, KafkaTestContainerConfig.class})
class PaymentServiceApplicationTests {
    @Test
    void contextLoads() {}
}
