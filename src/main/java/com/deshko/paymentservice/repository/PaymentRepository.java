package com.deshko.paymentservice.repository;

import com.deshko.paymentservice.entity.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository
        extends MongoRepository<Payment, String>, PaymentRepositoryCustom {
}
