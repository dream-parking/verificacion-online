import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/onboarding/widgets.dart';

void main() {
  Future<void> show(WidgetTester tester, {required String title, required String detail, double width = 390}) async {
    tester.view.physicalSize = Size(width, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: Stack(children: [LoadingOverlay(title: title, detail: detail)])),
    ));
  }

  const title = 'Revisando tu DUI…';
  const long = 'Estamos leyendo tus fotos. Puede tardar unos segundos.';

  testWidgets('the title and the detail are centered, also when the detail takes two lines', (tester) async {
    await show(tester, title: title, detail: long);
    expect(tester.widget<Text>(find.text(title)).textAlign, TextAlign.center);
    expect(tester.widget<Text>(find.text(long)).textAlign, TextAlign.center);
    final screen = tester.view.physicalSize.width;
    final detail = tester.getRect(find.text(long));
    expect(detail.center.dx, closeTo(screen / 2, 1), reason: 'the block of text is in the middle');
  });

  testWidgets('the text keeps a margin to the edges of the screen', (tester) async {
    await show(tester, title: title, detail: long, width: 320);
    final detail = tester.getRect(find.text(long));
    expect(detail.left, greaterThanOrEqualTo(24 - 0.01));
    expect(detail.right, lessThanOrEqualTo(320 - 24 + 0.01));
    final heading = tester.getRect(find.text(title));
    expect(heading.left, greaterThanOrEqualTo(24 - 0.01));
  });

  testWidgets('a long title also wraps centered and inside the margin', (tester) async {
    await show(tester, title: 'Estamos preparando tu solicitud, por favor espera', detail: long, width: 320);
    final heading = tester.getRect(find.text('Estamos preparando tu solicitud, por favor espera'));
    expect(heading.left, greaterThanOrEqualTo(24 - 0.01));
    expect(heading.right, lessThanOrEqualTo(320 - 24 + 0.01));
    expect(tester.widget<Text>(find.text('Estamos preparando tu solicitud, por favor espera')).textAlign, TextAlign.center);
  });

  testWidgets('it shows the spinner and covers the whole screen so nothing underneath can be tapped', (tester) async {
    await show(tester, title: title, detail: long);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    final overlay = tester.getRect(find.byType(LoadingOverlay));
    expect(overlay.size, tester.view.physicalSize);
  });

  testWidgets('it is announced to screen readers', (tester) async {
    final handle = tester.ensureSemantics();
    await show(tester, title: title, detail: long);
    expect(find.bySemanticsLabel(RegExp('Revisando tu DUI')), findsWidgets);
    handle.dispose();
  });
}
