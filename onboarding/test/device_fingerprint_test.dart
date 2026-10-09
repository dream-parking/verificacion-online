import 'dart:ui' show Locale, Size;

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:onboarding/signals/device_fingerprint.dart';
import 'package:onboarding/signals/signals.dart';

void main() {
  group('computeFingerprint', () {
    test('has the API format and fits in 64 characters', () {
      final f = computeFingerprint(['android', 'abc', 'google', 'Pixel 9']);
      expect(f, matches(RegExp(r'^[0-9a-f]{4}·[0-9a-f]{4}·[0-9a-f]{4}$')));
      expect(f.length, lessThanOrEqualTo(64));
    });

    test('is stable for the same device', () {
      expect(computeFingerprint(['android', 'abc', 'Pixel 9']), computeFingerprint(['android', 'abc', 'Pixel 9']));
      expect(computeFingerprint(['android', 'abc', 'Pixel 9']), computeFingerprint([' ANDROID', 'abc ', 'pixel 9']));
    });

    test('changes when the installation or the model changes', () {
      final base = computeFingerprint(['android', 'abc', 'Pixel 9']);
      expect(computeFingerprint(['android', 'xyz', 'Pixel 9']), isNot(base));
      expect(computeFingerprint(['android', 'abc', 'Pixel 8']), isNot(base));
    });
  });

  group('language and screen size (VDI-69)', () {
    final base = ['android', '16', 'es-SV', '1080x2400', 'abc', 'Pixel 9'];

    test('changing the language or the screen size changes the fingerprint', () {
      final f = computeFingerprint(base);
      expect(computeFingerprint([...base]..[2] = 'en-US'), isNot(f));
      expect(computeFingerprint([...base]..[3] = '1440x3120'), isNot(f));
    });

    test('screenSize does not change when the phone is rotated', () {
      expect(screenSize(const Size(1080, 2400)), '1080x2400');
      expect(screenSize(const Size(2400, 1080)), '1080x2400');
      expect(screenSize(const Size(1079.6, 2400.2)), '1080x2400');
    });
  });

  group('PlatformDeviceInfoSource reads the phone (VDI-69)', () {
    const channel = MethodChannel('dev.fluttercommunity.plus/device_info');
    const android = {
      'version': {'release': '16', 'sdkInt': 36, 'codename': 'REL', 'incremental': '1'},
      'board': 'caiman', 'bootloader': 'b', 'brand': 'google', 'device': 'caiman', 'display': 'd',
      'fingerprint': 'f', 'hardware': 'caiman', 'host': 'h', 'id': 'i', 'manufacturer': 'google',
      'model': 'Pixel 9', 'product': 'caiman', 'name': 'Pixel 9', 'supportedAbis': ['arm64-v8a'],
      'tags': 't', 'time': 0, 'type': 'user', 'isPhysicalDevice': true, 'freeDiskSize': 0,
      'totalDiskSize': 0, 'isLowRamDevice': false, 'physicalRamSize': 0, 'availableRamSize': 0,
    };
    const ios = {
      'name': 'iPhone', 'systemName': 'iOS', 'systemVersion': '18.1', 'model': 'iPhone',
      'modelName': 'iPhone 15', 'localizedModel': 'iPhone', 'identifierForVendor': 'vendor-1',
      'freeDiskSize': 0, 'totalDiskSize': 0, 'isPhysicalDevice': true, 'physicalRamSize': 0,
      'availableRamSize': 0, 'isiOSAppOnMac': false, 'isiOSAppOnVision': false,
      'utsname': {'sysname': 'Darwin', 'nodename': 'n', 'release': 'r', 'version': 'v', 'machine': 'iPhone15,4'},
    };

    Future<String> fingerprintOf(WidgetTester tester, String platform, Map<String, Object> data,
        {String language = 'es', Size screen = const Size(1080, 2400)}) async {
      tester.platformDispatcher.localeTestValue = Locale(language, 'SV');
      tester.view.physicalSize = screen;
      addTearDown(tester.platformDispatcher.clearLocaleTestValue);
      addTearDown(tester.view.resetPhysicalSize);
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (_) async => data);
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, null));
      return (await PlatformDeviceInfoSource(platform: platform).read()).fingerprint;
    }

    setUp(() {
      SharedPreferences.setMockInitialValues({'senales.id_instalacion': 'install-1'});
      PackageInfo.setMockInitialValues(
          appName: 'a', packageName: 'p', version: '1.0.0', buildNumber: '1', buildSignature: '');
    });

    testWidgets('on Android the language and the screen size change the fingerprint', (tester) async {
      final base = await fingerprintOf(tester, 'android', android);
      expect(base, matches(RegExp(r'^[0-9a-f]{4}·[0-9a-f]{4}·[0-9a-f]{4}$')));
      expect(await fingerprintOf(tester, 'android', android), base);
      expect(await fingerprintOf(tester, 'android', android, language: 'en'), isNot(base));
      expect(await fingerprintOf(tester, 'android', android, screen: const Size(1440, 3120)), isNot(base));
      expect(await fingerprintOf(tester, 'android', android, screen: const Size(2400, 1080)), base);
    });

    testWidgets('on iOS the language and the screen size change the fingerprint', (tester) async {
      final base = await fingerprintOf(tester, 'ios', ios);
      expect(await fingerprintOf(tester, 'ios', ios, language: 'en'), isNot(base));
      expect(await fingerprintOf(tester, 'ios', ios, screen: const Size(1179, 2556)), isNot(base));
    });

    testWidgets('on another system it still uses the language and the screen size', (tester) async {
      final base = await fingerprintOf(tester, 'linux', const {});
      expect(await fingerprintOf(tester, 'linux', const {}, language: 'en'), isNot(base));
    });
  });

  test('truncate respects the API limits', () {
    expect(truncate('Android 16', 30), 'Android 16');
    expect(truncate('x' * 100, 80), hasLength(80));
  });

  test('Signals without a device sends no fields; with a device it sends all four', () {
    final s = Signals();
    expect(s.toJson(), isEmpty);
    s.device = const DeviceInfo(
      fingerprint: 'd4f1·9a3c·e7b2',
      model: 'iPhone 15',
      operatingSystem: 'iOS 18.1',
      appVersion: '1.0.0+1',
    );
    expect(s.toJson(), {
      'deviceFingerprint': 'd4f1·9a3c·e7b2',
      'deviceModel': 'iPhone 15',
      'operatingSystem': 'iOS 18.1',
      'appVersion': '1.0.0+1',
    });
  });
}
