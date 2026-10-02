package com.jacobian.orders.persistence;

import com.jacobian.orders.domain.OrderType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link OrderType} to its {@code char(1)} code (L, I, M, C). */
@Converter(autoApply = true)
public class OrderTypeConverter implements AttributeConverter<OrderType, Character> {

  @Override
  public Character convertToDatabaseColumn(OrderType value) {
    return value == null ? null : value.code();
  }

  @Override
  public OrderType convertToEntityAttribute(Character code) {
    return code == null ? null : OrderType.fromCode(code);
  }
}
