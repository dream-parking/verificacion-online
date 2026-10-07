import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/onboarding/solicitud.dart';
import 'package:onboarding/senales/interaccion.dart';
import 'package:onboarding/senales/senales.dart';

void main() {
  final t0 = DateTime.utc(2026, 10, 7, 10);
  DateTime ms(int n) => t0.add(Duration(milliseconds: n));

  /// Escribe [texto] letra por letra, una tecla cada [cada] ms, empezando en [desde].
  int escribir(RitmoEscritura r, String campo, String texto, {int desde = 0, int cada = 200}) {
    for (var i = 1; i <= texto.length; i++) {
      r.registrar(campo, texto.substring(0, i), ms(desde + (i - 1) * cada));
    }
    return desde + (texto.length - 1) * cada;
  }

  group('RitmoEscritura', () {
    test('una tecla cada 200 ms son 300 caracteres por minuto', () {
      final r = RitmoEscritura();
      escribir(r, 'nombres', 'Marta Alejandra');
      expect(r.cpm, 300);
    });

    test('con pocas teclas no reporta nada', () {
      final r = RitmoEscritura();
      escribir(r, 'nombres', 'Ana');
      expect(r.cpm, isNull);
    });

    test('las pausas largas no cuentan como tiempo escribiendo', () {
      final r = RitmoEscritura();
      final fin = escribir(r, 'nombres', 'Marta');
      escribir(r, 'apellidos', 'Rivas', desde: fin + 30000); // 30 s pensando
      expect(r.cpm, 300);
    });

    test('pegar o autocompletar no cuenta como escritura', () {
      final r = RitmoEscritura();
      r.registrar('nombres', 'Marta Alejandra', ms(0));
      expect(r.cpm, isNull);
      escribir(r, 'apellidos', 'Rivas Cruz', desde: 100);
      expect(r.cpm, 300);
    });

    test('borrar no suma; el guion de la máscara sí cuenta como parte de la tecla', () {
      final r = RitmoEscritura();
      final textos = ['0', '04', '048', '0481', '04812', '048123', '0481237', '04812377', '04812377-5'];
      for (var i = 0; i < textos.length; i++) {
        r.registrar('dui', textos[i], ms(i * 200));
      }
      r.registrar('dui', '04812377-', ms(textos.length * 200));
      expect(r.cpm, 300);
    });

    test('nunca pasa del máximo de la API', () {
      final r = RitmoEscritura();
      escribir(r, 'nombres', 'Marta Alejandra', cada: 1);
      expect(r.cpm, 2000);
    });
  });

  group('TiemposPorPaso', () {
    test('guarda inicio, fin e intentos en el formato de la API', () {
      final p = TiemposPorPaso()
        ..iniciar(Pantalla.bienvenida, ms(0))
        ..iniciar(Pantalla.basicos, ms(1000))
        ..intento(Pantalla.basicos)
        ..intento(Pantalla.basicos)
        ..completar(Pantalla.basicos, ms(5000))
        ..iniciar(Pantalla.ingresos, ms(5000));

      expect(p.toJson(), [
        {
          'step': 'BASIC_DATA',
          'startedAt': '2026-10-07T10:00:01.000Z',
          'completedAt': '2026-10-07T10:00:05.000Z',
          'attempts': 2,
        },
        {'step': 'INCOME', 'startedAt': '2026-10-07T10:00:05.000Z', 'attempts': 1},
      ]);
    });

    test('al volver a un paso conserva el inicio y actualiza el fin', () {
      final p = TiemposPorPaso()
        ..iniciar(Pantalla.basicos, ms(0))
        ..completar(Pantalla.basicos, ms(1000))
        ..iniciar(Pantalla.basicos, ms(9000))
        ..completar(Pantalla.basicos, ms(12000));
      final paso = p.toJson().single;
      expect(paso['startedAt'], '2026-10-07T10:00:00.000Z');
      expect(paso['completedAt'], '2026-10-07T10:00:12.000Z');
    });
  });

  test('Senales incluye ritmo y pasos cuando hay datos', () {
    final s = Senales();
    expect(s.toJson(), isEmpty);
    escribir(s.ritmo, 'nombres', 'Marta Alejandra');
    s.pasos.iniciar(Pantalla.basicos, ms(0));
    final json = s.toJson();
    expect(json['typingSpeedCpm'], 300);
    expect(json['steps'], hasLength(1));
  });
}
