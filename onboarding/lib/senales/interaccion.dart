import '../onboarding/solicitud.dart';

/// Patrones de interacción durante el formulario (VDI-43).

/// Pasos del flujo con el nombre que usa la API (`CaptureSignalsStepTiming.step`).
const pasoApi = {
  Pantalla.privacidad: 'PRIVACY_NOTICE',
  Pantalla.basicos: 'BASIC_DATA',
  Pantalla.ingresos: 'INCOME',
  Pantalla.movimiento: 'EXPECTED_ACTIVITY',
  Pantalla.revision: 'REVIEW',
};

/// Ritmo de escritura en caracteres por minuto, medido solo mientras la persona escribe.
///
/// - Una pausa de más de [pausa] entre teclas cierra la "ráfaga": el tiempo pensando no cuenta.
/// - Un cambio de más de 2 caracteres de golpe (pegar, autocompletar, datos demo) no es escritura
///   y también cierra la ráfaga. Se aceptan 2 porque las máscaras agregan el guion al escribir.
/// - Borrar no suma caracteres.
class RitmoEscritura {
  static const pausa = Duration(seconds: 2);

  /// Con menos teclas el valor no es representativo y no se envía.
  static const minimoTeclas = 5;

  final _longitudes = <String, int>{};
  DateTime? _ultimaTecla;
  var _teclas = 0;
  var _tiempo = Duration.zero;

  void registrar(String campo, String texto, DateTime ahora) {
    final antes = _longitudes[campo] ?? 0;
    _longitudes[campo] = texto.length;
    final delta = texto.length - antes;
    if (delta <= 0) return;
    if (delta > 2) {
      _ultimaTecla = null;
      return;
    }
    final ultima = _ultimaTecla;
    if (ultima != null) {
      final intervalo = ahora.difference(ultima);
      if (intervalo <= pausa) {
        _teclas++;
        _tiempo += intervalo;
      }
    }
    _ultimaTecla = ahora;
  }

  /// Caracteres por minuto (0-2000, el rango de la API) o `null` si aún no hay suficientes datos.
  int? get cpm {
    if (_teclas < minimoTeclas || _tiempo <= Duration.zero) return null;
    final valor = (_teclas * 60000 / _tiempo.inMilliseconds).round();
    return valor.clamp(0, 2000);
  }
}

/// Inicio, fin e intentos de cada paso del formulario.
class TiemposPorPaso {
  final _pasos = <Pantalla, _Paso>{};

  /// La primera vez que se muestra el paso. Volver a él (p. ej. para editar) conserva el inicio.
  void iniciar(Pantalla p, DateTime ahora) {
    if (!pasoApi.containsKey(p)) return;
    _pasos.putIfAbsent(p, () => _Paso(ahora));
  }

  /// Cada vez que la persona toca "Continuar" en el paso, avance o no.
  void intento(Pantalla p) => _pasos[p]?.intentos++;

  /// El paso se completó; si se vuelve a completar después de editarlo, se guarda la última vez.
  void completar(Pantalla p, DateTime ahora) => _pasos[p]?.fin = ahora;

  List<Map<String, Object?>> toJson() => [
        for (final MapEntry(key: p, value: d) in _pasos.entries)
          {
            'step': pasoApi[p],
            'startedAt': d.inicio.toUtc().toIso8601String(),
            if (d.fin != null) 'completedAt': d.fin!.toUtc().toIso8601String(),
            'attempts': d.intentos < 1 ? 1 : d.intentos,
          },
      ];
}

class _Paso {
  _Paso(this.inicio);

  final DateTime inicio;
  DateTime? fin;
  var intentos = 0;
}
