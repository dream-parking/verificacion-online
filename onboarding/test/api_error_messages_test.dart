import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/onboarding/api_error_messages.dart';

void main() {
  ApiException error(int? status, {List<String> fields = const []}) =>
      ApiException('detail from the server', status: status, fields: fields);

  group('forSave', () {
    test('409 says the request was already submitted', () {
      expect(ApiErrorMessages.forSave(error(409)), ApiErrorMessages.alreadySubmitted);
    });

    test('409 uses the wording of the step when it has one', () {
      expect(ApiErrorMessages.forSave(error(409), conflict: 'Propio'), 'Propio');
    });

    test('400 names the rejected fields', () {
      expect(
        ApiErrorMessages.forSave(error(400, fields: ['dui', 'mobilePhone'])),
        'Revisa estos datos e inténtalo de nuevo: DUI, celular.',
      );
    });

    test('400 without known fields stays generic', () {
      expect(ApiErrorMessages.forSave(error(400, fields: ['unknown'])), 'Revisa tus datos e inténtalo de nuevo.');
      expect(ApiErrorMessages.forSave(error(400)), 'Revisa tus datos e inténtalo de nuevo.');
    });

    test('anything else is taken as a connection problem and never shows the server text', () {
      for (final status in [null, 500, 503, 404]) {
        final message = ApiErrorMessages.forSave(error(status));
        expect(message, ApiErrorMessages.connection);
        expect(message, isNot(contains('server')));
      }
    });
  });

  test('forDuiUpload: 409 asks to check the basic data, the rest is connection', () {
    expect(ApiErrorMessages.forDuiUpload(error(409)), ApiErrorMessages.duiNotSaved);
    expect(ApiErrorMessages.forDuiUpload(error(500)), ApiErrorMessages.connection);
  });

  test('forSelfieUpload: 409 says the request was submitted, the rest is connection', () {
    expect(ApiErrorMessages.forSelfieUpload(error(409)), ApiErrorMessages.alreadySubmitted);
    expect(ApiErrorMessages.forSelfieUpload(error(null)), ApiErrorMessages.connection);
  });
}
