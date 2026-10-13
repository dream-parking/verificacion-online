import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/api/onboarding_api.dart';
import 'package:onboarding/identity/identity_document.dart';

UnreadablePhotos from(String? reason, [String? side]) =>
    UnreadablePhotos.fromError(ApiException('x', status: 422, reason: reason, side: side));

void main() {
  group('reason of an unreadable photo', () {
    const reasons = {
      'BLURRY': (UnreadableReason.blurry, 'La foto salió borrosa. Toma otra con el teléfono bien quieto.'),
      'GLARE': (UnreadableReason.glare, 'Hay un reflejo sobre tus datos. Toma otra foto para poder seguir.'),
      'CROPPED': (
        UnreadableReason.cropped,
        'No se ven las 4 esquinas de tu DUI. Toma otra foto con todo el documento dentro del marco.',
      ),
      'TOO_DARK': (UnreadableReason.tooDark, 'La foto salió muy oscura. Busca un lugar con más luz y toma otra.'),
      'NOT_A_DUI': (UnreadableReason.notADui, 'No parece que sea un DUI. Toma otra foto de tu DUI.'),
      'WRONG_SIDES': (
        UnreadableReason.wrongSides,
        'Parece que el frente y el reverso están cambiados. Toma las dos fotos otra vez, empezando por el frente.',
      ),
      'OTHER': (UnreadableReason.other, 'No pudimos leer tu DUI. Toma otra foto para poder seguir.'),
    };

    for (final MapEntry(key: code, value: (reason, message)) in reasons.entries) {
      test('$code has its own message in Spanish', () {
        final photos = from(code, 'FRONT');
        expect(photos.reason, reason);
        expect(photos.message, message);
      });
    }

    test('every message is different, so the person knows what to fix', () {
      final messages = reasons.keys.map((c) => from(c).message).toSet();
      expect(messages, hasLength(reasons.length));
    });

    test('a reason we do not know is treated as OTHER', () {
      expect(from('SOMETHING_NEW').reason, UnreadableReason.other);
      expect(from(null).reason, UnreadableReason.other);
    });

    test('no message shows the technical code or English', () {
      for (final c in reasons.keys) {
        expect(from(c).message, isNot(contains(c)));
      }
    });
  });

  group('photos to take again', () {
    test('FRONT asks only for the front', () {
      final p = from('GLARE', 'FRONT');
      expect(p.sides, [DuiSide.front]);
      expect(p.title, 'Revisa la foto del frente');
    });

    test('BACK asks only for the back', () {
      final p = from('GLARE', 'BACK');
      expect(p.sides, [DuiSide.back]);
      expect(p.title, 'Revisa la foto del reverso');
    });

    test('BOTH, a missing side or an unknown one ask for both, front first', () {
      for (final side in ['BOTH', null, 'LEFT']) {
        final p = from('OTHER', side);
        expect(p.sides, [DuiSide.front, DuiSide.back], reason: 'side: $side');
        expect(p.title, 'Revisa las fotos de tu DUI');
      }
    });

    test('a photo over 5 MB asks for both photos', () {
      const p = UnreadablePhotos.tooLarge();
      expect(p.sides, [DuiSide.front, DuiSide.back]);
      expect(p.message, 'Una de las fotos pesa demasiado. Toma otra foto.');
    });

    test('there are three tips to take it better', () {
      expect(UnreadablePhotos.tips, hasLength(3));
    });
  });

  group('IdentityReading', () {
    test('a date that is not a date stays null', () {
      final r = IdentityReading.fromJson({'status': 'READ', 'birthDate': 'not-a-date', 'expiryDate': 20290601});
      expect(r.birthDate, isNull);
      expect(r.expiryDate, isNull);
    });

    test('an unknown status counts as read; only FAILED means the reader failed', () {
      expect(IdentityReading.fromJson({'status': 'READ'}).status, ReadingStatus.read);
      expect(IdentityReading.fromJson({'status': 'FAILED'}).status, ReadingStatus.failed);
    });
  });
}
