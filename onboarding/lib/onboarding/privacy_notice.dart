import 'package:flutter/material.dart';

/// Privacy notice shown before any signal is captured (VDI-45).
///
/// PROVISIONAL TEXT: replace it when Legal approves the final notice (VDI-34).
abstract final class PrivacyNotice {
  static const title = 'Cuidamos tu cuenta desde el primer paso';

  static const intro =
      'Mientras llenas tu solicitud, recopilamos algunos datos para protegerte contra el fraude:';

  /// Signals captured once the notice is accepted.
  static const signals = [
    (
      Icons.place_outlined,
      'Tu ubicación aproximada',
      'La ciudad desde donde haces la solicitud, no tu dirección exacta.',
    ),
    (
      Icons.smartphone_outlined,
      'El tipo de dispositivo',
      'Modelo de teléfono y sistema, para reconocer si es el mismo en otra ocasión.',
    ),
    (
      Icons.touch_app_outlined,
      'Cómo usas la aplicación',
      'Por ejemplo, el tiempo que tardas en cada paso y tu ritmo al escribir.',
    ),
  ];

  static const closing = 'Solo usamos esta información para proteger tu cuenta.';

  static const acceptance = 'He leído y acepto el aviso de privacidad.';

  static const termsAcceptance = 'He leído y acepto los términos y condiciones.';
}
