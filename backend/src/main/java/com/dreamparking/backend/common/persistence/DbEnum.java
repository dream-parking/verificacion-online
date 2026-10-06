package com.dreamparking.backend.common.persistence;

/**
 * Java enum backed by a PostgreSQL enum whose labels differ from the constant names
 * (the database keeps the original Spanish labels).
 */
public interface DbEnum {

	/** Label stored in the database. */
	String dbValue();

}
