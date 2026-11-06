package com.deshko.paymentservice.client;

import com.deshko.paymentservice.exception.RandomNumberUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class RandomNumberClient {
    private final RestClient restClient;

    public RandomNumberClient(@Value("${external.random-api.base-url}") String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public int getRandomNumber() {
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/integers/")
                            .queryParam("num", 1)
                            .queryParam("min", 1)
                            .queryParam("max", 100)
                            .queryParam("col", 1)
                            .queryParam("base", 10)
                            .queryParam("format", "plain")
                            .queryParam("rnd", "new")
                            .build())
                    .retrieve()
                    .body(String.class);
            return Integer.parseInt(body.trim());
        } catch (RestClientException | NumberFormatException | NullPointerException e) {
            throw new RandomNumberUnavailableException("Random number API is unavailable", e);
        }
    }
}
