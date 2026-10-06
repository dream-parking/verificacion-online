package com.dreamparking.backend.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.catalog.dto.CatalogItem;
import com.dreamparking.backend.catalog.dto.CatalogsResponse;
import com.dreamparking.backend.catalog.entity.IncomeRange;
import com.dreamparking.backend.catalog.entity.IncomeSource;
import com.dreamparking.backend.catalog.entity.TransactionType;
import com.dreamparking.backend.catalog.repository.IncomeRangeRepository;
import com.dreamparking.backend.catalog.repository.IncomeSourceRepository;
import com.dreamparking.backend.catalog.repository.TransactionTypeRepository;
import com.dreamparking.backend.common.exception.InvalidInputException;

@Service
@Transactional(readOnly = true)
public class CatalogService {

	private final IncomeSourceRepository incomeSources;

	private final IncomeRangeRepository incomeRanges;

	private final TransactionTypeRepository transactionTypes;

	public CatalogService(IncomeSourceRepository incomeSources, IncomeRangeRepository incomeRanges,
			TransactionTypeRepository transactionTypes) {
		this.incomeSources = incomeSources;
		this.incomeRanges = incomeRanges;
		this.transactionTypes = transactionTypes;
	}

	public CatalogsResponse activeCatalogs() {
		return new CatalogsResponse(
				incomeSources.findByActiveTrueOrderBySortOrder().stream().map(CatalogItem::of).toList(),
				incomeRanges.findByActiveTrueOrderBySortOrder().stream().map(CatalogItem::of).toList(),
				transactionTypes.findByActiveTrueOrderBySortOrder().stream().map(CatalogItem::of).toList());
	}

	public IncomeSource activeIncomeSource(String code) {
		return incomeSources.findById(code)
			.filter(IncomeSource::getActive)
			.orElseThrow(() -> new InvalidInputException("Unknown income source: " + code));
	}

	public IncomeRange activeIncomeRange(String code) {
		return incomeRanges.findById(code)
			.filter(IncomeRange::getActive)
			.orElseThrow(() -> new InvalidInputException("Unknown income range: " + code));
	}

	public TransactionType activeTransactionType(String code) {
		return transactionTypes.findById(code)
			.filter(TransactionType::getActive)
			.orElseThrow(() -> new InvalidInputException("Unknown transaction type: " + code));
	}

}
