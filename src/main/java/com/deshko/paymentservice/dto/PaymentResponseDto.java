package com.deshko.paymentservice.dto;

import com.deshko.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponseDto(
        String id,
        Long orderId,
        Long userId,
        PaymentStatus status,
        Instant timestamp,
        BigDecimal paymentAmount
) {}
