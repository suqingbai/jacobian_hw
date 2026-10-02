package com.jacobian.orders.persistence;

import com.jacobian.orders.domain.Priority;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link Priority} to its {@code char(1)} code (R, U, S). */
@Converter(autoApply = true)
public class PriorityConverter implements AttributeConverter<Priority, Character> {

  @Override
  public Character convertToDatabaseColumn(Priority value) {
    return value == null ? null : value.code();
  }

  @Override
  public Priority convertToEntityAttribute(Character code) {
    return code == null ? null : Priority.fromCode(code);
  }
}
