import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/main.dart';
import 'package:onboarding/onboarding/onboarding_flow.dart';
import 'package:onboarding/onboarding/solicitud.dart';
import 'package:onboarding/onboarding/widgets.dart';
import 'package:onboarding/senales/huella_dispositivo.dart';

/// Catálogo como el de la API de Dev (VDI-46 y VDI-50).
const catalogoDev = {
  'incomeSources': [
    {'code': 'SALARIO', 'label': 'Salario'},
    {'code': 'NEGOCIO_PROPIO', 'label': 'Negocio propio'},
    {'code': 'REMESAS', 'label': 'Remesas'},
    {'code': 'PENSION', 'label': 'Pensión'},
    {'code': 'OTRO', 'label': 'Otro'},
  ],
  'incomeRanges': [
    {'code': 'HASTA_500', 'label': 'Hasta USD 500', 'minUsd': null, 'maxUsd': 500.0},
    {'code': '500_1000', 'label': 'USD 500.01 a 1,000', 'minUsd': 500.01, 'maxUsd': 1000.0},
    {'code': '1000_2500', 'label': 'USD 1,000.01 a 2,500', 'minUsd': 1000.01, 'maxUsd': 2500.0},
    {'code': 'MAS_2500', 'label': 'Más de USD 2,500', 'minUsd': 2500.01, 'maxUsd': null},
  ],
  'transactionTypes': [
    {'code': 'PAGO_SALARIO', 'label': 'Pago de salario'},
  ],
  'monthlyAmountRanges': [
    {'code': 'HASTA_200', 'label': 'Hasta USD 200', 'minUsd': null, 'maxUsd': 200.0},
  ],
};

/// API falsa: responde el catálogo, `POST /api/onboarding/requests` y `PUT .../signals`, y guarda las llamadas.
class ApiFalsa {
  ApiFalsa({this.fallarPrimeras = 0, this.fallarSenales = 0, this.fallarCatalogos = 0});

  int fallarPrimeras;
  int fallarSenales;
  int fallarCatalogos;
  final llamadas = <http.Request>[];

  List<http.Request> get creaciones =>
      llamadas.where((r) => r.method == 'POST' && r.url.path == '/api/onboarding/requests').toList();
  List<http.Request> get catalogos => llamadas.where((r) => r.url.path == '/api/catalogs').toList();
  List<http.Request> get senales => llamadas.where((r) => r.url.path.endsWith('/signals')).toList();

  OnboardingApi get api => OnboardingApi(
        baseUrl: 'https://api.test',
        client: MockClient((req) async {
          llamadas.add(req);
          if (req.url.path == '/api/catalogs') {
            if (fallarCatalogos > 0) {
              fallarCatalogos--;
              return http.Response('', 503);
            }
            return http.Response.bytes(utf8.encode(jsonEncode(catalogoDev)), 200);
          }
          if (req.url.path.endsWith('/signals')) {
            if (fallarSenales > 0) {
              fallarSenales--;
              return http.Response('', 503);
            }
            return http.Response('', 204);
          }
          if (fallarPrimeras > 0) {
            fallarPrimeras--;
            return http.Response('{"status":503,"detail":"Servicio no disponible"}', 503);
          }
          return http.Response('{"id":"11111111-2222-3333-4444-555555555555","status":"IN_PROGRESS"}', 201);
        }),
      );
}

/// Dispositivo falso: cuenta cuántas veces se leyó.
class DispositivoFalso implements FuenteDispositivo {
  var lecturas = 0;

  @override
  Future<DatosDispositivo> leer() async {
    lecturas++;
    return const DatosDispositivo(
      huella: 'd4f1·9a3c·e7b2',
      modelo: 'Google Pixel 9',
      sistemaOperativo: 'Android 16',
      versionApp: '1.0.0+1',
    );
  }
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
          dispositivo: DispositivoFalso(),
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
      await tocar(tester, find.text('USD 500.01 a 1,000'));
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
      expect(find.text('Remesas'), findsOneWidget);
      expect(find.text('USD 500.01 a 1,000 al mes'), findsOneWidget);
      expect(find.text('Aprox. USD 1,500 al mes'), findsOneWidget);

      // Primer envío falla (simulado) y el reintento funciona.
      await tocar(tester, find.text('ENVIAR SOLICITUD'));
      expect(find.text('No pudimos enviar tu solicitud'), findsOneWidget);
      await tocar(tester, find.text('REINTENTAR'));

      expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
      expect(find.text('SOL-2026-00418'), findsOneWidget);
    });

    testWidgets('la flecha de volver regresa al paso anterior', (tester) async {
      await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: ApiFalsa().api, dispositivo: DispositivoFalso())));
      await tocar(tester, find.text('EMPEZAR'));
      expect(find.text('Cuidamos tu cuenta desde el primer paso'), findsOneWidget);
      await tocar(tester, find.byTooltip('Volver al paso anterior'));
      expect(find.text('Tu cuenta, desde tu teléfono.'), findsOneWidget);
    });

    group('aviso de privacidad (VDI-45)', () {
      testWidgets('no crea la solicitud hasta aceptar el aviso', (tester) async {
        final falsa = ApiFalsa();
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: DispositivoFalso())));
        await tocar(tester, find.text('EMPEZAR'));
        expect(find.text('Texto provisional · pendiente de revisión legal'), findsOneWidget);

        await continuar(tester);
        expect(falsa.creaciones, isEmpty);

        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);
        expect(falsa.creaciones, hasLength(1));
        expect(falsa.creaciones.single.url.path, '/api/onboarding/requests');
        expect(find.text('Paso 1 de 4'), findsOneWidget);
      });

      testWidgets('al volver, el aviso queda aceptado y no se crea otra solicitud', (tester) async {
        final falsa = ApiFalsa();
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: DispositivoFalso())));
        await tocar(tester, find.text('EMPEZAR'));
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);

        await tocar(tester, find.byTooltip('Volver al paso anterior'));
        final checkbox = tester.widget<Checkbox>(find.byType(Checkbox));
        expect(checkbox.value, isTrue);
        expect(checkbox.onChanged, isNull, reason: 'no se puede retirar el consentimiento ya registrado');

        await continuar(tester);
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(falsa.creaciones, hasLength(1));
      });

      testWidgets('si falla la conexión muestra el error y permite reintentar', (tester) async {
        final falsa = ApiFalsa(fallarPrimeras: 1);
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: DispositivoFalso())));
        await tocar(tester, find.text('EMPEZAR'));
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);

        expect(find.text('No pudimos iniciar tu solicitud'), findsOneWidget);
        expect(find.text('Paso 1 de 4'), findsNothing);

        await tocar(tester, find.text('REINTENTAR'));
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(falsa.creaciones, hasLength(2));
      });
    });

    group('huella del dispositivo (VDI-40)', () {
      testWidgets('no lee el dispositivo antes de aceptar el aviso', (tester) async {
        final falsa = ApiFalsa();
        final dispositivo = DispositivoFalso();
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: dispositivo)));
        await tocar(tester, find.text('EMPEZAR'));
        await continuar(tester);
        expect(dispositivo.lecturas, 0);
        expect(falsa.senales, isEmpty);
      });

      testWidgets('al aceptar envía la huella, el modelo, el sistema y la versión', (tester) async {
        final falsa = ApiFalsa();
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: DispositivoFalso())));
        await tocar(tester, find.text('EMPEZAR'));
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);

        expect(falsa.senales, hasLength(1));
        final req = falsa.senales.single;
        expect(req.method, 'PUT');
        expect(req.url.path, '/api/onboarding/requests/11111111-2222-3333-4444-555555555555/signals');
        expect(
          jsonDecode(req.body),
          allOf(
            containsPair('deviceFingerprint', 'd4f1·9a3c·e7b2'),
            containsPair('deviceModel', 'Google Pixel 9'),
            containsPair('operatingSystem', 'Android 16'),
            containsPair('appVersion', '1.0.0+1'),
          ),
        );
      });

      testWidgets('si falla el envío no bloquea y se reintenta en la siguiente pantalla', (tester) async {
        final falsa = ApiFalsa(fallarSenales: 1);
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: DispositivoFalso())));
        await tocar(tester, find.text('EMPEZAR'));
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);
        expect(find.text('Paso 1 de 4'), findsOneWidget);
        expect(falsa.senales, hasLength(1));

        await tocar(tester, find.text('Rellenar con datos de ejemplo (demo)'));
        await continuar(tester);
        expect(falsa.senales, hasLength(2));
      });
    });

    group('patrones de interacción (VDI-43)', () {
      testWidgets('envía el ritmo de escritura y el tiempo e intentos de cada paso', (tester) async {
        var ahora = DateTime.utc(2026, 10, 7, 10);
        void avanzar(int ms) => ahora = ahora.add(Duration(milliseconds: ms));
        final falsa = ApiFalsa();
        await tester.pumpWidget(OnboardingApp(
          home: OnboardingFlow(
            api: falsa.api,
            dispositivo: DispositivoFalso(),
            reloj: () => ahora,
            demoraEnvio: const Duration(milliseconds: 10),
          ),
        ));

        await tocar(tester, find.text('EMPEZAR'));
        avanzar(8000);
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);

        // Datos básicos: un intento fallido y luego escritos letra por letra (una tecla cada 200 ms).
        avanzar(1000);
        await continuar(tester);
        Future<void> escribir(int campo, String texto) async {
          for (var i = 1; i <= texto.length; i++) {
            avanzar(200);
            await tester.enterText(find.byType(TextField).at(campo), texto.substring(0, i));
          }
          avanzar(2500); // pausa entre campos: no cuenta como tiempo escribiendo
        }

        await escribir(0, 'Marta Alejandra');
        await escribir(1, 'Rivas Cruz');
        await escribir(2, '048123775');
        await escribir(3, '78452310');
        await continuar(tester);

        await tocar(tester, find.text('Remesas'));
        await tocar(tester, find.text('USD 500.01 a 1,000'));
        avanzar(4000);
        await continuar(tester);

        await tocar(tester, find.text('Ahorro'));
        await escribir(0, '320');
        await continuar(tester);

        avanzar(3000);
        await tocar(tester, find.text('ENVIAR SOLICITUD'));
        expect(find.text('¡Recibimos tu solicitud!'), findsOneWidget);
        await tester.pumpAndSettle();

        final ultimo = jsonDecode(falsa.senales.last.body) as Map<String, dynamic>;
        expect(ultimo['deviceFingerprint'], 'd4f1·9a3c·e7b2', reason: 'se sigue mandando la huella (VDI-40)');
        expect(ultimo['typingSpeedCpm'], 300);

        final pasos = {for (final p in ultimo['steps'] as List) p['step']: p};
        expect(pasos.keys, ['PRIVACY_NOTICE', 'BASIC_DATA', 'INCOME', 'EXPECTED_ACTIVITY', 'REVIEW']);
        for (final p in pasos.values) {
          expect(p['completedAt'], isNotNull, reason: '${p['step']} completado');
        }
        expect(pasos['PRIVACY_NOTICE']['startedAt'], '2026-10-07T10:00:00.000Z');
        expect(pasos['PRIVACY_NOTICE']['completedAt'], '2026-10-07T10:00:08.000Z');
        expect(pasos['BASIC_DATA']['attempts'], 2);
        expect(pasos['INCOME']['attempts'], 1);
      });

      testWidgets('no mide nada que se envíe antes de aceptar el aviso', (tester) async {
        final falsa = ApiFalsa();
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: DispositivoFalso())));
        await tocar(tester, find.text('EMPEZAR'));
        await continuar(tester);
        await continuar(tester);
        expect(falsa.llamadas.where((r) => r.url.path != '/api/catalogs'), isEmpty);
      });
    });

    group('pantalla de ingresos (VDI-48)', () {
      Future<void> irAIngresos(WidgetTester tester, ApiFalsa falsa) async {
        await tester.pumpWidget(OnboardingApp(home: OnboardingFlow(api: falsa.api, dispositivo: DispositivoFalso())));
        await tocar(tester, find.text('EMPEZAR'));
        await tocar(tester, find.byType(Checkbox));
        await continuar(tester);
        await tocar(tester, find.text('Rellenar con datos de ejemplo (demo)'));
        await continuar(tester);
        expect(find.text('Paso 2 de 4'), findsOneWidget);
      }

      testWidgets('muestra solo las opciones del catálogo de la UIF', (tester) async {
        final falsa = ApiFalsa();
        await irAIngresos(tester, falsa);
        for (final texto in [
          'Salario', 'Negocio propio', 'Remesas', 'Pensión', 'Otro', //
          'Hasta USD 500', 'USD 500.01 a 1,000', 'USD 1,000.01 a 2,500', 'Más de USD 2,500',
        ]) {
          expect(find.text(texto), findsOneWidget, reason: texto);
        }
        expect(find.byType(OpcionTarjeta), findsNWidgets(9));
        expect(find.text('USD 500 a 1,500'), findsNothing, reason: 'ya no se usan los rangos fijos');
        expect(falsa.catalogos, hasLength(1));
      });

      testWidgets('"Otro" pide el detalle y lo muestra en la revisión', (tester) async {
        await irAIngresos(tester, ApiFalsa());
        await tocar(tester, find.text('Otro'));
        await tocar(tester, find.text('Hasta USD 500'));
        await continuar(tester);
        expect(find.text('Cuéntanos de dónde vienen tus ingresos.'), findsOneWidget);
        expect(find.text('Paso 2 de 4'), findsOneWidget);

        await tester.enterText(find.byType(TextField), 'Venta de artesanías');
        await continuar(tester);
        expect(find.text('Paso 3 de 4'), findsOneWidget);

        await tocar(tester, find.text('Ahorro'));
        await tester.enterText(find.byType(TextField), '300');
        await continuar(tester);
        expect(find.text('Otro: Venta de artesanías'), findsOneWidget);
        expect(find.text('Hasta USD 500 al mes'), findsOneWidget);
      });

      testWidgets('si no cargan las opciones muestra el error y permite reintentar', (tester) async {
        final falsa = ApiFalsa(fallarCatalogos: 1);
        await irAIngresos(tester, falsa);
        expect(find.text('No pudimos cargar las opciones'), findsOneWidget);
        expect(find.byType(OpcionTarjeta), findsNothing);

        await tocar(tester, find.text('REINTENTAR'));
        expect(find.byType(OpcionTarjeta), findsNWidgets(9));
        expect(falsa.catalogos, hasLength(2));
      });
    });
  });
}
