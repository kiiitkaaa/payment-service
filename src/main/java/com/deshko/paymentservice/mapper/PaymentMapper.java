package com.deshko.paymentservice.mapper;

import com.deshko.paymentservice.dto.PaymentRequestDto;
import com.deshko.paymentservice.dto.PaymentResponseDto;
import com.deshko.paymentservice.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    Payment toEntity(PaymentRequestDto dto);

    PaymentResponseDto toDto(Payment payment);
    List<PaymentResponseDto> toDtoList(List<Payment> payments);
}
