import 'package:flutter_test/flutter_test.dart';
import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/onboarding/saved_request.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  const store = PreferencesSavedRequestStore();
  const id = '11111111-2222-3333-4444-555555555555';

  setUp(() => SharedPreferences.setMockInitialValues({}));

  group('request in progress', () {
    test('nothing is kept at first', () async {
      expect(await store.read(), isNull);
      expect(await store.selfieTaken(id), isFalse);
      expect(await store.submitted(id), isNull);
    });

    test('keeps the id and whether the selfie of that request was taken', () async {
      await store.save(id);
      await store.saveSelfieTaken(id);

      expect(await store.read(), id);
      expect(await store.selfieTaken(id), isTrue);
      expect(await store.selfieTaken('another'), isFalse);
    });

    test('clear forgets the id and the selfie', () async {
      await store.save(id);
      await store.saveSelfieTaken(id);
      await store.clear();

      expect(await store.read(), isNull);
      expect(await store.selfieTaken(id), isFalse);
    });
  });

  group('request submitted', () {
    test('keeps its number, how it was left and whether it was signed', () async {
      await store.saveSubmitted(id, const SubmittedRequest('SOL-2026-00454', SubmissionStatus.enhancedReview, signed: true));

      final kept = await store.submitted(id);
      expect(await store.read(), id);
      expect(kept?.number, 'SOL-2026-00454');
      expect(kept?.status, SubmissionStatus.enhancedReview);
      expect(kept?.signed, isTrue);
    });

    test('it is not signed unless said so', () async {
      await store.saveSubmitted(id, const SubmittedRequest('SOL-2026-00454', SubmissionStatus.completed));

      expect((await store.submitted(id))?.signed, isFalse);
    });

    test('it belongs to its request: another id finds nothing', () async {
      await store.saveSubmitted(id, const SubmittedRequest('SOL-2026-00454', SubmissionStatus.completed));

      expect(await store.submitted('another'), isNull);
    });

    test('the selfie is no longer needed once it is submitted', () async {
      await store.saveSelfieTaken(id);
      await store.saveSubmitted(id, const SubmittedRequest('SOL-2026-00454', SubmissionStatus.completed));

      expect(await store.selfieTaken(id), isFalse);
    });

    test('a status this version does not know is told as received', () async {
      SharedPreferences.setMockInitialValues({
        'onboarding.request_id': id,
        'onboarding.submitted_request_id': id,
        'onboarding.submitted_number': 'SOL-2026-00454',
        'onboarding.submitted_status': 'somethingNew',
      });

      expect((await store.submitted(id))?.status, SubmissionStatus.completed);
    });

    test('starting another request forgets the submitted one', () async {
      await store.saveSubmitted(id, const SubmittedRequest('SOL-2026-00454', SubmissionStatus.completed, signed: true));
      await store.save('another');

      expect(await store.read(), 'another');
      expect(await store.submitted(id), isNull);
      expect(await store.submitted('another'), isNull);
    });

    test('clear forgets it too', () async {
      await store.saveSubmitted(id, const SubmittedRequest('SOL-2026-00454', SubmissionStatus.completed));
      await store.clear();

      expect(await store.read(), isNull);
      expect(await store.submitted(id), isNull);
    });
  });
}
