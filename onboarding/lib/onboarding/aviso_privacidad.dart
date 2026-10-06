import 'package:flutter/material.dart';

/// Aviso de privacidad que se muestra antes de capturar cualquier señal (VDI-45).
///
/// TEXTO PROVISIONAL: se reemplaza cuando Legal apruebe el aviso definitivo (VDI-34).
/// Mientras [esProvisional] sea `true`, la pantalla muestra la etiqueta
/// "Texto provisional · pendiente de revisión legal".
abstract final class AvisoPrivacidad {
  static const esProvisional = true;

  static const titulo = 'Cuidamos tu cuenta desde el primer paso';

  static const introduccion =
      'Mientras llenas tu solicitud, recopilamos algunos datos para protegerte contra el fraude:';

  /// Señales que se capturan una vez aceptado el aviso.
  static const senales = [
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

  static const cierre = 'Solo usamos esta información para proteger tu cuenta.';

  static const aceptacion = 'He leído y acepto el aviso de privacidad.';
}
