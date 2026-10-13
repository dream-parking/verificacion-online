import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:geocoding/geocoding.dart';
import 'package:geolocator/geolocator.dart';

import 'package:onboarding/signals/device_location.dart';

/// Place as the geocoder returns it.
Placemark place({String? locality, String? administrativeArea, String? country, String? isoCountryCode}) =>
    Placemark.fromMap({
      for (final key in const [
        'name', 'street', 'postalCode', 'subAdministrativeArea', 'subLocality', 'thoroughfare', 'subThoroughfare', //
      ])
        key: '',
      'locality': locality ?? '',
      'administrativeArea': administrativeArea ?? '',
      'country': country ?? '',
      'isoCountryCode': isoCountryCode ?? '',
    });

/// Phone with fake plugins: answers what each test sets and records what was asked (VDI-73).
class FakePhone {
  FakePhone({
    this.permission = LocationPermission.whileInUse,
    this.afterRequest = LocationPermission.whileInUse,
    this.serviceEnabled = true,
    List<Placemark>? places,
  }) : places = places ?? [place(locality: 'San Salvador', country: 'El Salvador', isoCountryCode: 'SV')];

  LocationPermission permission;
  LocationPermission afterRequest;
  bool serviceEnabled;
  List<Placemark> places;

  /// When set, the position never arrives (to test the time limit).
  Completer<Coordinates>? hangingPosition;

  /// When set, looking up the city throws it.
  Exception? geocoderError;

  var requests = 0;
  var positions = 0;
  (double, double)? lookedUp;

  PlatformDeviceLocationSource source({Duration timeLimit = const Duration(seconds: 15)}) =>
      PlatformDeviceLocationSource(
        checkPermission: () async => permission,
        requestPermission: () async {
          requests++;
          return afterRequest;
        },
        serviceEnabled: () async => serviceEnabled,
        position: () {
          positions++;
          return hangingPosition?.future ?? Future.value((latitude: 13.69, longitude: -89.21));
        },
        placemarks: (latitude, longitude) async {
          lookedUp = (latitude, longitude);
          final error = geocoderError;
          if (error != null) throw error;
          return places;
        },
        timeLimit: timeLimit,
      );
}

void main() {
  group('PlatformDeviceLocationSource', () {
    test('with permission it returns only "City, Country" and the ISO country', () async {
      final phone = FakePhone();

      final location = await phone.source().read();

      expect(location.status, DeviceLocationStatus.available);
      expect(location.place, 'San Salvador, El Salvador');
      expect(location.countryIso, 'SV');
      expect(phone.requests, 0, reason: 'the permission was already given');
      expect(phone.lookedUp, (13.69, -89.21), reason: 'the coordinates are only used to find the city');
      expect(location.toJson(), {
        'deviceLocationStatus': 'AVAILABLE',
        'approximateLocation': 'San Salvador, El Salvador',
        'countryIso': 'SV',
      });
    });

    test('asks for the permission when it was not given yet', () async {
      final phone = FakePhone(permission: LocationPermission.denied);

      final location = await phone.source().read();

      expect(phone.requests, 1);
      expect(location.status, DeviceLocationStatus.available);
    });

    // CP-41: refusing the system permission ends as "not available", never as an error.
    test('if the person refuses the system permission it is DENIED and the position is not read', () async {
      final phone = FakePhone(permission: LocationPermission.denied, afterRequest: LocationPermission.denied);

      final location = await phone.source().read();

      expect(location.status, DeviceLocationStatus.denied);
      expect(location.place, isNull);
      expect(phone.positions, 0);
      expect(location.toJson(), {'deviceLocationStatus': 'DENIED'});
    });

    test('if it was refused forever it is DENIED without asking again', () async {
      final phone = FakePhone(permission: LocationPermission.deniedForever);

      final location = await phone.source().read();

      expect(location.status, DeviceLocationStatus.denied);
      expect(phone.requests, 0);
    });

    test('if the system cannot tell the permission it is UNAVAILABLE', () async {
      final location = await FakePhone(permission: LocationPermission.unableToDetermine).source().read();

      expect(location.status, DeviceLocationStatus.unavailable);
    });

    test('with the location turned off it is UNAVAILABLE', () async {
      final phone = FakePhone(serviceEnabled: false);

      final location = await phone.source().read();

      expect(location.status, DeviceLocationStatus.unavailable);
      expect(phone.positions, 0);
      expect(location.toJson(), {'deviceLocationStatus': 'UNAVAILABLE'});
    });

    test('if the phone does not find any place it is UNAVAILABLE', () async {
      final location = await FakePhone(places: []).source().read();

      expect(location.status, DeviceLocationStatus.unavailable);
    });

    test('if the place has neither city nor country it is UNAVAILABLE', () async {
      final location = await FakePhone(places: [place(locality: ' ', isoCountryCode: 'SV')]).source().read();

      expect(location.status, DeviceLocationStatus.unavailable);
    });

    test('without a locality it uses the department; an invalid ISO code is not sent', () async {
      final phone = FakePhone(places: [
        place(locality: '', administrativeArea: 'Santa Ana', country: 'El Salvador', isoCountryCode: 'SLV'),
      ]);

      final location = await phone.source().read();

      expect(location.place, 'Santa Ana, El Salvador');
      expect(location.countryIso, isNull);
      expect(location.toJson().containsKey('countryIso'), isFalse);
    });

    test('a lowercase ISO code is sent in uppercase and a long name is cut to 120 characters', () async {
      final phone = FakePhone(places: [place(locality: 'a' * 130, country: 'El Salvador', isoCountryCode: 'sv')]);

      final location = await phone.source().read();

      expect(location.place, hasLength(120));
      expect(location.countryIso, 'SV');
    });

    test('if the city does not arrive in time it is UNAVAILABLE', () async {
      final phone = FakePhone()..hangingPosition = Completer<Coordinates>();

      final location = await phone.source(timeLimit: const Duration(milliseconds: 10)).read();

      expect(location.status, DeviceLocationStatus.unavailable);
    });

    test('if the geocoder fails it is UNAVAILABLE, it does not throw', () async {
      final phone = FakePhone()..geocoderError = Exception('no geocoder on this phone');

      final location = await phone.source().read();

      expect(location.status, DeviceLocationStatus.unavailable);
    });
  });
}
