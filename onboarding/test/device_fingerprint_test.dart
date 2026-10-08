import 'package:flutter_test/flutter_test.dart';

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
