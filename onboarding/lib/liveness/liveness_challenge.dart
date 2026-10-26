import 'dart:ui';

import 'face_camera.dart';

/// The three things the person is asked in front of the camera, in order (VDI-116).
enum ChallengeStep {
  lookFront('Mira al frente', 'Mira de frente a la pantalla'),
  turn('Gira la cabeza', 'Gira la cabeza hacia un lado'),
  blink('Parpadea', 'Parpadea despacio');

  const ChallengeStep(this.label, this.instruction);

  /// Short name for the progress at the top.
  final String label;

  /// What the person is asked to do now.
  final String instruction;
}

/// What the camera tells the person about their face.
enum FaceHint {
  searching('Coloca tu rostro dentro del óvalo'),
  tooFar('Acércate un poco al teléfono'),
  tooClose('Aleja un poco el teléfono'),
  offCenter('Centra tu rostro dentro del óvalo'),
  manyFaces('Solo tú debes aparecer en la cámara'),
  tooDark('Hay poca luz. Busca un lugar más iluminado.'),
  good('Bien, tu rostro está dentro del óvalo');

  const FaceHint(this.message);

  final String message;

  bool get isGood => this == FaceHint.good;
}

/// Active liveness check on the phone (VDI-116): the same face has to look at the camera, turn the head and blink,
/// and then look at the camera again for the selfie. What it decides never leaves the phone: the API judges the
/// selfie on its own. It is fed the frames of the camera and says when the photo can be taken.
///
/// The turn is measured from how the face looked at the camera, not from 0°: a camera that is not right in front of
/// the person (the webcam of a laptop, a phone held to a side) already reads several degrees when they look at it.
/// Measured with a person in front of a laptop webcam: looking at it read about 8°, and a natural turn 10° to 17° away
/// from that.
class LivenessChallenge {
  LivenessChallenge({
    this.frontYaw = 15,
    this.turnYaw = 15,
    this.openEyes = 0.7,
    this.closedEyes = 0.25,
    this.steadyFrames = 3,
  });

  /// Degrees under which the head looks at the camera.
  final double frontYaw;

  /// Degrees the head has to turn away from how it looked at the camera. A side either way: the person only has to
  /// move.
  final double turnYaw;

  /// Chance of open eyes over which both are taken as open, and under [closedEyes] as closed.
  final double openEyes;
  final double closedEyes;

  /// Frames in a row looking at the camera inside the oval before it counts, so a passing frame does not.
  final int steadyFrames;

  var _step = ChallengeStep.lookFront;
  var _hint = FaceHint.searching;
  int? _face;
  var _steady = 0;

  /// Head angles while the face looked at the camera; their mean is where the turn is measured from.
  final _frontYaws = <double>[];
  double _baseYaw = 0;
  var _eyesOpenSeen = false;
  var _eyesClosedSeen = false;
  var _blinked = false;
  var _ready = false;

  /// The step being asked now; after the blink it stays on it while the person looks at the camera for the photo.
  ChallengeStep get step => _step;

  FaceHint get hint => _hint;

  /// The steps were done by the same face, which now looks at the camera with the eyes open: take the selfie.
  bool get ready => _ready;

  /// Steps already done, for the progress at the top.
  bool isDone(ChallengeStep s) => s.index < _step.index || (s == ChallengeStep.blink && _blinked);

  void add(FaceFrame frame) {
    if (_ready) return;
    // Only a scene too dark to see the face stops the check, as with the shutter of the DUI. «Poca luz» from the
    // exposure is only a warning there: on an iPhone it reads in any room with ordinary light (under 6.5 EV), so it
    // stopped the check forever. If the selfie does come out dark, the API answers 422 TOO_DARK and it is taken again.
    final dark = frame.light.blocksShutter;
    if (frame.faces.length > 1) {
      _hint = FaceHint.manyFaces;
      _steady = 0;
      return;
    }
    if (frame.faces.isEmpty) {
      _hint = dark ? FaceHint.tooDark : FaceHint.searching;
      _steady = 0;
      return;
    }
    final face = frame.faces.single;
    // Another face, or the same one lost and found again: what was done does not count for this one.
    if (face.trackingId != null && _face != null && face.trackingId != _face) _restart();
    _face = face.trackingId ?? _face;

    final placement = _placement(face.box);
    _hint = dark ? FaceHint.tooDark : placement;
    final yaw = face.yaw ?? 0;
    switch (_step) {
      case ChallengeStep.lookFront:
        final front = placement.isGood && !dark && yaw.abs() < frontYaw;
        _steady = front ? _steady + 1 : 0;
        if (front) {
          _frontYaws.add(yaw);
        } else {
          _frontYaws.clear();
        }
        if (_steady >= steadyFrames) {
          _baseYaw = _frontYaws.reduce((a, b) => a + b) / _frontYaws.length;
          _step = ChallengeStep.turn;
          _steady = 0;
        }
      case ChallengeStep.turn:
        // While turning the face leaves the middle of the oval: only that it is still there matters.
        if ((yaw - _baseYaw).abs() > turnYaw) _step = ChallengeStep.blink;
      case ChallengeStep.blink:
        if (!_blinked) {
          _blink(face);
        } else {
          final front = placement.isGood && !dark && (yaw - _baseYaw).abs() < frontYaw && _eyesOpen(face);
          _steady = front ? _steady + 1 : 0;
          _ready = _steady >= steadyFrames;
        }
    }
  }

  /// A blink is open, closed and open again.
  void _blink(DetectedFace face) {
    if (_eyesOpen(face)) {
      if (_eyesClosedSeen) {
        _blinked = true;
        _steady = 0;
      }
      _eyesOpenSeen = true;
    } else if (_eyesOpenSeen && _eyesClosed(face)) {
      _eyesClosedSeen = true;
    }
  }

  bool _eyesOpen(DetectedFace f) => (f.leftEyeOpen ?? 0) > openEyes && (f.rightEyeOpen ?? 0) > openEyes;

  bool _eyesClosed(DetectedFace f) =>
      f.leftEyeOpen != null && f.rightEyeOpen != null && f.leftEyeOpen! < closedEyes && f.rightEyeOpen! < closedEyes;

  /// The oval is in the middle of the image; the face has to fill a good part of it. With the image upright and
  /// narrower than tall, a face at a comfortable distance measured 70 % to 90 % of its width.
  static FaceHint _placement(Rect box) {
    if (box.width < 0.3) return FaceHint.tooFar;
    if (box.width > 1.0) return FaceHint.tooClose;
    final center = box.center;
    if ((center.dx - 0.5).abs() > 0.2 || (center.dy - 0.45).abs() > 0.2) return FaceHint.offCenter;
    return FaceHint.good;
  }

  void _restart() {
    _step = ChallengeStep.lookFront;
    _steady = 0;
    _frontYaws.clear();
    _eyesOpenSeen = false;
    _eyesClosedSeen = false;
    _blinked = false;
  }
}
