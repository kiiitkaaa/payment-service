package com.deshko.paymentservice.client;

import com.deshko.paymentservice.exception.RandomNumberUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

public class RandomNumberClientTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private RandomNumberClient client() {
        return new RandomNumberClient(wireMock.baseUrl());
    }

    @Test
    void getRandomNumber_parsesPlainTextBody() {
        wireMock.stubFor(get(urlPathEqualTo("/integers/"))
                .withQueryParam("num", equalTo("1"))
                .withQueryParam("format", equalTo("plain"))
                .willReturn(ok("42\n")));

        assertThat(client().getRandomNumber()).isEqualTo(42);
    }

    @Test
    void getRandomNumber_serverError_throwsUnavailable() {
        wireMock.stubFor(get(urlPathEqualTo("/integers/")).willReturn(serverError()));

        assertThatThrownBy(() -> client().getRandomNumber())
                .isInstanceOf(RandomNumberUnavailableException.class);
    }

    @Test
    void getRandomNumber_nonNumericBody_throwsUnavailable() {
        wireMock.stubFor(get(urlPathEqualTo("/integers/")).willReturn(ok("not a number")));

        assertThatThrownBy(() -> client().getRandomNumber())
                .isInstanceOf(RandomNumberUnavailableException.class);
    }

    @Test
    void getRandomNumber_slowResponse_timesOut() {
        wireMock.stubFor(get(urlPathEqualTo("/integers/"))
                .willReturn(ok("1").withFixedDelay(4000)));

        assertThatThrownBy(() -> client().getRandomNumber())
                .isInstanceOf(RandomNumberUnavailableException.class);
    }
}
