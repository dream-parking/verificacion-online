import 'dart:convert';
import 'dart:io' show Platform;
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Señales del dispositivo que se envían a la API (VDI-40).
class DatosDispositivo {
  const DatosDispositivo({
    required this.huella,
    required this.modelo,
    required this.sistemaOperativo,
    required this.versionApp,
  });

  /// Hash estable del dispositivo con formato `xxxx·xxxx·xxxx`.
  final String huella;
  final String modelo;
  final String sistemaOperativo;
  final String versionApp;
}

/// Origen de los datos del dispositivo; los tests usan uno falso.
abstract interface class FuenteDispositivo {
  Future<DatosDispositivo> leer();
}

/// Lee el dispositivo real con `device_info_plus` y calcula su huella.
///
/// La huella combina atributos de hardware con un identificador estable:
/// - iOS: `identifierForVendor` (igual mientras la app del banco siga instalada).
/// - Android: un id aleatorio guardado al instalar la app, porque Android no da un id del
///   dispositivo sin permisos especiales. Se pierde si se desinstala la app.
/// Así la misma persona que hace varias solicitudes desde el mismo teléfono obtiene la misma
/// huella, y la consola puede contar "solicitudes desde el mismo dispositivo".
class HuellaDispositivo implements FuenteDispositivo {
  HuellaDispositivo({DeviceInfoPlugin? deviceInfo}) : _deviceInfo = deviceInfo ?? DeviceInfoPlugin();

  final DeviceInfoPlugin _deviceInfo;
  DatosDispositivo? _cache;

  static const _claveInstalacion = 'senales.id_instalacion';

  @override
  Future<DatosDispositivo> leer() async {
    if (_cache != null) return _cache!;
    final app = await PackageInfo.fromPlatform();
    final versionApp = recortar('${app.version}+${app.buildNumber}', 20);

    if (Platform.isAndroid) {
      final a = await _deviceInfo.androidInfo;
      return _cache = DatosDispositivo(
        huella: calcularHuella([
          'android',
          await _idInstalacion(),
          a.brand,
          a.manufacturer,
          a.model,
          a.device,
          a.hardware,
          a.board,
          a.supportedAbis.join(','),
        ]),
        modelo: recortar('${_capitalizar(a.manufacturer)} ${a.model}', 80),
        sistemaOperativo: recortar('Android ${a.version.release}', 30),
        versionApp: versionApp,
      );
    }
    if (Platform.isIOS) {
      final i = await _deviceInfo.iosInfo;
      return _cache = DatosDispositivo(
        huella: calcularHuella(['ios', i.identifierForVendor ?? await _idInstalacion(), i.utsname.machine]),
        modelo: recortar(i.modelName.isNotEmpty ? i.modelName : i.utsname.machine, 80),
        sistemaOperativo: recortar('${i.systemName} ${i.systemVersion}', 30),
        versionApp: versionApp,
      );
    }
    return _cache = DatosDispositivo(
      huella: calcularHuella([Platform.operatingSystem, await _idInstalacion()]),
      modelo: 'Desconocido',
      sistemaOperativo: recortar(Platform.operatingSystem, 30),
      versionApp: versionApp,
    );
  }

  /// Id aleatorio que se crea la primera vez y se conserva entre aperturas de la app.
  static Future<String> _idInstalacion() async {
    final prefs = await SharedPreferences.getInstance();
    var id = prefs.getString(_claveInstalacion);
    if (id == null) {
      final r = Random.secure();
      id = List.generate(16, (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
      await prefs.setString(_claveInstalacion, id);
    }
    return id;
  }

  static String _capitalizar(String s) => s.isEmpty ? s : s[0].toUpperCase() + s.substring(1);
}

/// SHA-256 de los atributos, abreviado a 12 caracteres hex con el formato de la API: `d4f1·9a3c·e7b2`.
String calcularHuella(List<String> atributos) {
  final hex = sha256.convert(utf8.encode(atributos.map((a) => a.trim().toLowerCase()).join('|'))).toString();
  return '${hex.substring(0, 4)}·${hex.substring(4, 8)}·${hex.substring(8, 12)}';
}

String recortar(String s, int max) => s.length <= max ? s : s.substring(0, max);
