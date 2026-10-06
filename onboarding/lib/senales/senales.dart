import 'huella_dispositivo.dart';

/// Señales capturadas durante la solicitud, en el formato de `PUT /api/onboarding/requests/{id}/signals`.
///
/// El backend reemplaza la sesión completa en cada envío: un campo que no se manda queda vacío.
/// Por eso siempre se envía este objeto entero, con todo lo capturado hasta el momento.
class Senales {
  DatosDispositivo? dispositivo;

  Map<String, Object?> toJson() {
    final d = dispositivo;
    return {
      if (d != null) ...{
        'deviceFingerprint': d.huella,
        'deviceModel': d.modelo,
        'operatingSystem': d.sistemaOperativo,
        'appVersion': d.versionApp,
      },
    };
  }
}
