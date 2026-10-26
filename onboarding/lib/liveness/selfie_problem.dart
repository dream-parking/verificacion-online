/// Why the selfie has to be taken again (VDI-116). It only names what the person can fix about how it was taken; it
/// never says the system suspects anything, so as not to teach what it looks for.
enum SelfieProblem {
  // `reason` of a 422 of `PUT /liveness`: the API could not judge the selfie because of how it was taken.
  blurry('La foto salió movida. Sostén el teléfono firme.'),
  tooDark('Había poca luz en tu rostro.'),
  glare('Había mucha luz detrás de ti o reflejos en tu rostro.'),
  faceNotVisible('No se veía tu rostro completo.'),
  multipleFaces('Aparecía más de una persona.'),
  other(null);

  const SelfieProblem(this.detail);

  /// What went wrong, or `null` to give only the tips.
  final String? detail;

  static SelfieProblem fromReason(String? reason) => switch (reason) {
    'BLURRY' => blurry,
    'TOO_DARK' => tooDark,
    'GLARE' => glare,
    'FACE_NOT_VISIBLE' => faceNotVisible,
    'MULTIPLE_FACES' => multipleFaces,
    _ => other,
  };

  /// «Intenta así», on the screen to try again.
  static const tips = [
    'Ponte frente a una ventana o una lámpara, no de espaldas a la luz.',
    'Deja tu rostro completo dentro del óvalo, sin lentes oscuros, gorra ni cubrebocas.',
    'Sostén el teléfono firme a la altura de tus ojos al tomar la foto.',
  ];
}
