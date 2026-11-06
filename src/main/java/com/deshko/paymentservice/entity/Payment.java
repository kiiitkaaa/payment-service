package com.deshko.paymentservice.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    private String id;

    @Field("order_id")
    private Long orderId;

    @Field("user_id")
    private Long userId;

    private PaymentStatus status;

    @CreatedDate
    private Instant timestamp;

    @Field(name = "payment_amount", targetType = FieldType.DECIMAL128)
    private BigDecimal paymentAmount;
}
