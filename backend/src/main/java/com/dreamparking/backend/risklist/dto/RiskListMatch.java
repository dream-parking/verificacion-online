package com.dreamparking.backend.risklist.dto;

import com.dreamparking.backend.risklist.entity.RiskListEntry;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;
import com.dreamparking.backend.risklist.entity.enums.RiskListType;

/**
 * A piece of data of a request that is on an internal risk list: which list, its type and its weight, and which kind
 * of data matched. It carries neither the value nor its hash.
 */
public record RiskListMatch(Long entryId, String listCode, String listName, RiskListType listType, int weight,
		RiskListDataType dataType) {

	public static RiskListMatch of(RiskListEntry entry) {
		return new RiskListMatch(entry.getId(), entry.getList().getCode(), entry.getList().getName(),
				entry.getList().getType(), entry.getList().getWeight(), entry.getDataType());
	}

}
