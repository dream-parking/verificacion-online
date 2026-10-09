import '../onboarding/application_form.dart';

/// Options from `GET /api/catalogs` (VDI-46 and VDI-50). The codes are sent to the API as they are.
class Catalogs {
  const Catalogs({
    required this.incomeSources,
    required this.incomeRanges,
    required this.transactionTypes,
    required this.amountRanges,
  });

  factory Catalogs.fromJson(Map<String, dynamic> json) {
    List<Option> list(String key) => [
          for (final item in (json[key] as List? ?? const []).cast<Map<String, dynamic>>())
            Option(item['code'] as String, item['label'] as String),
        ];
    return Catalogs(
      incomeSources: list('incomeSources'),
      incomeRanges: list('incomeRanges'),
      transactionTypes: list('transactionTypes'),
      amountRanges: list('monthlyAmountRanges'),
    );
  }

  final List<Option> incomeSources;
  final List<Option> incomeRanges;
  final List<Option> transactionTypes;
  final List<Option> amountRanges;
}
