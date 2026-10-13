import '../identity/identity_document.dart';

/// Flow screens, in order.
enum Screen {
  welcome,
  privacy,
  location,
  basicData,
  identityDocument,
  identityConfirmation,
  income,
  expectedActivity,
  review,
  confirmation,
}

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

/// Longest first or last names the API accepts (`BasicDataRequest`).
const maxNameLength = 100;

/// Letters (accents and ñ included) in words joined by one space, hyphen or apostrophe, each between two letters:
/// «Rivas-Cruz», «D'Aubuisson». VDI-88 makes the API accept the same.
final _namePattern = RegExp(r"^\p{L}+([ '\-]\p{L}+)*$", unicode: true);

/// Trims the name and leaves a single space between words, as the API expects it.
String normalizeName(String raw) => raw.trim().replaceAll(RegExp(r'\s+'), ' ');

/// Trims the name, leaves a single space between words and writes every typographic apostrophe
/// (the phone keyboards type ’) as the plain one the API expects.
String normalizePersonName(String raw) => normalizeName(raw.replaceAll(RegExp('[’‘ʼ]'), "'"));

/// Error message for first or last names ([what] is `nombres` or `apellidos`); [missing] is shown when there is
/// nothing to check yet.
String _nameMessage(String raw, String missing, String what) {
  final name = normalizePersonName(raw);
  if (name.length < 2) return missing;
  if (name.length > maxNameLength) return 'Usa como máximo $maxNameLength caracteres.';
  if (!_namePattern.hasMatch(name)) {
    return "Escribe tus $what solo con letras. También puedes usar espacios, apóstrofo (') o guion (-).";
  }
  return '';
}

/// Longest income detail the API accepts (`IncomeDeclarationRequest.sourceDetail`).
const maxIncomeDetailLength = 150;

/// Income detail: letters, numbers, spaces and basic punctuation; no emojis or other symbols.
final _detailPattern = RegExp(r'^[\p{L}\p{N} .,\-/()]+$', unicode: true);

