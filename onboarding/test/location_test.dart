import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart'
    show GeolocatorPlatform, LocationAccuracy, LocationPermission, LocationSettings, Position;

import 'package:onboarding/signals/device_fingerprint.dart';
import 'package:onboarding/signals/location.dart';
import 'package:onboarding/signals/signals.dart';

/// Phone location services with scripted answers; records whether the permission was requested.
class FakePlatform implements LocationPlatform {
  FakePlatform({
    this.current = LocationPermission.whileInUse,
    this.afterRequest = LocationPermission.whileInUse,
    this.serviceEnabled = true,
    this.position,
  });

  final LocationPermission current;
  final LocationPermission afterRequest;
  final bool serviceEnabled;
  final Future<({double latitude, double longitude, double accuracy})> Function()? position;
  var requests = 0;

  @override
  Future<LocationPermission> checkPermission() async => current;

  @override
  Future<LocationPermission> requestPermission() async {
    requests++;
    return afterRequest;
  }

  @override
  Future<bool> isLocationServiceEnabled() async => serviceEnabled;

  @override
  Future<({double latitude, double longitude, double accuracy})> currentPosition() =>
      position?.call() ?? Future.value((latitude: 13.6929, longitude: -89.2182, accuracy: 1199.6));
}

/// Replaces the geolocator plugin itself, to test the adapter that calls it.
class FakeGeolocatorPlatform extends GeolocatorPlatform {
  LocationSettings? settings;
  var requests = 0;

  @override
  Future<LocationPermission> checkPermission() async => LocationPermission.denied;

  @override
  Future<LocationPermission> requestPermission() async {
    requests++;
    return LocationPermission.whileInUse;
  }

  @override
  Future<bool> isLocationServiceEnabled() async => true;

  @override
  Future<Position> getCurrentPosition({LocationSettings? locationSettings}) async {
    settings = locationSettings;
    return Position(
      latitude: 13.6929,
      longitude: -89.2182,
      accuracy: 1200,
      timestamp: DateTime.utc(2026, 10, 8),
      altitude: 0,
      altitudeAccuracy: 0,
      heading: 0,
      headingAccuracy: 0,
      speed: 0,
      speedAccuracy: 0,
    );
  }
}

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

  group('PlatformLocationSource', () {
    Future<ApproximateLocation> read(FakePlatform platform, {Duration timeout = const Duration(seconds: 15)}) =>
        PlatformLocationSource(platform: platform, timeout: timeout).read();

    test('with the permission already granted it reads the position without asking again', () async {
      final platform = FakePlatform();
      final location = await read(platform);
      expect(location.status, LocationStatus.available);
      expect(location.latitude, 13.6929);
      expect(location.longitude, -89.2182);
      expect(location.accuracyMeters, 1200);
      expect(platform.requests, 0);
    });

    test('asks for the permission once and reads the position when granted', () async {
      final platform = FakePlatform(current: LocationPermission.denied);
      expect((await read(platform)).status, LocationStatus.available);
      expect(platform.requests, 1);
    });

    test('if the applicant does not grant it, the location is not available for lack of permission', () async {
      final platform = FakePlatform(current: LocationPermission.denied, afterRequest: LocationPermission.denied);
      expect((await read(platform)).status, LocationStatus.permissionDenied);
    });

    test('a permission denied forever is not asked again', () async {
      final platform = FakePlatform(current: LocationPermission.deniedForever);
      expect((await read(platform)).status, LocationStatus.permissionDenied);
      expect(platform.requests, 0);
    });

    test('with the location turned off or an undetermined permission it is unavailable', () async {
      expect((await read(FakePlatform(serviceEnabled: false))).status, LocationStatus.unavailable);
      expect((await read(FakePlatform(current: LocationPermission.unableToDetermine))).status, LocationStatus.unavailable);
    });

    test('an error or a position that takes too long is unavailable, never an exception', () async {
      final failing = FakePlatform(position: () => Future.error(Exception('GPS error')));
      expect((await read(failing)).status, LocationStatus.unavailable);

      final slow = FakePlatform(position: () => Completer<({double latitude, double longitude, double accuracy})>().future);
      expect((await read(slow, timeout: const Duration(milliseconds: 10))).status, LocationStatus.unavailable);
    });
  });

  group('GeolocatorLocationPlatform', () {
    late FakeGeolocatorPlatform plugin;

    setUp(() {
      final original = GeolocatorPlatform.instance;
      plugin = FakeGeolocatorPlatform();
      GeolocatorPlatform.instance = plugin;
      addTearDown(() => GeolocatorPlatform.instance = original);
    });

    test('asks the plugin for the permission and a low-accuracy position', () async {
      const platform = GeolocatorLocationPlatform();
      expect(await platform.checkPermission(), LocationPermission.denied);
      expect(await platform.requestPermission(), LocationPermission.whileInUse);
      expect(await platform.isLocationServiceEnabled(), isTrue);

      final position = await platform.currentPosition();
      expect(position.latitude, 13.6929);
      expect(position.longitude, -89.2182);
      expect(position.accuracy, 1200);
      expect(plugin.settings?.accuracy, LocationAccuracy.low, reason: 'only the approximate area is needed');
    });

    test('the default reader uses the plugin end to end', () async {
      final location = await const PlatformLocationSource().read();
      expect(location.toJson(), {
        'locationStatus': 'AVAILABLE',
        'latitude': 13.6929,
        'longitude': -89.2182,
        'locationAccuracyMeters': 1200,
      });
      expect(plugin.requests, 1);
    });
  });
}
