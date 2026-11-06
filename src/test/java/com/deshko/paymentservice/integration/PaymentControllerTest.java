package com.deshko.paymentservice.integration;

import com.deshko.paymentservice.config.KafkaTestContainerConfig;
import com.deshko.paymentservice.config.MongoTestContainerConfig;
import com.deshko.paymentservice.entity.Payment;
import com.deshko.paymentservice.kafka.PaymentEventProducer;
import com.deshko.paymentservice.repository.PaymentRepository;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import org.testcontainers.kafka.KafkaContainer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({MongoTestContainerConfig.class, KafkaTestContainerConfig.class})
public class PaymentControllerTest {
    @RegisterExtension
    static WireMockExtension randomApi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("external.random-api.base-url", randomApi::baseUrl);
    }

    @Autowired MockMvc mockMvc;
    @Autowired PaymentRepository paymentRepository;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired KafkaContainer kafka;

    @BeforeEach
    void clean() {
        paymentRepository.deleteAll();
    }

    private static final String BODY = """
            {"orderId": %d, "paymentAmount": %s}""";

    @Test
    void create_evenNumber_savesSuccessPaymentForHeaderUser() throws Exception {
        stubRandom("42");

        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(1001, "100.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.orderId").value(1001))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        List<Payment> saved = paymentRepository.findByFilter(5L, 1001L, null);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTimestamp()).isNotNull();
    }

    @Test
    void create_oddNumber_savesFailedPayment() throws Exception {
        stubRandom("7");

        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(1002, "10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void create_publishesCreatePaymentEventToKafka() throws Exception {
        stubRandom("2");

        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(2001, "15.00")))
                .andExpect(status().isCreated());

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(PaymentEventProducer.TOPIC));
            List<ConsumerRecord<String, String>> mine = new ArrayList<>();

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500))
                        .forEach(r -> { if ("2001".equals(r.key())) mine.add(r); });
                assertThat(mine).hasSize(1);
            });

            String json = mine.get(0).value();
            assertThat(json).contains("CREATE_PAYMENT").contains("\"orderId\":2001")
                    .contains("\"status\":\"SUCCESS\"");
        }
    }

    @Test
    void create_withoutGatewayHeaders_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(1, "10")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_invalidAmount_returns400() throws Exception {
        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(1, "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_randomApiDown_returns503AndSavesNothing() throws Exception {
        stubRandomError();

        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(3001, "10")))
                .andExpect(status().isServiceUnavailable());

        assertThat(paymentRepository.findByFilter(null, 3001L, null)).isEmpty();
    }

    @Test
    void getMyPayments_returnsOnlyOwn() throws Exception {
        stubRandom("2");
        for (long user : new long[]{5, 5, 6}) {
            mockMvc.perform(asUser(post("/api/v1/payments"), user, "USER")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.formatted(4000 + user, "10")));
        }

        mockMvc.perform(asUser(get("/api/v1/payments/me"), 5, "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].userId", everyItem(is(5))));
    }

    @Test
    void getTotalMe_sumsOnlySuccessfulPayments() throws Exception {
        stubRandom("2");
        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(5001, "10.50")));
        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(5002, "20.25")));
        stubRandom("1");
        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(5003, "999")));

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(asUser(get("/api/v1/payments/total/me"), 5, "USER")
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(30.75));
    }

    @Test
    void getTotalForAll_forbiddenForUser_allowedForAdmin() throws Exception {
        String from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        String to = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        mockMvc.perform(asUser(get("/api/v1/payments/total"), 5, "USER")
                        .param("from", from).param("to", to))
                .andExpect(status().isForbidden());

        mockMvc.perform(asUser(get("/api/v1/payments/total"), 1, "ADMIN")
                        .param("from", from).param("to", to))
                .andExpect(status().isOk());
    }

    @Test
    void adminList_filtersByOrderId_andRequiresAtLeastOneFilter() throws Exception {
        stubRandom("2");
        mockMvc.perform(asUser(post("/api/v1/payments"), 5, "USER")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(6001, "10")));

        mockMvc.perform(asUser(get("/api/v1/payments"), 1, "ADMIN")
                        .param("orderId", "6001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(asUser(get("/api/v1/payments"), 1, "ADMIN"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asUser(get("/api/v1/payments"), 5, "USER")
                        .param("orderId", "6001"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mongockMigration_createdPaymentsIndexes() {
        List<String> names = mongoTemplate.indexOps("payments").getIndexInfo()
                .stream().map(IndexInfo::getName).toList();

        assertThat(names).contains(
                "idx_payments_user_id", "idx_payments_order_id", "idx_payments_status",
                "idx_payments_user_id_timestamp", "idx_payments_timestamp");
    }

    private void stubRandom(String number) {
        randomApi.stubFor(WireMock.get(urlPathEqualTo("/integers/")).willReturn(ok(number)));
    }

    private void stubRandomError() {
        randomApi.stubFor(WireMock.get(urlPathEqualTo("/integers/")).willReturn(serverError()));
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b, long id, String role) {
        return b.header("X-User-Id", id)
                .header("X-User-Role", role)
                .header("X-User-Email", "user" + id + "@test.com");
    }
}
