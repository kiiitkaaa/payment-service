package com.deshko.paymentservice.repository;

import com.deshko.paymentservice.entity.Payment;
import com.deshko.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentRepositoryCustom {
    List<Payment> findByFilter(Long userId, Long orderId, PaymentStatus status);
    BigDecimal sumByUserIdAndPeriod(Long userId, Instant from, Instant to);
    BigDecimal sumByPeriod(Instant from, Instant to);
}
