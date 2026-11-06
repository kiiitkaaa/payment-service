package com.deshko.paymentservice.repository.impl;

import com.deshko.paymentservice.entity.Payment;
import com.deshko.paymentservice.entity.PaymentStatus;
import com.deshko.paymentservice.repository.PaymentRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class PaymentRepositoryCustomImpl implements PaymentRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    @Override
    public List<Payment> findByFilter(Long userId, Long orderId, PaymentStatus status) {
        Query query = new Query();
        if (userId != null) {
            query.addCriteria(Criteria.where("userId").is(userId));
        }
        if (orderId != null) {
            query.addCriteria(Criteria.where("orderId").is(orderId));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        return mongoTemplate.find(query, Payment.class);
    }

    @Override
    public BigDecimal sumByUserIdAndPeriod(Long userId, Instant from, Instant to) {
        return sum(periodCriteria(from, to).and("userId").is(userId));
    }

    @Override
    public BigDecimal sumByPeriod(Instant from, Instant to) {
        return sum(periodCriteria(from, to));
    }

    private Criteria periodCriteria(Instant from, Instant to) {
        return Criteria.where("status").is(PaymentStatus.SUCCESS)
                .and("timestamp").gte(from).lt(to);
    }

    private BigDecimal sum(Criteria criteria) {
        TypedAggregation<Payment> aggregation = Aggregation.newAggregation(
                Payment.class,
                Aggregation.match(criteria),
                Aggregation.group().sum("paymentAmount").as("total"));

        SumResult result = mongoTemplate
                .aggregate(aggregation, SumResult.class)
                .getUniqueMappedResult();

        return result == null || result.total() == null ? BigDecimal.ZERO : result.total();
    }

    private record SumResult(BigDecimal total) {}
}
