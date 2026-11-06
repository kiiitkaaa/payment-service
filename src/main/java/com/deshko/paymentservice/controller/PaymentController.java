package com.deshko.paymentservice.controller;

import com.deshko.paymentservice.dto.PaymentRequestDto;
import com.deshko.paymentservice.dto.PaymentResponseDto;
import com.deshko.paymentservice.dto.PaymentSumResponseDto;
import com.deshko.paymentservice.entity.PaymentStatus;
import com.deshko.paymentservice.security.GatewayUser;
import com.deshko.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> create(
            @Valid @RequestBody PaymentRequestDto dto,
            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(user.id(), dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponseDto>> getPayments(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) PaymentStatus status) {
        return ResponseEntity.ok(paymentService.getPayments(userId, orderId, status));
    }

    @GetMapping("/me")
    public ResponseEntity<List<PaymentResponseDto>> getMyPayments(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) PaymentStatus status,
            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(paymentService.getPayments(user.id(), orderId, status));
    }

    @GetMapping("/total/me")
    public ResponseEntity<PaymentSumResponseDto> getMyTotal(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(paymentService.getTotalSumForUser(user.id(), from, to));
    }

    @GetMapping("/total")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentSumResponseDto> getTotalForAll(
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(paymentService.getTotalSumForAllUsers(from, to));
    }
}
