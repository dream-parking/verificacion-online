import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/signals/device_fingerprint.dart';
import 'package:onboarding/signals/location.dart';
import 'package:onboarding/signals/signals.dart';

void main() {
  test('an available location sends its status, coordinates and accuracy', () {
    const location = ApproximateLocation.available(latitude: 13.6929, longitude: -89.2182, accuracyMeters: 1200);
    expect(location.toJson(), {
      'locationStatus': 'AVAILABLE',
      'latitude': 13.6929,
      'longitude': -89.2182,
      'locationAccuracyMeters': 1200,
    });
  });

  test('a not available location sends only its status, never coordinates', () {
    expect(const ApproximateLocation.notAvailable(LocationStatus.permissionDenied).toJson(),
        {'locationStatus': 'PERMISSION_DENIED'});
    expect(const ApproximateLocation.notAvailable(LocationStatus.unavailable).toJson(),
        {'locationStatus': 'UNAVAILABLE'});
  });

  test('Signals sends the location together with the device', () {
    final s = Signals()
      ..device = const DeviceInfo(
        fingerprint: 'd4f1·9a3c·e7b2',
        model: 'iPhone 15',
        operatingSystem: 'iOS 18.1',
        appVersion: '1.0.0+1',
      )
      ..location = const ApproximateLocation.notAvailable(LocationStatus.permissionDenied);
    expect(s.toJson(), containsPair('locationStatus', 'PERMISSION_DENIED'));
    expect(s.toJson(), containsPair('deviceFingerprint', 'd4f1·9a3c·e7b2'));
  });
}
