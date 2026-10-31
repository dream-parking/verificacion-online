import 'package:flutter/material.dart';

import '../api/onboarding_api.dart';
import '../theme.dart';

/// Conditions of the accounts side by side (VDI-147). Each card holds the same rows in the same order, so the person
/// compares them across; tapping one chooses it. Only what the API sends is shown, nothing about why it qualifies.
class ProductsComparison extends StatelessWidget {
  const ProductsComparison({super.key, required this.products, required this.selectedCode, required this.onSelect});

  final List<Product> products;
  final String? selectedCode;
  final ValueChanged<Product> onSelect;

  static const _cardWidth = 250.0;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final (i, product) in products.indexed) ...[
              if (i > 0) const SizedBox(width: 12),
              SizedBox(
                width: _cardWidth,
                child: _ProductCard(
                  product: product,
                  selected: product.code == selectedCode,
                  onTap: () => onSelect(product),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  /// «0.50 %», or «—» when the API did not send it.
  static String percent(double? value) => value == null ? '—' : '${value.toStringAsFixed(2)} %';

  /// «Sin cuota» for a fee of zero, «USD 2.50» otherwise.
  static String fee(double? value) => switch (value) {
        null => '—',
        0 => 'Sin cuota',
        _ => 'USD ${value.toStringAsFixed(2)}',
      };

  /// «USD 10.00», or «—» when the API did not send it.
  static String usd(double? value) => value == null ? '—' : 'USD ${value.toStringAsFixed(2)}';
}

class _ProductCard extends StatelessWidget {
  const _ProductCard({required this.product, required this.selected, required this.onTap});

  final Product product;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      inMutuallyExclusiveGroup: true,
      checked: selected,
      child: Material(
        key: Key('product-${product.code}'),
        color: selected ? AppColors.brandSoft : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: selected
              ? const BorderSide(color: AppColors.brandGreen, width: 2)
              : const BorderSide(color: AppColors.borderStrong),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(child: Text(product.name, style: AppText.heading(20, height: 1.2))),
                    const SizedBox(width: 8),
                    Icon(
                      selected ? Icons.radio_button_checked : Icons.radio_button_unchecked,
                      color: selected ? AppColors.brandGreen : AppColors.muted,
                    ),
                  ],
                ),
                if (product.description.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(product.description, style: AppText.small),
                ],
                const SizedBox(height: 16),
                _Condition('Interés anual', ProductsComparison.percent(product.annualInterestRate)),
                _Condition('Cuota mensual', ProductsComparison.fee(product.monthlyFeeUsd)),
                _Condition('Depósito para abrir', ProductsComparison.usd(product.minimumOpeningDepositUsd)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Condition extends StatelessWidget {
  const _Condition(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppText.label),
          Text(value, style: AppText.bodyBold),
        ],
      ),
    );
  }
}
