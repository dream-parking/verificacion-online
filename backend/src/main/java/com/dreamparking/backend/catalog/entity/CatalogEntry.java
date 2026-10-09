package com.dreamparking.backend.catalog.entity;

/** Entry of a code/label catalog shown in the mobile app. The code is the primary key and never changes. */
public interface CatalogEntry {

	String getCode();

	void setCode(String code);

	String getLabel();

	void setLabel(String label);

	Short getSortOrder();

	void setSortOrder(Short sortOrder);

	Boolean getActive();

	void setActive(Boolean active);

}
