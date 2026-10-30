package com.dreamparking.backend.outcome.entity.enums;

/** How a case ended once an analyst looked into it. */
public enum CaseOutcome {

	/** It was fraud: the data of the request goes on the internal risk list. */
	FRAUDE_CONFIRMADO,
	/** It was not fraud: only recorded, so false positives can be measured. Nothing goes on the lists. */
	FALSO_POSITIVO

}
