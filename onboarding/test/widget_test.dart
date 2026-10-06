import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/main.dart';
import 'package:onboarding/onboarding/onboarding_flow.dart';
import 'package:onboarding/onboarding/solicitud.dart';

/// API falsa: responde `POST /api/onboarding/requests` y cuenta las llamadas.
class ApiFalsa {
  ApiFalsa({this.fallarPrimeras = 0});

  int fallarPrimeras;
  final llamadas = <http.Request>[];

  OnboardingApi get api => OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient((req) async {
          llamadas.add(req);
          if (fallarPrimeras > 0) {
            fallarPrimeras--;
            return http.Response('{"status":503,"detail":"Servicio no disponible"}', 503);
          }
          return http.Response('{"id":"11111111-2222-3333-4444-555555555555","status":"IN_PROGRESS"}', 201);
        }),
      );
}

void main() {
  group('validación y formato', () {
    test('máscaras de DUI, teléfono y monto', () {
      expect(formatearDui('048123775'), '04812377-5');
      expect(formatearDui('04a8-12377599'), '04812377-5');
      expect(formatearTel('78452310'), '7845-2310');
      expect(formatearTel('784'), '784');
      expect(formatearMonto('1234567'), '1,234,567');
      expect(formatearMonto('320'), '320');
    });

    test('datos básicos', () {
      final s = Solicitud();
      expect(s.pantallaValida(Pantalla.basicos), isFalse);
      s
        ..nombres = 'Marta'
        ..apellidos = 'Rivas'
        ..dui = '04812377-5'
        ..tel = '1845-2310';
      expect(s.mensajes()['tel'], isNotEmpty, reason: 'el celular debe empezar con 2, 6 o 7');
      s.tel = '7845-2310';
      expect(s.pantallaValida(Pantalla.basicos), isTrue);
    });

    test('monto debe ser mayor que 0', () {
      final s = Solicitud()..tipo = 'ahorro';
      for (final m in ['', '0']) {
        s.monto = m;
        expect(s.pantallaValida(Pantalla.movimiento), isFalse);
      }
      s.monto = '320';
      expect(s.pantallaValida(Pantalla.movimiento), isTrue);
    });
  });

  group('flujo', () {
    setUp(() {
      final binding = TestWidgetsFlutterBinding.ensureInitialized();
      binding.platformDispatcher.views.first
        ..physicalSize = const Size(390, 844)
        ..devicePixelRatio = 1;
    });
    tearDown(() => TestWidgetsFlutterBinding.instance.platformDispatcher.views.first
      ..resetPhysicalSize()
      ..resetDevicePixelRatio());

    Future<void> tocar(WidgetTester tester, Finder f) async {
      await tester.ensureVisible(f);
      await tester.pumpAndSettle();
      await tester.tap(f);
      await tester.pumpAndSettle();
    }

    Future<void> continuar(WidgetTester tester) => tocar(tester, find.text('CONTINUAR'));

    testWidgets('completa la solicitud de principio a fin', (tester) async {
      await tester.pumpWidget(OnboardingApp(
        home: OnboardingFlow(
          api: ApiFalsa().api,
          simularErrorDeConexion: true,
          demoraEnvio: const Duration(milliseconds: 10),
        ),
      ));
      expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);

      await tocar(tester, find.text('EMPEZAR'));

      // Privacidad: no avanza sin aceptar.
      await continuar(tester);
      expect(find.text('Para continuar necesitamos que aceptes el aviso de privacidad.'), findsOneWidget);
      await tocar(tester, find.byType(Checkbox));
      await continuar(tester);

      // Datos básicos.
      expect(find.text('Paso 1 de 4'), findsOneWidget);
      await continuar(tester);
      expect(find.text('Escribe tus nombres.'), findsOneWidget);
      await tocar(tester, find.text('Rellenar con datos de ejemplo (demo)'));
      await continuar(tester);

      // Ingresos.
      expect(find.text('Paso 2 de 4'), findsOneWidget);
      await tocar(tester, find.text('Remesas'));
      await tocar(tester, find.text('USD 500 a 1,500'));
      await continuar(tester);

      // Movimiento esperado.
      expect(find.text('Paso 3 de 4'), findsOneWidget);
      await tocar(tester, find.text('Ahorro'));
      await tester.enterText(find.byType(TextField), '1500');
      await continuar(tester);

      // Revisión.
      expect(find.text('Paso 4 de 4'), findsOneWidget);
      expect(find.text('Marta Alejandra Rivas Cruz'), findsOneWidget);
      expect(find.text('DUI 04812377-5 · Cel. 7845-2310'), findsOneWidget);
      expect(find.text('Aprox. USD 1,500 al mes'), findsOneWidget);

      // Primer envío falla (simulado) y el reintento funciona.
      await tocar(tester, find.text('ENVIAR SOLICITUD'));
      expect(find.text('No pudimos enviar tu solicitud'), findsOneWidget);
      await tocar(tester, find.text('REINTENTAR'));

      expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
      expect(find.text('SOL-2026-00418'), findsOneWidget);
    });

    testWidgets('la flecha de volver regresa al paso anterior', (tester) async {
      await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: ApiFalsa().api)));
      await tocar(tester, find.text('EMPEZAR'));
      expect(find.text('Cuidamos tu cuenta desde el primer paso'), findsOneWidget);
      await tocar(tester, find.byTooltip('Volver al paso anterior'));
      expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);
    });

    group('aviso de privacidad (VDI-45)', () {
      testWidgets('no crea la solicitud hasta aceptar el aviso', (tester) async {
        final falsa = ApiFalsa();
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api)));
        await tocar(tester, find.text('EMPEZAR'));
        expect(find.text('Texto provisional · pendiente de revisión legal'), findsOneWidget);

        await continuar(tester);
        expect(falsa.llamadas, isEmpty);

        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);
        expect(falsa.llamadas, hasLength(1));
        expect(falsa.llamadas.single.method, 'POST');
        expect(falsa.llamadas.single.url.path, '/api/onboarding/requests');
        expect(find.text('Paso 1 de 4'), findsOneWidget);
      });

      testWidgets('al volver, el aviso queda aceptado y no se crea otra solicitud', (tester) async {
        final falsa = ApiFalsa();
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api)));
        await tocar(tester, find.text('EMPEZAR'));
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);

        await tocar(tester, find.byTooltip('Volver al paso anterior'));
        final checkbox = tester.widget<Checkbox>(find.byType(Checkbox));
        expect(checkbox.value, isTrue);
        expect(checkbox.onChanged, isNull, reason: 'no se puede retirar el consentimiento ya registrado');

        await continuar(tester);
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(falsa.llamadas, hasLength(1));
      });

      testWidgets('si falla la conexión muestra el error y permite reintentar', (tester) async {
        final falsa = ApiFalsa(fallarPrimeras: 1);
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api)));
        await tocar(tester, find.text('EMPEZAR'));
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);

        expect(find.text('No pudimos iniciar tu solicitud'), findsOneWidget);
        expect(find.text('Paso 1 de 4'), findsNothing);

        await tocar(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(falsa.llamadas, hasLength(2));
      });
    });
  });
}
