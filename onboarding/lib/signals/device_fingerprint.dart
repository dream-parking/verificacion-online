import 'dart:convert';
import 'dart:io' show Platform;
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Device signals sent to the API (VDI-40).
class DeviceInfo {
  const DeviceInfo({
    required this.fingerprint,
    required this.model,
    required this.operatingSystem,
    required this.appVersion,
  });

  /// Stable hash of the device, formatted as `xxxx·xxxx·xxxx`.
  final String fingerprint;
  final String model;
  final String operatingSystem;
  final String appVersion;
}

/// Source of the device data; tests use a fake one.
abstract interface class DeviceInfoSource {
  Future<DeviceInfo> read();
}

/// Reads the real device with `device_info_plus` and computes its fingerprint.
///
/// The fingerprint combines hardware attributes with a stable identifier:
/// - iOS: `identifierForVendor` (unchanged while the bank's app stays installed).
/// - Android: a random id saved when the app is installed, because Android does not expose a device
///   id without special permissions. It is lost if the app is uninstalled.
/// This way the same person making several requests from the same phone gets the same fingerprint,
/// and the console can count "requests from the same device".
class PlatformDeviceInfoSource implements DeviceInfoSource {
  PlatformDeviceInfoSource({DeviceInfoPlugin? deviceInfo}) : _deviceInfo = deviceInfo ?? DeviceInfoPlugin();

  final DeviceInfoPlugin _deviceInfo;
  DeviceInfo? _cache;

  /// Stored key: its value is kept as it was so existing installations keep their fingerprint.
  static const _installIdKey = 'senales.id_instalacion';

  @override
  Future<DeviceInfo> read() async {
    if (_cache != null) return _cache!;
    final app = await PackageInfo.fromPlatform();
    final appVersion = truncate('${app.version}+${app.buildNumber}', 20);

    if (Platform.isAndroid) {
      final a = await _deviceInfo.androidInfo;
      return _cache = DeviceInfo(
        fingerprint: computeFingerprint([
          'android',
          await _installId(),
          a.brand,
          a.manufacturer,
          a.model,
          a.device,
          a.hardware,
          a.board,
          a.supportedAbis.join(','),
        ]),
        model: truncate('${_capitalize(a.manufacturer)} ${a.model}', 80),
        operatingSystem: truncate('Android ${a.version.release}', 30),
        appVersion: appVersion,
      );
    }
    if (Platform.isIOS) {
      final i = await _deviceInfo.iosInfo;
      return _cache = DeviceInfo(
        fingerprint: computeFingerprint(['ios', i.identifierForVendor ?? await _installId(), i.utsname.machine]),
        model: truncate(i.modelName.isNotEmpty ? i.modelName : i.utsname.machine, 80),
        operatingSystem: truncate('${i.systemName} ${i.systemVersion}', 30),
        appVersion: appVersion,
      );
    }
    return _cache = DeviceInfo(
      fingerprint: computeFingerprint([Platform.operatingSystem, await _installId()]),
      model: 'Desconocido',
      operatingSystem: truncate(Platform.operatingSystem, 30),
      appVersion: appVersion,
    );
  }

  /// Random id created the first time and kept across app launches.
  static Future<String> _installId() async {
    final prefs = await SharedPreferences.getInstance();
    var id = prefs.getString(_installIdKey);
    if (id == null) {
      final r = Random.secure();
      id = List.generate(16, (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
      await prefs.setString(_installIdKey, id);
    }
    return id;
  }

  static String _capitalize(String s) => s.isEmpty ? s : s[0].toUpperCase() + s.substring(1);
}

/// SHA-256 of the attributes, shortened to 12 hex characters in the API format: `d4f1·9a3c·e7b2`.
String computeFingerprint(List<String> attributes) {
  final hex = sha256.convert(utf8.encode(attributes.map((a) => a.trim().toLowerCase()).join('|'))).toString();
  return '${hex.substring(0, 4)}·${hex.substring(4, 8)}·${hex.substring(8, 12)}';
}

String truncate(String s, int max) => s.length <= max ? s : s.substring(0, max);
