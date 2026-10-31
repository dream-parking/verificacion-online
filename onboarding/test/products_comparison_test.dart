import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/onboarding/products_comparison.dart';

void main() {
  const savings = Product(
    code: 'AHORRO_BASICA',
    name: 'Cuenta de ahorro básica',
    description: 'Para empezar a ahorrar',
    annualInterestRate: 0.5,
    monthlyFeeUsd: 0,
    minimumOpeningDepositUsd: 10,
  );
  const bare = Product(code: 'CORRIENTE', name: 'Cuenta corriente');

  Widget host({String? selected, ValueChanged<Product>? onSelect}) => MaterialApp(
        home: Scaffold(
          body: ProductsComparison(products: const [savings, bare], selectedCode: selected, onSelect: onSelect ?? (_) {}),
        ),
      );

  test('conditions are written the way the person reads them', () {
    expect(ProductsComparison.percent(0.5), '0.50 %');
    expect(ProductsComparison.fee(0), 'Sin cuota');
    expect(ProductsComparison.fee(2.5), 'USD 2.50');
    expect(ProductsComparison.usd(100), 'USD 100.00');
    expect([ProductsComparison.percent(null), ProductsComparison.fee(null), ProductsComparison.usd(null)], everyElement('—'));
  });

  testWidgets('a condition the API did not send shows a dash, never zero', (tester) async {
    await tester.pumpWidget(host());
    expect(find.text('—'), findsNWidgets(3));
    expect(find.text('Sin cuota'), findsOneWidget, reason: 'only the account whose fee is really zero');
  });

  testWidgets('tapping an account reports it and the selected one is marked', (tester) async {
    Product? tapped;
    await tester.pumpWidget(host(onSelect: (p) => tapped = p));
    await tester.tap(find.byKey(const Key('product-AHORRO_BASICA')));
    expect(tapped?.code, 'AHORRO_BASICA');
    expect(find.byIcon(Icons.radio_button_unchecked), findsNWidgets(2));

    await tester.pumpWidget(host(selected: 'CORRIENTE'));
    expect(find.byIcon(Icons.radio_button_checked), findsOneWidget);
  });
}
