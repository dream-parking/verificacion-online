import '../api/onboarding_api.dart';

/// VDI-125: Spanish messages for the HTTP errors of the API. They name what went wrong for the person, never the
/// server text. No widget code in here, so it is tested without the interface.
class ApiErrorMessages {
  const ApiErrorMessages._();

  static const connection = 'Revisa tu conexión a internet e inténtalo de nuevo.';
  static const alreadySubmitted = 'Tu solicitud ya fue enviada y no se puede modificar.';
  static const duiNotSaved =
      'No pudimos guardar tu DUI. Revisa que tus datos básicos estén completos e inténtalo de nuevo.';
  static const duiDataNotSaved =
      'No pudimos guardar los datos de tu DUI. Vuelve a tomar las fotos e inténtalo de nuevo.';

  static const signRejected = 'No pudimos registrar tu firma. Inténtalo de nuevo.';
  static const contractChanged = 'El contrato se actualizó. Léelo de nuevo antes de firmar.';

  /// Signing the contract: a 422 is the signature service rejecting it; anything else is taken as a connection problem
  /// (a 409 is handled by the screen, which checks the contract in force).
  static String forSign(ApiException e) => e.status == 422 ? signRejected : connection;

  /// Saving a step: a 409 means the request can no longer change (or [conflict] when the step has its own wording),
  /// a 400 names the rejected fields, anything else is taken as a connection problem.
  static String forSave(ApiException e, {String? conflict}) => switch (e.status) {
        409 => conflict ?? alreadySubmitted,
        400 => invalidData(e.fields),
        _ => connection,
      };

  static const productsNotAvailable =
      'Todavía no podemos mostrarte las cuentas. Si tu solicitud sigue en revisión, te avisaremos.';
  static const productsEmpty = 'Por ahora no tenemos cuentas para ofrecerte. Te avisaremos cuando haya una disponible.';

  /// Listing the accounts (VDI-147): a 409 or a 404 means the request cannot choose one now; anything else is taken as
  /// a connection problem.
  static String forProducts(ApiException e) => e.status == 409 || e.status == 404 ? productsNotAvailable : connection;

  /// Sending the DUI photos when the API did not complain about the photos themselves.
  static String forDuiUpload(ApiException e) => e.status == 409 ? duiNotSaved : connection;

  /// Sending the selfie when the API did not complain about the photo itself.
  static String forSelfieUpload(ApiException e) => e.status == 409 ? alreadySubmitted : connection;

  /// Message for a 400: names the rejected fields.
  static String invalidData(List<String> fields) {
    const labels = {
      'firstNames': 'nombres',
      'lastNames': 'apellidos',
      'dui': 'DUI',
      'mobilePhone': 'celular',
      'birthDate': 'fecha de nacimiento',
      'expiryDate': 'fecha de vencimiento',
      'sourceDetail': 'origen de tus ingresos',
    };
    final names = [
      for (final f in fields)
        if (labels[f] != null) labels[f]!,
    ];
    return names.isEmpty
        ? 'Revisa tus datos e inténtalo de nuevo.'
        : 'Revisa estos datos e inténtalo de nuevo: ${names.join(', ')}.';
  }
}
