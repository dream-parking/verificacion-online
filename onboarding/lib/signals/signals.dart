import 'device_fingerprint.dart';
import 'interaction.dart';
import 'location.dart';

/// Signals captured during the request, in the format of `PUT /api/onboarding/requests/{id}/signals`.
///
/// The backend replaces the whole session on every request: a field that is not sent ends up empty.
/// That is why this whole object is always sent, with everything captured so far.
class Signals {
  DeviceInfo? device;

  /// VDI-41: null until the app tried to read it.
  ApproximateLocation? location;
  final typing = TypingRhythm();
  final steps = StepTimings();

  Map<String, Object?> toJson() {
    final d = device;
    final l = location;
    final cpm = typing.cpm;
    final stepTimings = steps.toJson();
    return {
      if (d != null) ...{
        'deviceFingerprint': d.fingerprint,
        'deviceModel': d.model,
        'operatingSystem': d.operatingSystem,
        'appVersion': d.appVersion,
      },
      ...?l?.toJson(),
      'typingSpeedCpm': ?cpm, // omitted while there are not enough keystrokes
      if (stepTimings.isNotEmpty) 'steps': stepTimings,
    };
  }
}
