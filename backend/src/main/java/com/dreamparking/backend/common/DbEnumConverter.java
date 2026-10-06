package com.dreamparking.backend.common;

import jakarta.persistence.AttributeConverter;

/**
 * Maps a {@link DbEnum} to its database label. Entity columns pair it with
 * {@code @JdbcType(PgEnumJdbcType.class)} so PostgreSQL casts the label to its enum type.
 */
public abstract class DbEnumConverter<E extends Enum<E> & DbEnum> implements AttributeConverter<E, String> {

	private final Class<E> type;

	protected DbEnumConverter(Class<E> type) {
		this.type = type;
	}

	@Override
	public String convertToDatabaseColumn(E value) {
		return value == null ? null : value.dbValue();
	}

	@Override
	public E convertToEntityAttribute(String dbValue) {
		if (dbValue == null) {
			return null;
		}
		for (E value : type.getEnumConstants()) {
			if (value.dbValue().equals(dbValue)) {
				return value;
			}
		}
		throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + dbValue);
	}

}
