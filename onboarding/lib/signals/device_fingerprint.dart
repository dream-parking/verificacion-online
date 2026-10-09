import 'dart:convert';
import 'dart:io' show Platform;
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/widgets.dart' show Size, WidgetsBinding;
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
/// The fingerprint combines the model, the system and its version, the language and the screen size
/// (VDI-12, criterion 2; no personal data) with a stable identifier:
/// - iOS: `identifierForVendor` (unchanged while the bank's app stays installed).
/// - Android: a random id saved when the app is installed, because Android does not expose a device
///   id without special permissions. It is lost if the app is uninstalled.
/// This way the same person making several requests from the same phone gets the same fingerprint,
/// and the console can count "requests from the same device".
class PlatformDeviceInfoSource implements DeviceInfoSource {
  /// [platform] is `Platform.operatingSystem` (`android`, `ios`, …); tests pass another one.
  PlatformDeviceInfoSource({DeviceInfoPlugin? deviceInfo, String? platform})
      : _deviceInfo = deviceInfo ?? DeviceInfoPlugin(),
        _platform = platform ?? Platform.operatingSystem;

  final DeviceInfoPlugin _deviceInfo;
  final String _platform;
  DeviceInfo? _cache;

  /// Stored key: its value is kept as it was so existing installations keep their fingerprint.
  static const _installIdKey = 'senales.id_instalacion';

  @override
  Future<DeviceInfo> read() async {
    if (_cache != null) return _cache!;
    final app = await PackageInfo.fromPlatform();
    final appVersion = truncate('${app.version}+${app.buildNumber}', 20);

    final display = _displayAttributes();

    if (_platform == 'android') {
      final a = await _deviceInfo.androidInfo;
      return _cache = DeviceInfo(
        fingerprint: computeFingerprint([
          'android',
          a.version.release,
          ...display,
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
    if (_platform == 'ios') {
      final i = await _deviceInfo.iosInfo;
      return _cache = DeviceInfo(
        fingerprint: computeFingerprint([
          'ios',
          i.systemVersion,
          ...display,
          i.identifierForVendor ?? await _installId(),
          i.utsname.machine,
        ]),
        model: truncate(i.modelName.isNotEmpty ? i.modelName : i.utsname.machine, 80),
        operatingSystem: truncate('${i.systemName} ${i.systemVersion}', 30),
        appVersion: appVersion,
      );
    }
    return _cache = DeviceInfo(
      fingerprint: computeFingerprint([_platform, ...display, await _installId()]),
      model: 'Desconocido',
      operatingSystem: truncate(_platform, 30),
      appVersion: appVersion,
    );
  }

  /// Language of the phone and size of its screen, as the system reports them.
  static List<String> _displayAttributes() {
    final dispatcher = WidgetsBinding.instance.platformDispatcher;
    final views = dispatcher.views;
    return [
      dispatcher.locale.toLanguageTag(),
      views.isEmpty ? '' : screenSize(views.first.physicalSize),
    ];
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

/// Screen size in physical pixels, smaller side first, so rotating the phone does not change it: `1080x2400`.
String screenSize(Size physical) {
  final a = physical.width.round(), b = physical.height.round();
  return a <= b ? '${a}x$b' : '${b}x$a';
}

String truncate(String s, int max) => s.length <= max ? s : s.substring(0, max);
