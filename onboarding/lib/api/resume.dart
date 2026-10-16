import '../onboarding/application_form.dart';

/// Where a request was left and what was saved (`GET /{id}/resume`, VDI-86).
class ResumeInfo {
  const ResumeInfo({
    required this.resumable,
    this.step,
    this.firstName,
    this.firstNames,
    this.lastNames,
    this.dui,
    this.mobilePhone,
    this.identityConfirmed = false,
    this.incomeSource,
    this.incomeRange,
    this.incomeDetail,
    this.transactionType,
    this.amountRange,
  });

  /// `state` is `RESUMABLE`: it can go on. `EXPIRED`, `COMPLETED` and `ABANDONED` cannot.
  final bool resumable;

  /// Screen of the step the person was in; `null` when it cannot be resumed.
  final Screen? step;

  /// For the greeting.
  final String? firstName;

  // What was saved, to fill the screens again.
  final String? firstNames;
  final String? lastNames;
  final String? dui;
  final String? mobilePhone;

  /// The person confirmed the data of the DUI. The photos stay in the API: the phone does not keep them.
  final bool identityConfirmed;
  final String? incomeSource;
  final String? incomeRange;
  final String? incomeDetail;
  final String? transactionType;
  final String? amountRange;

  factory ResumeInfo.fromJson(Map<String, dynamic> json) {
    final saved = json['saved'] as Map<String, dynamic>? ?? const {};
    final basic = saved['basicData'] as Map<String, dynamic>? ?? const {};
    final identity = saved['identityDocument'] as Map<String, dynamic>? ?? const {};
    final income = saved['income'] as Map<String, dynamic>? ?? const {};
    final activity =
        saved['expectedActivity'] as Map<String, dynamic>? ?? const {};
    final resumable = json['state'] == 'RESUMABLE';
    return ResumeInfo(
      resumable: resumable,
      step: resumable ? screenOf(json['currentStep'] as String?) : null,
      firstName: json['firstName'] as String?,
      firstNames: basic['firstNames'] as String?,
      lastNames: basic['lastNames'] as String?,
      dui: basic['dui'] as String?,
      mobilePhone: basic['mobilePhone'] as String?,
      identityConfirmed: identity['confirmed'] == true,
      incomeSource: income['sourceCode'] as String?,
      incomeRange: income['rangeCode'] as String?,
      incomeDetail: income['sourceDetail'] as String?,
      transactionType: activity['transactionTypeCode'] as String?,
      amountRange: activity['monthlyAmountRangeCode'] as String?,
    );
  }

  /// Screen that asks for the API step. The DUI photos are not kept in the phone, so that step starts with the camera;
  /// the privacy notice was already accepted when the request exists, so it goes on with the basic data.
  static Screen? screenOf(String? step) => switch (step) {
    'PRIVACY_NOTICE' || 'BASIC_DATA' => Screen.basicData,
    'IDENTITY_DOCUMENT' => Screen.identityDocument,
    'INCOME' => Screen.income,
    'EXPECTED_ACTIVITY' => Screen.expectedActivity,
    'REVIEW' => Screen.review,
    _ => null,
  };
}
