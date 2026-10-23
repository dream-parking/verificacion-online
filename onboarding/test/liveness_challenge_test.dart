import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:onboarding/identity/frame_quality.dart';
import 'package:onboarding/liveness/face_camera.dart';
import 'package:onboarding/liveness/liveness_challenge.dart';

/// One face in the middle of the oval.
FaceFrame face({
  double yaw = 0,
  double eyes = 0.95,
  int? trackingId = 1,
  Rect box = const Rect.fromLTWH(0.25, 0.2, 0.5, 0.5),
  FrameHint light = FrameHint.good,
}) =>
    FaceFrame(
      faces: [DetectedFace(box: box, yaw: yaw, leftEyeOpen: eyes, rightEyeOpen: eyes, trackingId: trackingId)],
      light: light,
    );

void main() {
  LivenessChallenge feed(List<FaceFrame> frames) {
    final c = LivenessChallenge();
    frames.forEach(c.add);
    return c;
  }

  final front = [face(), face(), face()];

  test('the same face looks at the camera, turns, blinks and looks again: the selfie can be taken', () {
    final c = LivenessChallenge();
    for (final f in front) {
      c.add(f);
    }
    expect(c.step, ChallengeStep.turn);
    expect(c.isDone(ChallengeStep.lookFront), isTrue);

    c.add(face(yaw: -30));
    expect(c.step, ChallengeStep.blink, reason: 'either side counts');

    c
      ..add(face())
      ..add(face(eyes: 0.05))
      ..add(face());
    expect(c.isDone(ChallengeStep.blink), isTrue);
    expect(c.ready, isFalse, reason: 'it waits for the face to look at the camera for the photo');

    for (final f in front) {
      c.add(f);
    }
    expect(c.ready, isTrue);
  });

  test('a passing frame looking at the camera does not count', () {
    final c = feed([face(), face(), face(yaw: 20), face()]);
    expect(c.step, ChallengeStep.lookFront);
  });

  test('a still photo or a screen held in front of the camera never gets to the selfie', () {
    // The face does not turn or blink: it stays on «Gira la cabeza».
    final c = feed([for (var i = 0; i < 100; i++) face()]);
    expect(c.step, ChallengeStep.turn);
    expect(c.ready, isFalse);
  });

  test('a face that does not blink never gets to the selfie', () {
    final c = feed([...front, face(yaw: 35), for (var i = 0; i < 50; i++) face()]);
    expect(c.step, ChallengeStep.blink);
    expect(c.isDone(ChallengeStep.blink), isFalse);
    expect(c.ready, isFalse);
  });

  test('eyes closed from the start are not a blink: it has to see them open first', () {
    final c = feed([...front, face(yaw: 35), face(eyes: 0.05), face(eyes: 0.05)]);
    expect(c.isDone(ChallengeStep.blink), isFalse);
  });

  test('without knowing the eyes it cannot see a blink', () {
    final unknownEyes = FaceFrame(faces: [DetectedFace(box: const Rect.fromLTWH(0.25, 0.2, 0.5, 0.5), yaw: 0, trackingId: 1)]);
    final c = feed([...front, face(yaw: 35), unknownEyes, unknownEyes, unknownEyes]);
    expect(c.isDone(ChallengeStep.blink), isFalse);
  });

  test('another face in the middle of the check starts it over', () {
    final c = feed([...front, face(yaw: 35), face(trackingId: 2)]);
    expect(c.step, ChallengeStep.lookFront);
    expect(c.isDone(ChallengeStep.lookFront), isFalse);
  });

  test('after the blink, another face looking at the camera does not get the photo', () {
    final c = feed([...front, face(yaw: 35), face(), face(eyes: 0.05), face(), ...[for (var i = 0; i < 3; i++) face(trackingId: 9)]]);
    expect(c.ready, isFalse);
    expect(c.step, ChallengeStep.turn, reason: 'the new face is checked from the start');
  });

  test('two faces stop the check and say only one person must appear', () {
    final two = FaceFrame(faces: [face().faces.single, face(trackingId: 2).faces.single]);
    final c = feed([face(), face(), two, face()]);
    expect(c.hint, FaceHint.good);
    expect(c.step, ChallengeStep.lookFront, reason: 'the count started over');
    c.add(two);
    expect(c.hint, FaceHint.manyFaces);
  });

  test('without a face it asks to place it in the oval', () {
    final c = feed([const FaceFrame()]);
    expect(c.hint, FaceHint.searching);
  });

  test('in the dark it asks for light and does not move on', () {
    final c = feed([for (var i = 0; i < 5; i++) face(light: FrameHint.tooDark)]);
    expect(c.hint, FaceHint.tooDark);
    expect(c.step, ChallengeStep.lookFront);
  });

  test('a face too far, too close or off the middle is told how to fix it and does not count', () {
    expect(feed([face(box: const Rect.fromLTWH(0.4, 0.35, 0.2, 0.2))]).hint, FaceHint.tooFar);
    expect(feed([face(box: const Rect.fromLTWH(-0.05, 0.0, 1.1, 0.6))]).hint, FaceHint.tooClose);
    final aside = feed([for (var i = 0; i < 5; i++) face(box: const Rect.fromLTWH(0.55, 0.2, 0.5, 0.5))]);
    expect(aside.hint, FaceHint.offCenter);
    expect(aside.step, ChallengeStep.lookFront);
  });

  test('the photo is not taken with the head turned or the eyes closed', () {
    final afterBlink = [...front, face(yaw: 35), face(), face(eyes: 0.05), face()];
    expect(feed([...afterBlink, for (var i = 0; i < 5; i++) face(yaw: 30)]).ready, isFalse);
    expect(feed([...afterBlink, for (var i = 0; i < 5; i++) face(eyes: 0.4)]).ready, isFalse);
  });

  group('measured with a person in front of a laptop webcam', () {
    // Looking at the webcam read about 8°, the face took 70 % to 90 % of the image width, and a natural turn reached
    // 17° one way and -9.5° the other. With 25° from 0° it never got past «Gira la cabeza».
    FaceFrame webcam({double yaw = 8, double eyes = 0.99, double width = 0.72}) =>
        face(yaw: yaw, eyes: eyes, box: Rect.fromLTWH(0.5 - width / 2, 0.31, width, 0.4));

    test('a face at that distance is well placed', () {
      for (final width in [0.68, 0.72, 0.84, 0.9]) {
        expect(feed([webcam(width: width)]).hint, FaceHint.good, reason: 'width $width');
      }
    });

    test('the turn is measured from how the face looked at the camera', () {
      final front = [webcam(), webcam(yaw: 7.6), webcam(yaw: 9.2)];
      expect(feed([...front, webcam(yaw: 17.2)]).step, ChallengeStep.turn, reason: '9° from the start is not a turn');
      expect(feed([...front, webcam(yaw: -9.5)]).step, ChallengeStep.blink, reason: '17.5° from the start is');
    });

    test('the whole check gets to the selfie', () {
      final c = feed([
        webcam(), webcam(yaw: 7.6), webcam(yaw: 9.2),
        webcam(yaw: -9.5),
        webcam(), webcam(eyes: 0.03), webcam(),
        webcam(yaw: 7.5), webcam(yaw: 9.9), webcam(yaw: 9.4),
      ]);
      expect(c.ready, isTrue);
    });

    test('the final photo is taken looking at the camera as at the start, not turned', () {
      final c = feed([
        webcam(), webcam(), webcam(),
        webcam(yaw: -9.5), webcam(), webcam(eyes: 0.03), webcam(),
        for (var i = 0; i < 5; i++) webcam(yaw: -9),
      ]);
      expect(c.ready, isFalse);
    });
  });

  test('once ready it stays ready', () {
    final c = feed([...front, face(yaw: 35), face(), face(eyes: 0.05), face(), ...front, const FaceFrame()]);
    expect(c.ready, isTrue);
  });
}
