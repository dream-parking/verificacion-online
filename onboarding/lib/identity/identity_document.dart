import '../api/onboarding_api.dart';

/// Whether the reader could extract the DUI data (`IdentityDocumentResponse.status`).
enum ReadingStatus { read, failed }

/// What the API read from the DUI photos. A field that was not read clearly is `null` and the
/// confirmation screen (VDI-78) marks it for the person to check.
class IdentityReading {
  const IdentityReading({
    required this.status,
    this.dui,
    this.firstNames,
    this.lastNames,
    this.birthDate,
    this.issueDate,
    this.expiryDate,
    this.gender,
  });

  factory IdentityReading.fromJson(Map<String, dynamic> json) =>
      IdentityReading(
        status: json['status'] == 'FAILED'
            ? ReadingStatus.failed
            : ReadingStatus.read,
        dui: json['dui'] as String?,
        firstNames: json['firstNames'] as String?,
        lastNames: json['lastNames'] as String?,
        birthDate: _date(json['birthDate']),
        issueDate: _date(json['issueDate']),
        expiryDate: _date(json['expiryDate']),
        gender: json['gender'] as String?,
      );

  final ReadingStatus status;
  final String? dui;
  final String? firstNames;
  final String? lastNames;
  final DateTime? birthDate;
  final DateTime? issueDate;
  final DateTime? expiryDate;
  final String? gender;

  static DateTime? _date(Object? value) =>
      value is String ? DateTime.tryParse(value) : null;
}

/// Side of the DUI.
enum DuiSide { front, back }

/// Why the API could not read the photos (`reason` of a 422).
enum UnreadableReason {
  blurry,
  glare,
  cropped,
  tooDark,
  notADui,
  wrongSides,
  tooLarge,
  other,
}

/// Photos the person has to take again, as the API says (`side` of a 422).
enum Retake { front, back, both }

/// The photos the API rejected: what happened and which ones to take again.
class UnreadablePhotos {
  const UnreadablePhotos(this.reason, this.retake);

  /// Reads the 422 of the API. A reason or side we do not know about is treated as the general case:
  /// `OTHER` and both photos, so the person is never left without a way forward.
  factory UnreadablePhotos.fromError(ApiException e) => UnreadablePhotos(
    switch (e.reason) {
      'BLURRY' => UnreadableReason.blurry,
      'GLARE' => UnreadableReason.glare,
      'CROPPED' => UnreadableReason.cropped,
      'TOO_DARK' => UnreadableReason.tooDark,
      'NOT_A_DUI' => UnreadableReason.notADui,
      'WRONG_SIDES' => UnreadableReason.wrongSides,
      _ => UnreadableReason.other,
    },
    switch (e.side) {
      'FRONT' => Retake.front,
      'BACK' => Retake.back,
      _ => Retake.both,
    },
  );

  /// A photo over the 5 MB limit (413): the API did not read it, but the way out is the same.
  const UnreadablePhotos.tooLarge()
    : this(UnreadableReason.tooLarge, Retake.both);

  final UnreadableReason reason;
  final Retake retake;

  /// Photos to take again, in the order they are asked for.
  List<DuiSide> get sides => switch (retake) {
    Retake.front => [DuiSide.front],
    Retake.back => [DuiSide.back],
    Retake.both => [DuiSide.front, DuiSide.back],
  };

  /// Title of the screen: names what to check.
  String get title => switch (retake) {
    Retake.front => 'Revisa la foto del frente',
    Retake.back => 'Revisa la foto del reverso',
    Retake.both => 'Revisa las fotos de tu DUI',
  };

  /// What happened, in words the person understands and with the way out: take another photo.
  String get message => switch (reason) {
    UnreadableReason.blurry =>
      'La foto salió borrosa. Toma otra con el teléfono bien quieto.',
    UnreadableReason.glare =>
      'Hay un reflejo sobre tus datos. Toma otra foto para poder seguir.',
    UnreadableReason.cropped => 'No se ven las 4 esquinas de tu DUI. Toma otra foto con todo el documento dentro del marco.',
    UnreadableReason.tooDark =>
      'La foto salió muy oscura. Busca un lugar con más luz y toma otra.',
    UnreadableReason.notADui =>
      'No parece que sea un DUI. Toma otra foto de tu DUI.',
    UnreadableReason.wrongSides => 'Parece que el frente y el reverso están cambiados. Toma las dos fotos otra vez, empezando por el frente.',
    UnreadableReason.tooLarge =>
      'Una de las fotos pesa demasiado. Toma otra foto.',
    UnreadableReason.other =>
      'No pudimos leer tu DUI. Toma otra foto para poder seguir.',
  };

  /// Tips to take the photo better (the same for every reason).
  static const tips = [
    'Pon tu DUI sobre una mesa oscura.',
    'Aléjate de lámparas o ventanas que brillen sobre él.',
    'Que se vean las 4 esquinas dentro del marco.',
  ];
}