String _incomeDetailMessage(String raw) {
  final detail = normalizeName(raw);
  if (detail.length < 3) return 'Cuéntanos de dónde vienen tus ingresos.';
  if (!_detailPattern.hasMatch(detail)) {
    return 'Usa solo letras, números y los signos . , - / ( ), sin emojis ni otros símbolos.';
  }
  return '';
}

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
  bool termsAccepted = false;
  String firstNames = '';
  String lastNames = '';
  String dui = '';
  String phone = '';

  /// What the API read from the DUI photos (VDI-77); the person confirms or corrects it (VDI-78).
  IdentityReading? identityReading;

  // Data of the DUI as the person confirms or corrects it (VDI-78), in the format of the screen.
  String idDui = '';
  String idFirstNames = '';
  String idLastNames = '';
  String idBirthDate = '';
  String idExpiryDate = '';

  /// Fields of the DUI the reader did not read clearly: the screen marks them to be reviewed.
  final idUnclear = <String>{};

  /// Fields of the DUI the person typed: a new reading (after repeating a photo) does not overwrite them.
  final _idEdited = <String>{};

  /// The reader was not available: the person types all the data and nothing is marked.
  bool readerFailed = false;

  static const idFields = ['idDui', 'idFirstNames', 'idLastNames', 'idBirthDate', 'idExpiryDate'];

  /// Puts what the reader found in the DUI fields. A value that is missing or does not pass the same checks the
  /// person's own input does is marked to be reviewed. What the person already corrected is kept.
  void applyReading(IdentityReading r) {
    readerFailed = r.status == ReadingStatus.failed;
    final read = {
      'idDui': r.dui,
      'idFirstNames': r.firstNames,
      'idLastNames': r.lastNames,
      'idBirthDate': r.birthDate == null ? null : formatDateOf(r.birthDate!),
      'idExpiryDate': r.expiryDate == null ? null : formatDateOf(r.expiryDate!),
    };
    for (final MapEntry(key: field, value: value) in read.entries) {
      if (!_idEdited.contains(field)) _setId(field, readerFailed ? '' : value ?? '');
    }
    idUnclear.clear();
    if (readerFailed) return;
    final m = messages();
    idUnclear.addAll([
      for (final field in idFields)
        if (!_idEdited.contains(field) && m[field]!.isNotEmpty) field,
    ]);
  }

  /// The person typed in [field]: it is no longer marked and later readings do not overwrite it.
  void markEdited(String field) {
    _idEdited.add(field);
    idUnclear.remove(field);
  }

  void _setId(String field, String value) {
    switch (field) {
      case 'idDui':
        idDui = value;
      case 'idFirstNames':
        idFirstNames = value;
      case 'idLastNames':
        idLastNames = value;
      case 'idBirthDate':
        idBirthDate = value;
      case 'idExpiryDate':
        idExpiryDate = value;
    }
  }
  String incomeSource = '';
  String incomeSourceDetail = '';
  String incomeRange = '';
  String transactionType = '';
  String amountRange = '';

  String get fullName => normalizePersonName('$firstNames $lastNames');

  /// Error message per field; empty string when the field is valid.
  Map<String, String> messages() {
    return {
      'accepted': _acceptanceMessage(),
      'firstNames': _nameMessage(firstNames, 'Escribe tus nombres.', 'nombres'),
      'lastNames': _nameMessage(lastNames, 'Escribe tus apellidos.', 'apellidos'),
      'dui': RegExp(r'^\d{8}-\d$').hasMatch(dui) ? '' : 'Escribe tu DUI con el formato 00000000-0.',
      // Mobile numbers in El Salvador start with 6 or 7 (those starting with 2 are landlines), same as the API.
      'phone': RegExp(r'^[67]\d{3}-\d{4}$').hasMatch(phone)
          ? ''
          : 'Escribe un celular de 8 dígitos que empiece con 6 o 7, por ejemplo 7845-2310.',
      'idDui': RegExp(r'^\d{8}-\d$').hasMatch(idDui) ? '' : 'Escribe tu DUI con el formato 00000000-0.',
      'idFirstNames': _nameMessage(idFirstNames, 'Escribe tus nombres.', 'nombres'),
      'idLastNames': _nameMessage(idLastNames, 'Escribe tus apellidos.', 'apellidos'),
      'idBirthDate': _birthDateMessage(),
      'idExpiryDate': _expiryDateMessage(),
      'incomeSource': incomeSource.isNotEmpty ? '' : 'Elige de dónde vienen tus ingresos.',
      'incomeSourceDetail': incomeSource == otherIncomeSource ? _incomeDetailMessage(incomeSourceDetail) : '',
      'incomeRange': incomeRange.isNotEmpty ? '' : 'Elige tu nivel de ingresos mensuales.',
      'transactionType': transactionType.isNotEmpty ? '' : 'Elige qué tipo de dinero manejarás.',
      'amountRange': amountRange.isNotEmpty ? '' : 'Elige cuánto dinero moverás al mes.',
    };
  }

  String _birthDateMessage() {
    final birth = parseDate(idBirthDate);
    if (birth == null) return 'Escribe tu fecha de nacimiento como día/mes/año, por ejemplo 14/03/1991.';
    final now = DateTime.now();
    return birth.isBefore(DateTime(now.year, now.month, now.day)) ? '' : 'La fecha de nacimiento debe ser anterior a hoy.';
  }

  String _expiryDateMessage() {
    final expiry = parseDate(idExpiryDate);
    if (expiry == null) return 'Escribe la fecha de vencimiento como día/mes/año, por ejemplo 22/07/2031.';
    final birth = parseDate(idBirthDate);
    return birth != null && !expiry.isAfter(birth) ? 'La fecha de vencimiento debe ser posterior a la de nacimiento.' : '';
  }

  /// One message for both checkboxes of the privacy screen, naming what is missing.
  String _acceptanceMessage() {
    final missing = [
      if (!accepted) 'el aviso de privacidad',
      if (!termsAccepted) 'los términos y condiciones',
    ];
    return missing.isEmpty ? '' : 'Para continuar necesitamos que aceptes ${missing.join(' y ')}.';
  }

  static List<String> fieldsOf(Screen s) => switch (s) {
        Screen.privacy => ['accepted'],
        Screen.basicData => ['firstNames', 'lastNames', 'dui', 'phone'],
        Screen.identityConfirmation => idFields,
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

/// Applies the dd/mm/aaaa mask, keeping digits only.
String formatDate(String raw) {
  final d = raw.replaceAll(RegExp(r'\D'), '');
  final s = d.length > 8 ? d.substring(0, 8) : d;
  final day = s.length > 2 ? '${s.substring(0, 2)}/' : s;
  if (s.length <= 2) return day;
  final month = s.length > 4 ? '${s.substring(2, 4)}/${s.substring(4)}' : s.substring(2);
  return '$day$month';
}

/// Reads a dd/mm/aaaa date; `null` if it is not complete or not a real date (31/02 is not rolled over to March).
DateTime? parseDate(String text) {
  final m = RegExp(r'^(\d{2})/(\d{2})/(\d{4})$').firstMatch(text);
  if (m == null) return null;
  final year = int.parse(m[3]!);
  final month = int.parse(m[2]!);
  final day = int.parse(m[1]!);
  final date = DateTime(year, month, day);
  return date.year == year && date.month == month && date.day == day ? date : null;
}

/// dd/mm/aaaa, as the screen shows it.
String formatDateOf(DateTime d) => '${_two(d.day)}/${_two(d.month)}/${d.year.toString().padLeft(4, '0')}';

/// aaaa-mm-dd, as the API expects it.
String dateForApi(DateTime d) => '${d.year.toString().padLeft(4, '0')}-${_two(d.month)}-${_two(d.day)}';

String _two(int n) => n.toString().padLeft(2, '0');
