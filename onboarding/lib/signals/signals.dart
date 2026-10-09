import '../onboarding/application_form.dart';
import 'device_fingerprint.dart';
import 'interaction.dart';

/// Signals captured during the request, in the format of `PUT /api/onboarding/requests/{id}/signals`.
///
/// The backend replaces the whole session on every request: a field that is not sent ends up empty.
/// That is why this whole object is always sent, with everything captured so far.
class Signals {
  DeviceInfo? device;

  /// Typing of the whole request (`typingSpeedCpm`).
  final typing = TypingRhythm();

  /// Typing of each screen (`steps[].typingSpeedCps`, VDI-68).
  final _typingByScreen = <Screen, TypingRhythm>{};
  final steps = StepTimings();

  /// Records a change in a text field of [screen], for the whole request and for that screen.
  void recordTyping(Screen screen, String field, String text, DateTime now) {
    typing.record(field, text, now);
    _typingByScreen.putIfAbsent(screen, TypingRhythm.new).record(field, text, now);
  }

  Map<String, Object?> toJson() {
    final d = device;
    final cpm = typing.cpm;
    final stepTimings = steps.toJson(typingSpeeds: {
      for (final MapEntry(key: screen, value: rhythm) in _typingByScreen.entries)
        screen: ?rhythm.cps,
    });
    return {
      if (d != null) ...{
        'deviceFingerprint': d.fingerprint,
        'deviceModel': d.model,
        'operatingSystem': d.operatingSystem,
        'appVersion': d.appVersion,
      },
      'typingSpeedCpm': ?cpm, // omitted while there are not enough keystrokes
      if (stepTimings.isNotEmpty) 'steps': stepTimings,
    };
  }
}
