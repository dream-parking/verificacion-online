package com.dreamparking.backend.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.alert.entity.enums.AlertCriticality;

/** Catalog of alert types with their default criticality. */
@Entity
@Table(name = "alert_type")
public class AlertType {

	@Id
	@Column(name = "code", length = 40)
	private String code;

	@Column(name = "description", nullable = false, length = 200)
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "default_criticality", nullable = false, columnDefinition = "alert_criticality")
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
