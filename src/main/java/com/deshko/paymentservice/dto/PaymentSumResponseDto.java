package com.deshko.paymentservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSumResponseDto(
        BigDecimal total,
        Instant from,
        Instant to
) {}
