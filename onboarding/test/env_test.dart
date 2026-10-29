import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/env.dart';
import 'package:onboarding/main.dart';

void main() {
  group('API URL (VDI-141)', () {
    test('there is no fallback: without API_URL the app has no URL', () {
      expect(apiBaseUrl, isEmpty);
    });

    test('an empty or blank URL is rejected and the message says how to define it', () {
      for (final url in ['', '   ']) {
        expect(
          () => requireApiUrl(url, env: 'dev'),
          throwsA(isA<StateError>().having((e) => e.message, 'message', contains('--dart-define=API_URL'))),
        );
      }
    });

    test('a URL is returned without the spaces around it', () {
      expect(requireApiUrl(' https://api.dev.example.com ', env: 'dev'), 'https://api.dev.example.com');
    });

    test('a Dev or QA build cannot point to the local machine', () {
      for (final env in ['dev', 'qa']) {
        for (final url in ['http://localhost:8080', 'http://127.0.0.1:8080', 'http://[::1]:8080', 'http://10.0.2.2:8080']) {
          expect(() => requireApiUrl(url, env: env), throwsA(isA<StateError>()), reason: '$env $url');
        }
      }
    });

    test('a local run can point to the local machine', () {
      expect(requireApiUrl('http://10.0.2.2:8080', env: 'local'), 'http://10.0.2.2:8080');
    });

    test('a host that only starts like localhost is not taken as local', () {
      expect(requireApiUrl('https://localhost.example.com', env: 'qa'), 'https://localhost.example.com');
    });

    test('the API client needs a URL: without one it does not start', () {
      expect(() => OnboardingApi(), throwsA(isA<StateError>()));
      expect(() => OnboardingApi(baseUrl: 'https://api.test'), returnsNormally);
    });

    testWidgets('an app built without API_URL says so instead of calling another environment', (tester) async {
      await tester.pumpWidget(const MissingApiUrl());
      expect(find.text(MissingApiUrl.message), findsOneWidget);
      expect(find.byType(Text), findsOneWidget);
    });
  });
}
