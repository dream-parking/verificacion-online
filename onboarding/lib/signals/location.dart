import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart' show Geolocator, LocationAccuracy, LocationPermission, LocationSettings;

/// Whether the approximate location could be captured (VDI-41). Same values as `locationStatus` in the API.
enum LocationStatus {
  /// Latitude and longitude were captured.
  available('AVAILABLE'),

  /// The applicant did not grant the location permission.
  permissionDenied('PERMISSION_DENIED'),

  /// Permission granted, but the phone could not get a position (location off, error or timeout).
  unavailable('UNAVAILABLE');

  const LocationStatus(this.apiValue);

  final String apiValue;
}

/// Approximate location of the applicant, or why it is not available.
class ApproximateLocation {
  const ApproximateLocation.available({required double this.latitude, required double this.longitude, this.accuracyMeters})
      : status = LocationStatus.available;

  /// [status] is [LocationStatus.permissionDenied] or [LocationStatus.unavailable].
  const ApproximateLocation.notAvailable(this.status)
      : latitude = null,
        longitude = null,
        accuracyMeters = null;

  final LocationStatus status;
  final double? latitude;
  final double? longitude;

  /// Radius reported by the phone, in meters.
  final int? accuracyMeters;

  /// Fields of `PUT /signals`: coordinates only when available, as the API requires.
  Map<String, Object?> toJson() => {
        'locationStatus': status.apiValue,
        if (status == LocationStatus.available) ...{
          'latitude': latitude,
          'longitude': longitude,
          'locationAccuracyMeters': ?accuracyMeters,
        },
      };
}

/// Reads the approximate location; tests replace it with a fake one.
abstract interface class LocationSource {
  /// Never throws: without permission or position it returns a not available location.
  Future<ApproximateLocation> read();
}

/// The calls to the phone's location services; tests replace them.
abstract interface class LocationPlatform {
  Future<LocationPermission> checkPermission();

  Future<LocationPermission> requestPermission();

  Future<bool> isLocationServiceEnabled();

  /// One low-accuracy position: latitude, longitude and accuracy radius in meters.
  Future<({double latitude, double longitude, double accuracy})> currentPosition();
}

/// [LocationPlatform] backed by the geolocator plugin.
class GeolocatorLocationPlatform implements LocationPlatform {
  const GeolocatorLocationPlatform();

  @override
  Future<LocationPermission> checkPermission() => Geolocator.checkPermission();

  @override
  Future<LocationPermission> requestPermission() => Geolocator.requestPermission();

  @override
  Future<bool> isLocationServiceEnabled() => Geolocator.isLocationServiceEnabled();

  @override
  Future<({double latitude, double longitude, double accuracy})> currentPosition() async {
    final p = await Geolocator.getCurrentPosition(locationSettings: const LocationSettings(accuracy: LocationAccuracy.low));
    return (latitude: p.latitude, longitude: p.longitude, accuracy: p.accuracy);
  }
}

/// Asks for the location permission ("while using the app") and reads one approximate position.
///
/// Android only declares the approximate location permission, and the reading uses low accuracy:
/// the risk score needs the area, not the exact address.
class PlatformLocationSource implements LocationSource {
  const PlatformLocationSource({
    this.platform = const GeolocatorLocationPlatform(),
    this.timeout = const Duration(seconds: 15),
  });

  final LocationPlatform platform;

  /// The location must not delay the signals: after this time it is reported as unavailable.
  final Duration timeout;

  @override
  Future<ApproximateLocation> read() async {
    try {
      var permission = await platform.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await platform.requestPermission();
      }
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        return const ApproximateLocation.notAvailable(LocationStatus.permissionDenied);
      }
      if (permission == LocationPermission.unableToDetermine || !await platform.isLocationServiceEnabled()) {
        return const ApproximateLocation.notAvailable(LocationStatus.unavailable);
      }
      final position = await platform.currentPosition().timeout(timeout);
      return ApproximateLocation.available(
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyMeters: position.accuracy.round(),
      );
    } on Exception catch (e) {
      debugPrint('Could not read the location: $e');
      return const ApproximateLocation.notAvailable(LocationStatus.unavailable);
    }
  }
}
