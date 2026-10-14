import 'package:flutter/material.dart';

/// One kind of data the app collects, as the notice lists it.
class NoticeItem {
  const NoticeItem(this.icon, this.title, this.description, {this.document = false});

  final IconData icon;
  final String title;
  final String description;

  /// The photos of the DUI: shown with the blue tint of the identity document in the design.
  final bool document;
}

/// Privacy notice shown before any signal is captured (VDI-45, VDI-92).
///
/// PROVISIONAL TEXT: replace it, and the version, when Legal approves the final notice (VDI-34 and VDI-91).
abstract final class PrivacyNotice {
  static const title = 'Cuidamos tu cuenta desde el primer paso';

  /// Version of the notice shown. `[X.Y]` until Legal approves the text; then the server (VDI-93) records it.
  static const version = '[X.Y]';

  static const badge = 'Aviso aprobado por Legal · versión $version';

  static const intro =
      'Mientras llenas tu solicitud, recopilamos algunos datos para protegerte contra el fraude:';

  /// Data collected once the notice is accepted.
  static const signals = [
    NoticeItem(
      Icons.place_outlined,
      'Tu ubicación aproximada',
      'La ciudad desde donde haces la solicitud, no tu dirección exacta.',
    ),
    NoticeItem(
      Icons.smartphone_outlined,
      'El tipo de dispositivo',
      'Modelo de teléfono y sistema, para reconocer si es el mismo en otra ocasión.',
    ),
    NoticeItem(
      Icons.touch_app_outlined,
      'Cómo usas la aplicación',
      'Por ejemplo, el tiempo que tardas en cada paso y tu ritmo al escribir.',
    ),
    NoticeItem(
      Icons.badge_outlined,
      'Las fotos de tu DUI',
      'Frente y reverso, guardadas de forma segura en tu expediente.',
      document: true,
    ),
  ];

  static const readFullNotice = 'Leer el aviso de privacidad completo';

  static const closing = 'Solo usamos esta información para proteger tu cuenta.';

  static const acceptance = 'He leído y acepto el aviso de privacidad.';

  static const termsAcceptance = 'He leído y acepto los términos y condiciones.';

  static const acceptButton = 'Aceptar y continuar';

  static const recordedNote = 'Guardamos la fecha y la versión del aviso que aceptas.';

  // Full notice (provisional).
  static const fullTitle = 'Aviso de privacidad';

  static const fullProvisional =
      'Este es el texto provisional del aviso de privacidad, versión $version. Legal lo reemplazará por el texto '
      'final y la versión que aceptes quedará registrada junto con la fecha.';

  static const fullPurpose =
      'Recopilamos estos datos solo para proteger tu cuenta contra el fraude y para verificar tu identidad al '
      'abrirla.';
}
