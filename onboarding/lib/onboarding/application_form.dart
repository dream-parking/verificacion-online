/// Flow screens, in order.
enum Screen { welcome, privacy, basicData, income, expectedActivity, review, confirmation }

/// A catalog option (income source, income range, transaction type, amount range).
class Option {
  const Option(this.value, this.label, [this.description = '']);

  final String value;
  final String label;
  final String description;
}

/// Code of the "Other" income source: the API requires the detail (`sourceDetail`) when it is chosen.
const otherIncomeSource = 'OTRO';

/// Help text from the design for each catalog transaction type (VDI-50).
/// A new type in the catalog is still shown, just without a description.
const transactionTypeDescriptions = {
  'PAGO_SALARIO': 'Tu empleador te depositará aquí el sueldo.',
  'COBROS_NEGOCIO': 'Ventas y pagos que recibirás de tus clientes.',
  'REMESAS': 'Dinero que te envía tu familia desde el exterior.',
  'AHORRO': 'Dinero que irás guardando en la cuenta.',
};

String labelOf(List<Option> options, String value) {
  for (final o in options) {
    if (o.value == value) return o.label;
  }
  return '';
}

/// Data the customer fills in during onboarding.
class ApplicationForm {
  /// `id` of the request in the backend; created when the privacy notice is accepted.
  /// While it is `null`, no signal is captured.
  String? id;
  DateTime? privacyAcceptedAt;

  bool get captureAllowed => id != null;

  /// Number assigned by the server when the request is submitted (SOL-YYYY-NNNNN).
  String? number;

  bool accepted = false;
  String firstNames = '';
  String lastNames = '';
  String dui = '';
  String phone = '';
  String incomeSource = '';
  String incomeSourceDetail = '';
  String incomeRange = '';
  String transactionType = '';
  String amountRange = '';

  String get fullName => '$firstNames $lastNames'.trim();

  /// Error message per field; empty string when the field is valid.
  Map<String, String> messages() {
    return {
      'accepted': accepted ? '' : 'Para continuar necesitamos que aceptes el aviso de privacidad.',
      'firstNames': firstNames.trim().length < 2 ? 'Escribe tus nombres.' : '',
      'lastNames': lastNames.trim().length < 2 ? 'Escribe tus apellidos.' : '',
      'dui': RegExp(r'^\d{8}-\d$').hasMatch(dui) ? '' : 'Escribe tu DUI con el formato 00000000-0.',
      // Mobile numbers in El Salvador start with 6 or 7 (those starting with 2 are landlines), same as the API.
      'phone': RegExp(r'^[67]\d{3}-\d{4}$').hasMatch(phone)
          ? ''
          : 'Escribe un celular de 8 dígitos que empiece con 6 o 7, por ejemplo 7845-2310.',
      'incomeSource': incomeSource.isNotEmpty ? '' : 'Elige de dónde vienen tus ingresos.',
      'incomeSourceDetail': incomeSource == otherIncomeSource && incomeSourceDetail.trim().isEmpty
          ? 'Cuéntanos de dónde vienen tus ingresos.'
          : '',
      'incomeRange': incomeRange.isNotEmpty ? '' : 'Elige tu nivel de ingresos mensuales.',
      'transactionType': transactionType.isNotEmpty ? '' : 'Elige qué tipo de dinero manejarás.',
      'amountRange': amountRange.isNotEmpty ? '' : 'Elige cuánto dinero moverás al mes.',
    };
  }

  static List<String> fieldsOf(Screen s) => switch (s) {
        Screen.privacy => ['accepted'],
        Screen.basicData => ['firstNames', 'lastNames', 'dui', 'phone'],
        Screen.income => ['incomeSource', 'incomeSourceDetail', 'incomeRange'],
        Screen.expectedActivity => ['transactionType', 'amountRange'],
        _ => [],
      };

  bool isScreenValid(Screen s) {
    final m = messages();
    return fieldsOf(s).every((f) => m[f]!.isEmpty);
  }
}

/// Applies the 00000000-0 mask, keeping digits only.
String formatDui(String raw) {
  final d = raw.replaceAll(RegExp(r'\D'), '');
  final s = d.length > 9 ? d.substring(0, 9) : d;
  return s.length > 8 ? '${s.substring(0, 8)}-${s.substring(8)}' : s;
}

/// Applies the 0000-0000 mask, keeping digits only.
String formatPhone(String raw) {
  final d = raw.replaceAll(RegExp(r'\D'), '');
  final s = d.length > 8 ? d.substring(0, 8) : d;
  return s.length > 4 ? '${s.substring(0, 4)}-${s.substring(4)}' : s;
}
