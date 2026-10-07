/// Pantallas del flujo, en orden.
enum Pantalla { bienvenida, privacidad, basicos, ingresos, movimiento, revision, confirmacion }

/// Una opción de catálogo (origen de ingresos, nivel, tipo de dinero).
class Opcion {
  const Opcion(this.valor, this.etiqueta, [this.descripcion = '']);

  final String valor;
  final String etiqueta;
  final String descripcion;
}

/// Código del origen "Otro": la API exige el detalle (`sourceDetail`) cuando se elige.
const origenOtro = 'OTRO';

/// Texto de ayuda del diseño para cada tipo de movimiento del catálogo (VDI-50).
/// Un tipo nuevo en el catálogo se muestra igual, solo sin descripción.
const descripcionesMovimiento = {
  'PAGO_SALARIO': 'Tu empleador te depositará aquí el sueldo.',
  'COBROS_NEGOCIO': 'Ventas y pagos que recibirás de tus clientes.',
  'REMESAS': 'Dinero que te envía tu familia desde el exterior.',
  'AHORRO': 'Dinero que irás guardando en la cuenta.',
};

String etiquetaDe(List<Opcion> opciones, String valor) {
  for (final o in opciones) {
    if (o.valor == valor) return o.etiqueta;
  }
  return '';
}

/// Datos que el cliente llena durante el onboarding.
class Solicitud {
  /// `id` de la solicitud en el backend; se crea al aceptar el aviso de privacidad.
  /// Mientras sea `null` no se captura ninguna señal.
  String? id;
  DateTime? avisoAceptadoEn;

  bool get capturaPermitida => id != null;

  bool aceptado = false;
  String nombres = '';
  String apellidos = '';
  String dui = '';
  String tel = '';
  String origen = '';
  String detalleOrigen = '';
  String nivel = '';
  String tipo = '';
  String rangoMonto = '';

  String get nombreCompleto => '$nombres $apellidos'.trim();

  /// Mensajes de error por campo; cadena vacía si el campo es válido.
  Map<String, String> mensajes() {
    return {
      'aceptado': aceptado ? '' : 'Para continuar necesitamos que aceptes el aviso de privacidad.',
      'nombres': nombres.trim().length < 2 ? 'Escribe tus nombres.' : '',
      'apellidos': apellidos.trim().length < 2 ? 'Escribe tus apellidos.' : '',
      'dui': RegExp(r'^\d{8}-\d$').hasMatch(dui) ? '' : 'Escribe tu DUI con el formato 00000000-0.',
      'tel': RegExp(r'^[267]\d{3}-\d{4}$').hasMatch(tel)
          ? ''
          : 'Escribe un celular de 8 dígitos, por ejemplo 7845-2310.',
      'origen': origen.isNotEmpty ? '' : 'Elige de dónde vienen tus ingresos.',
      'detalleOrigen': origen == origenOtro && detalleOrigen.trim().isEmpty
          ? 'Cuéntanos de dónde vienen tus ingresos.'
          : '',
      'nivel': nivel.isNotEmpty ? '' : 'Elige tu nivel de ingresos mensuales.',
      'tipo': tipo.isNotEmpty ? '' : 'Elige qué tipo de dinero manejarás.',
      'rangoMonto': rangoMonto.isNotEmpty ? '' : 'Elige cuánto dinero moverás al mes.',
    };
  }

  static List<String> camposDe(Pantalla p) => switch (p) {
        Pantalla.privacidad => ['aceptado'],
        Pantalla.basicos => ['nombres', 'apellidos', 'dui', 'tel'],
        Pantalla.ingresos => ['origen', 'detalleOrigen', 'nivel'],
        Pantalla.movimiento => ['tipo', 'rangoMonto'],
        _ => [],
      };

  bool pantallaValida(Pantalla p) {
    final m = mensajes();
    return camposDe(p).every((c) => m[c]!.isEmpty);
  }
}

/// Aplica la máscara 00000000-0 conservando solo dígitos.
String formatearDui(String raw) {
  final d = raw.replaceAll(RegExp(r'\D'), '');
  final s = d.length > 9 ? d.substring(0, 9) : d;
  return s.length > 8 ? '${s.substring(0, 8)}-${s.substring(8)}' : s;
}

/// Aplica la máscara 0000-0000 conservando solo dígitos.
String formatearTel(String raw) {
  final d = raw.replaceAll(RegExp(r'\D'), '');
  final s = d.length > 8 ? d.substring(0, 8) : d;
  return s.length > 4 ? '${s.substring(0, 4)}-${s.substring(4)}' : s;
}
