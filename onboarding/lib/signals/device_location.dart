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

/// Coordinates used only to look up the city.
typedef Coordinates = ({double latitude, double longitude});

/// Reads the city with `geolocator` (coarse permission, low accuracy) and `geocoding` (reverse geocoding on
/// the phone). The coordinates are only used to find the city and are not sent nor stored.
///
/// The plugin calls can be replaced: tests pass fake ones.
class PlatformDeviceLocationSource implements DeviceLocationSource {
  PlatformDeviceLocationSource({
    Future<LocationPermission> Function()? checkPermission,
    Future<LocationPermission> Function()? requestPermission,
    Future<bool> Function()? serviceEnabled,
    Future<Coordinates> Function()? position,
    Future<List<Placemark>> Function(double latitude, double longitude)? placemarks,
    this.timeLimit = const Duration(seconds: 15),
  })  : _checkPermission = checkPermission ?? Geolocator.checkPermission,
        _requestPermission = requestPermission ?? Geolocator.requestPermission,
        _serviceEnabled = serviceEnabled ?? Geolocator.isLocationServiceEnabled,
        _position = position ?? _lowAccuracyPosition,
        _placemarks = placemarks ?? placemarkFromCoordinates;

  final Future<LocationPermission> Function() _checkPermission;
  final Future<LocationPermission> Function() _requestPermission;
  final Future<bool> Function() _serviceEnabled;
  final Future<Coordinates> Function() _position;
  final Future<List<Placemark>> Function(double latitude, double longitude) _placemarks;

  /// The whole lookup (position and city) gives up after this time.
  final Duration timeLimit;

  static final _countryIso = RegExp(r'^[A-Za-z]{2}$');

  static Future<Coordinates> _lowAccuracyPosition() async {
    final p = await Geolocator.getCurrentPosition(locationSettings: const LocationSettings(accuracy: LocationAccuracy.low));
    return (latitude: p.latitude, longitude: p.longitude);
  }

  @override
  Future<DeviceLocation> read() async {
    try {
      var permission = await _checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await _requestPermission();
      }
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        return const DeviceLocation.denied();
      }
      if (permission == LocationPermission.unableToDetermine || !await _serviceEnabled()) {
        return const DeviceLocation.unavailable();
      }
      return await _city().timeout(timeLimit);
    } on Exception {
      // Timeout, permission dialog already open, location services off, no geocoder on the phone...
      return const DeviceLocation.unavailable();
    }
  }

  Future<DeviceLocation> _city() async {
    final position = await _position();
    final places = await _placemarks(position.latitude, position.longitude);
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
