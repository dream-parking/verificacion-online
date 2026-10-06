package com.dreamparking.backend.alert.dto;

import com.dreamparking.backend.alert.entity.AlertType;
import com.dreamparking.backend.alert.entity.enums.AlertCriticality;

public record AlertTypeResponse(String code, String description, AlertCriticality defaultCriticality) {

	public static AlertTypeResponse of(AlertType type) {
		return new AlertTypeResponse(type.getCode(), type.getDescription(), type.getDefaultCriticality());
	}

}
