package com.dreamparking.backend.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcType;

import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.common.persistence.PgEnumJdbcType;

/** Catalog of alert types with their default criticality. */
@Entity
@Table(name = "tipo_alerta")
public class AlertType {

	@Id
	@Column(name = "codigo", length = 40)
	private String code;

	@Column(name = "descripcion", nullable = false, length = 200)
	private String description;

	@Convert(converter = AlertCriticality.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "criticidad_default", nullable = false, columnDefinition = "criticidad_alerta")
	private AlertCriticality defaultCriticality;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public AlertCriticality getDefaultCriticality() {
		return defaultCriticality;
	}

	public void setDefaultCriticality(AlertCriticality defaultCriticality) {
		this.defaultCriticality = defaultCriticality;
	}

}
