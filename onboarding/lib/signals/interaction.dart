import '../onboarding/application_form.dart';

/// Interaction patterns while the customer fills in the form (VDI-43).

/// Flow steps with the name the API uses (`CaptureSignalsStepTiming.step`).
const apiStepNames = {
  Screen.privacy: 'PRIVACY_NOTICE',
  Screen.basicData: 'BASIC_DATA',
  Screen.income: 'INCOME',
  Screen.expectedActivity: 'EXPECTED_ACTIVITY',
  Screen.review: 'REVIEW',
};

/// Typing speed, measured only while the person is typing: in characters per minute for the whole
/// request ([cpm]) and in characters per second for one screen ([cps]).
///
/// - A pause longer than [pause] between keystrokes ends the "burst": thinking time does not count.
/// - A change of more than 2 characters at once (paste, autofill, demo data) is not typing and also
///   ends the burst. 2 are accepted because the masks add the dash while typing.
/// - Deleting does not add characters.
class TypingRhythm {
  static const pause = Duration(seconds: 2);

  /// With fewer keystrokes the value is not representative and is not sent.
  static const minKeystrokes = 5;

  final _lengths = <String, int>{};
  DateTime? _lastKeystroke;
  var _keystrokes = 0;
  var _typingTime = Duration.zero;

  void record(String field, String text, DateTime now) {
    final before = _lengths[field] ?? 0;
    _lengths[field] = text.length;
    final delta = text.length - before;
    if (delta <= 0) return;
    if (delta > 2) {
      _lastKeystroke = null;
      return;
    }
    final last = _lastKeystroke;
    if (last != null) {
      final interval = now.difference(last);
      if (interval <= pause) {
        _keystrokes++;
        _typingTime += interval;
      }
    }
    _lastKeystroke = now;
  }

  /// Characters per minute (0-2000, the API range) or `null` while there is not enough data.
  int? get cpm {
    if (_keystrokes < minKeystrokes || _typingTime <= Duration.zero) return null;
    final value = (_keystrokes * 60000 / _typingTime.inMilliseconds).round();
    return value.clamp(0, 2000);
  }

  /// Characters per second with 2 decimals (0-50, the API range of `steps[].typingSpeedCps`) or
  /// `null` while there is not enough data.
  double? get cps {
    if (_keystrokes < minKeystrokes || _typingTime <= Duration.zero) return null;
    final value = _keystrokes * 1000 / _typingTime.inMilliseconds;
    return (value.clamp(0, 50) * 100).round() / 100;
  }
}

/// Start, end and attempts of each form step.
class StepTimings {
  final _steps = <Screen, _StepTiming>{};

  /// The first time the step is shown. Coming back to it (e.g. to edit) keeps the start.
  void start(Screen s, DateTime now) {
    if (!apiStepNames.containsKey(s)) return;
    _steps.putIfAbsent(s, () => _StepTiming(now));
  }

  /// Every time the person taps "Continuar" on the step, whether it advances or not.
  void attempt(Screen s) => _steps[s]?.attempts++;

  /// The step was completed; if it is completed again after editing it, the last time is kept.
  void complete(Screen s, DateTime now) => _steps[s]?.completedAt = now;

  /// [typingSpeeds]: characters per second typed on each screen; screens without typing omit it.
  List<Map<String, Object?>> toJson({Map<Screen, double> typingSpeeds = const {}}) => [
        for (final MapEntry(key: s, value: t) in _steps.entries)
          {
            'step': apiStepNames[s],
            'startedAt': t.startedAt.toUtc().toIso8601String(),
            if (t.completedAt != null) 'completedAt': t.completedAt!.toUtc().toIso8601String(),
            'attempts': t.attempts < 1 ? 1 : t.attempts,
            'typingSpeedCps': ?typingSpeeds[s],
          },
      ];
}

class _StepTiming {
  _StepTiming(this.startedAt);

  final DateTime startedAt;
  DateTime? completedAt;
  var attempts = 0;
}
