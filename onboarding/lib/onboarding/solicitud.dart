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

const tiposMovimiento = [
  Opcion('salario', 'Pago de salario', 'Tu empleador te depositará aquí el sueldo.'),
  Opcion('negocio', 'Cobros de mi negocio', 'Ventas y pagos que recibirás de tus clientes.'),
  Opcion('remesas', 'Remesas familiares', 'Dinero que te envía tu familia desde el exterior.'),
  Opcion('ahorro', 'Ahorro', 'Dinero que irás guardando en la cuenta.'),
];

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
  String monto = '';

  String get nombreCompleto => '$nombres $apellidos'.trim();

  /// Mensajes de error por campo; cadena vacía si el campo es válido.
  Map<String, String> mensajes() {
    final n = int.tryParse(monto) ?? 0;
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
      'monto': (n > 0 && n <= 1000000) ? '' : 'Escribe un monto mensual mayor que 0.',
    };
  }

  static List<String> camposDe(Pantalla p) => switch (p) {
        Pantalla.privacidad => ['aceptado'],
        Pantalla.basicos => ['nombres', 'apellidos', 'dui', 'tel'],
        Pantalla.ingresos => ['origen', 'detalleOrigen', 'nivel'],
        Pantalla.movimiento => ['tipo', 'monto'],
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

/// 1234567 -> "1,234,567"
String formatearMonto(String monto) {
  final s = (int.tryParse(monto) ?? 0).toString();
  final buf = StringBuffer();
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) buf.write(',');
    buf.write(s[i]);
  }
  return buf.toString();
}
