import 'dart:async';

import 'package:geocoding/geocoding.dart';
import 'package:geolocator/geolocator.dart';

import 'device_fingerprint.dart' show truncate;

/// Result of asking for the approximate location (VDI-73), with the name the API uses
/// (`deviceLocationStatus`).
enum DeviceLocationStatus {
  /// Permission granted and the phone found the city.
  available('AVAILABLE'),

  /// The person chose "Ahora no" or refused the system permission.
  denied('DENIED'),

  /// Permission granted, but the phone could not find the city (location off, timeout...).
  unavailable('UNAVAILABLE');

  const DeviceLocationStatus(this.apiName);

  final String apiName;
}

/// Approximate location of the request: only city and country, never coordinates.
class DeviceLocation {
  const DeviceLocation(this.status, {this.place, this.countryIso});

  const DeviceLocation.denied() : this(DeviceLocationStatus.denied);

  const DeviceLocation.unavailable() : this(DeviceLocationStatus.unavailable);

  final DeviceLocationStatus status;

  /// "City, Country" (`approximateLocation`, at most 120 characters).
  final String? place;

  /// ISO 3166-1 alpha-2 (`countryIso`).
  final String? countryIso;

  /// Fields of `PUT /signals`. Without permission only the status travels: the server stores the location as
  /// unavailable and the request goes on.
  Map<String, Object?> toJson() => {
        'deviceLocationStatus': status.apiName,
        if (status == DeviceLocationStatus.available) ...{
          'approximateLocation': ?place,
          'countryIso': ?countryIso,
        },
      };
}

/// Source of the approximate location; tests use a fake one.
abstract interface class DeviceLocationSource {
  /// Asks the system for the permission (if it was not given yet) and returns the city. It never throws: any
  /// problem ends as [DeviceLocationStatus.denied] or [DeviceLocationStatus.unavailable].
  Future<DeviceLocation> read();
}

/// Reads the city with `geolocator` (coarse permission, low accuracy) and `geocoding` (reverse geocoding on
/// the phone). The coordinates are only used to find the city and are not sent nor stored.
class PlatformDeviceLocationSource implements DeviceLocationSource {
  /// The whole lookup (position and city) gives up after this time.
  static const timeLimit = Duration(seconds: 15);

  static final _countryIso = RegExp(r'^[A-Za-z]{2}$');

  @override
  Future<DeviceLocation> read() async {
    try {
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        return const DeviceLocation.denied();
      }
      if (permission == LocationPermission.unableToDetermine || !await Geolocator.isLocationServiceEnabled()) {
        return const DeviceLocation.unavailable();
      }
      return await _city().timeout(timeLimit);
    } on TimeoutException {
      return const DeviceLocation.unavailable();
    } on Exception {
      // Permission dialog already open, location services off, no geocoder on the phone...
      return const DeviceLocation.unavailable();
    }
  }

  Future<DeviceLocation> _city() async {
    final position = await Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.low),
    );
    final places = await placemarkFromCoordinates(position.latitude, position.longitude);
    if (places.isEmpty) return const DeviceLocation.unavailable();
    final p = places.first;
    final city = [p.locality, p.subAdministrativeArea, p.administrativeArea]
        .firstWhere((s) => s != null && s.trim().isNotEmpty, orElse: () => null)
        ?.trim();
    final country = p.country?.trim();
    final parts = [?city, if (country != null && country.isNotEmpty) country];
    if (parts.isEmpty) return const DeviceLocation.unavailable();
    final iso = p.isoCountryCode?.trim();
    return DeviceLocation(
      DeviceLocationStatus.available,
      place: truncate(parts.join(', '), 120),
      countryIso: iso != null && _countryIso.hasMatch(iso) ? iso.toUpperCase() : null,
    );
  }
}
