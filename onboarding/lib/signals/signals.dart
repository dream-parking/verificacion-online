import 'device_fingerprint.dart';
import 'interaction.dart';

/// Signals captured during the request, in the format of `PUT /api/onboarding/requests/{id}/signals`.
///
/// The backend replaces the whole session on every request: a field that is not sent ends up empty.
/// That is why this whole object is always sent, with everything captured so far.
class Signals {
  DeviceInfo? device;
  final typing = TypingRhythm();
  final steps = StepTimings();

  Map<String, Object?> toJson() {
    final d = device;
    final cpm = typing.cpm;
    final stepTimings = steps.toJson();
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
