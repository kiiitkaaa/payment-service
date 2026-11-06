package com.deshko.paymentservice.service;

import com.deshko.paymentservice.client.RandomNumberClient;
import com.deshko.paymentservice.dto.PaymentRequestDto;
import com.deshko.paymentservice.dto.PaymentResponseDto;
import com.deshko.paymentservice.dto.PaymentSumResponseDto;
import com.deshko.paymentservice.entity.Payment;
import com.deshko.paymentservice.entity.PaymentStatus;
import com.deshko.paymentservice.exception.InvalidRequestException;
import com.deshko.paymentservice.mapper.PaymentMapper;
import com.deshko.paymentservice.repository.PaymentRepository;
import com.deshko.paymentservice.kafka.PaymentEventProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.time.Instant;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RandomNumberClient randomNumberClient;
    private final PaymentEventProducer  paymentEventProducer;

    public PaymentResponseDto createPayment(Long userId, @Valid PaymentRequestDto dto) {
        Payment payment = paymentMapper.toEntity(dto);
        payment.setUserId(userId);
        payment.setStatus(randomNumberClient.getRandomNumber() % 2 == 0
                ? PaymentStatus.SUCCESS
                : PaymentStatus.FAILED);

        Payment saved = paymentRepository.save(payment);
        paymentEventProducer.sendCreatePayment(saved);
        return paymentMapper.toDto(saved);
    }

    public List<PaymentResponseDto> getPayments(Long userId, Long orderId, PaymentStatus status) {
        if (userId == null && orderId == null && status == null) {
            throw new InvalidRequestException("At least one of userId, orderId or status must be provided");
        }
        return paymentMapper.toDtoList(paymentRepository.findByFilter(userId, orderId, status));
    }

    public PaymentSumResponseDto getTotalSumForUser(Long userId, Instant from, Instant to) {
        validatePeriod(from, to);
        if (userId == null) {
            throw new InvalidRequestException("userId must be provided");
        }
        return new PaymentSumResponseDto(
                paymentRepository.sumByUserIdAndPeriod(userId, from, to), from, to);
    }

    public PaymentSumResponseDto getTotalSumForAllUsers(Instant from, Instant to) {
        validatePeriod(from, to);
        return new PaymentSumResponseDto(paymentRepository.sumByPeriod(from, to), from, to);
    }

    private void validatePeriod(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new InvalidRequestException("'from' must be before 'to'");
        }
    }
}
