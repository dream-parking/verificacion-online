package com.dreamparking.backend.common.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

/**
 * Text column bound as {@link Types#OTHER}, so PostgreSQL casts the label to the column's enum type
 * in INSERT/UPDATE as well as in query parameters ({@code where status = :status}).
 */
public class PgEnumJdbcType extends VarcharJdbcType {

	@Override
	public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
		return new BasicBinder<>(javaType, this) {

			@Override
			protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
					throws SQLException {
				st.setObject(index, javaType.unwrap(value, String.class, options), Types.OTHER);
			}

			@Override
			protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
					throws SQLException {
				st.setObject(name, javaType.unwrap(value, String.class, options), Types.OTHER);
			}

		};
	}

}
