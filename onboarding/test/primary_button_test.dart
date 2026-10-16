import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/onboarding/widgets.dart';
import 'package:onboarding/theme.dart';

void main() {
  Future<FilledButton> pumpButton(WidgetTester tester, PrimaryButton button) async {
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: button)));
    return tester.widget<FilledButton>(find.byType(FilledButton));
  }

  Color? background(FilledButton b) => b.style?.backgroundColor?.resolve(const {});

  Color? foreground(FilledButton b) => b.style?.foregroundColor?.resolve(const {});

  testWidgets('a ready button is the brand color with dark text', (tester) async {
    final button = await pumpButton(tester, PrimaryButton(text: 'Continuar', onPressed: () {}));
    expect(background(button), AppColors.brand);
    expect(foreground(button), AppColors.ink);
  });

  testWidgets('a button that is not ready is gray, like a disabled one', (tester) async {
    final button = await pumpButton(tester, PrimaryButton(text: 'Continuar', ready: false, onPressed: () {}));
    expect(background(button), AppColors.disabled);
    expect(foreground(button), AppColors.muted);
  });

  testWidgets('a button that is not ready can still be tapped, so the screen can say what is missing', (tester) async {
    var taps = 0;
    await pumpButton(tester, PrimaryButton(text: 'Continuar', ready: false, onPressed: () => taps++));
    await tester.tap(find.text('Continuar'));
    expect(taps, 1);
  });

  testWidgets('a screen reader is told that data is missing', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpButton(tester, PrimaryButton(text: 'Continuar', ready: false, onPressed: () {}));
    expect(find.semantics.byPredicate((node) => node.hint == 'Faltan datos por completar'), findsOneWidget);
    handle.dispose();
  });

  testWidgets('a button without action stays disabled, as before', (tester) async {
    final button = await pumpButton(tester, const PrimaryButton(text: 'Continuar', onPressed: null));
    expect(button.onPressed, isNull);
    expect(button.style?.backgroundColor?.resolve({WidgetState.disabled}), AppColors.disabled);
  });
}
