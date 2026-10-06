package com.dreamparking.backend.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.InvalidInputException;

@Service
@Transactional(readOnly = true)
public class CatalogService {

	private final IncomeSourceRepository incomeSources;

	private final IncomeRangeRepository incomeRanges;

	private final TransactionTypeRepository transactionTypes;

	private final MonthlyAmountRangeRepository monthlyAmountRanges;

	public CatalogService(IncomeSourceRepository incomeSources, IncomeRangeRepository incomeRanges,
			TransactionTypeRepository transactionTypes,
			MonthlyAmountRangeRepository monthlyAmountRanges) {
		this.incomeSources = incomeSources;
		this.incomeRanges = incomeRanges;
		this.transactionTypes = transactionTypes;
		this.monthlyAmountRanges = monthlyAmountRanges;
	}

	public CatalogsResponse activeCatalogs() {
		return new CatalogsResponse(
				incomeSources.findByActiveTrueOrderBySortOrder().stream().map(CatalogItem::of).toList(),
				incomeRanges.findByActiveTrueOrderBySortOrder().stream().map(CatalogItem::of).toList(),
				transactionTypes.findByActiveTrueOrderBySortOrder().stream().map(CatalogItem::of).toList(),
				monthlyAmountRanges.findByActiveTrueOrderBySortOrder().stream().map(CatalogItem::of).toList());
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

	public MonthlyAmountRange activeMonthlyAmountRange(String code) {
		return monthlyAmountRanges.findById(code)
			.filter(MonthlyAmountRange::getActive)
			.orElseThrow(() -> new InvalidInputException("Unknown monthly amount range: " + code));
	}

	public TransactionType activeTransactionType(String code) {
		return transactionTypes.findById(code)
			.filter(TransactionType::getActive)
			.orElseThrow(() -> new InvalidInputException("Unknown transaction type: " + code));
	}

}
