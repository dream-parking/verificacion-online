import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/catalogs.dart';
import '../api/onboarding_api.dart';
import '../api/resume.dart';
import '../identity/document_camera.dart';
import '../identity/dui_capture.dart';
import '../identity/identity_document.dart';
import '../signals/device_fingerprint.dart';
import '../signals/device_location.dart';
import '../signals/signals.dart';
import '../theme.dart';
import 'application_form.dart';
import 'privacy_notice.dart';
import 'saved_request.dart';
import 'widgets.dart';

/// Account opening flow: welcome → privacy → location permission → 5 steps → confirmation.
class OnboardingFlow extends StatefulWidget {
  const OnboardingFlow({
    super.key,
    this.api,
    this.deviceInfo,
    this.camera = const PlatformDocumentCamera(),
    this.location,
    this.savedRequest = const PreferencesSavedRequestStore(),
    this.clock = DateTime.now,
  });

  /// API client; tests pass a fake one.
  final OnboardingApi? api;

  /// Device reader (VDI-40); tests pass a fake one.
  final DeviceInfoSource? deviceInfo;

  /// Camera that photographs the DUI (VDI-77); tests pass a fake one.
  final DocumentCamera camera;

  /// Approximate location reader (VDI-73); tests pass a fake one.
  final DeviceLocationSource? location;

  /// Where the id of the request in progress is kept (VDI-87); tests pass one in memory.
  final SavedRequestStore savedRequest;

  /// Current time used to measure timings (VDI-43); tests pass a controlled one.
  final DateTime Function() clock;

  @override
  State<OnboardingFlow> createState() => _OnboardingFlowState();
}

class _OnboardingFlowState extends State<OnboardingFlow> {
  static const _next = {
    Screen.welcome: Screen.privacy,
    Screen.privacy: Screen.location,
    Screen.location: Screen.basicData,
    Screen.basicData: Screen.identityDocument,
    Screen.identityDocument: Screen.identityConfirmation,
    Screen.identityConfirmation: Screen.income,
    Screen.income: Screen.expectedActivity,
    Screen.expectedActivity: Screen.review,
  };
  static const _previous = {
    Screen.privacy: Screen.welcome,
    Screen.location: Screen.privacy,
    Screen.basicData: Screen.location,
    Screen.identityDocument: Screen.basicData,
    Screen.identityConfirmation: Screen.identityDocument,
    Screen.income: Screen.identityConfirmation,
    Screen.expectedActivity: Screen.income,
    Screen.review: Screen.expectedActivity,
  };
  static const _stepNumber = {
    Screen.basicData: 1,
    Screen.identityDocument: 2,
    Screen.identityConfirmation: 2,
    Screen.income: 3,
    Screen.expectedActivity: 4,
    Screen.review: 5,
  };

  final _scroll = ScrollController();
  final _firstNames = TextEditingController();
  final _lastNames = TextEditingController();
  final _dui = TextEditingController();
  final _phone = TextEditingController();
  final _incomeSourceDetail = TextEditingController();

  // DUI data the person confirms or corrects (VDI-78).
  final _idDui = TextEditingController();
  final _idFirstNames = TextEditingController();
  final _idLastNames = TextEditingController();
  final _idBirthDate = TextEditingController();
  final _idExpiryDate = TextEditingController();

  late final OnboardingApi _api = widget.api ?? OnboardingApi();
  late final DeviceInfoSource _deviceInfo = widget.deviceInfo ?? PlatformDeviceInfoSource();
  late final DeviceLocationSource _location = widget.location ?? PlatformDeviceLocationSource();
  var _form = ApplicationForm();

  /// Request created in the backend whose privacy notice could not be recorded yet: the retry reuses it.
  String? _createdRequestId;

  /// Options from the API (VDI-48). They are not personal data: they are requested when the app opens.
  Catalogs? _catalogs;
  var _catalogsFailed = false;
  var _signals = Signals();
  var _signalsPending = false;
  Future<void>? _signalsInFlight;
  var _resendSignals = false;
  var _screen = Screen.welcome;
  final _attempted = <Screen>{};
  var _submitting = false;
  var _starting = false;
  var _locating = false;
  String? _startError;
  var _saving = false;
  String? _saveError;
  var _submitFailed = false;
  var _copied = false;

  // DUI step (VDI-77).
  /// Photos taken so far; the API gets both together.
  final _duiPhotos = <DuiSide, Uint8List>{};

  /// Sides the camera asks for now: both at first, or only those the API asked to take again.
  var _duiToCapture = const [DuiSide.front, DuiSide.back];
  UnreadablePhotos? _unreadable;
  String? _duiError;
  var _uploadingDui = false;

  /// A request left halfway that the person can pick up (VDI-87). `null` when there is none.
  ResumeInfo? _resumable;
  String? _resumableId;

  /// The request was picked up with the DUI confirmed: its screens are empty in the phone, so back skips them.
  var _duiConfirmedBeforeResume = false;

  /// The data of the DUI was confirmed and saved in the API; the review says so (VDI-87).
  var _duiConfirmed = false;

  /// Differences between the basic data and the DUI that the person already accepted («Continuar de todos modos»).
  String? _acceptedDifference;

  /// The person came back to correct the basic data after the warning: when saved, it returns to the confirmation.
  var _correctingBasicData = false;

  /// The person came from the confirmation to repeat a photo: the other one and the data are kept.
  var _retakingDui = false;

  @override
  void dispose() {
    for (final c in [_scroll, _firstNames, _lastNames, _dui, _phone, _incomeSourceDetail, ..._idControllers]) {
      c.dispose();
    }
    super.dispose();
  }

  List<TextEditingController> get _idControllers => [_idDui, _idFirstNames, _idLastNames, _idBirthDate, _idExpiryDate];

  /// Shows in the DUI fields what the form holds.
  void _syncIdControllers() {
    _idDui.text = _form.idDui;
    _idFirstNames.text = _form.idFirstNames;
    _idLastNames.text = _form.idLastNames;
    _idBirthDate.text = _form.idBirthDate;
    _idExpiryDate.text = _form.idExpiryDate;
  }

  @override
  void initState() {
    super.initState();
    _loadCatalogs();
    unawaited(_checkSavedRequest());
  }

  Future<void> _loadCatalogs() async {
    if (_catalogsFailed) setState(() => _catalogsFailed = false);
    try {
      final catalogs = await _api.catalogs();
      if (mounted) setState(() => _catalogs = catalogs);
    } on ApiException catch (e) {
      debugPrint('Could not load the catalogs: $e');
      if (mounted) setState(() => _catalogsFailed = true);
    }
  }

  /// VDI-87: when the app opens, asks the API about the request kept in the phone. It is offered only if it can go on;
  /// if it expired, was submitted, abandoned or does not exist, it is forgotten. Without connection it is kept for next time.
  Future<void> _checkSavedRequest() async {
    final String? id;
    try {
      id = await widget.savedRequest.read();
    } on Exception catch (e) {
      debugPrint('Could not read the saved request: $e');
      return;
    }
    if (id == null) return;
    try {
      final info = await _api.resume(id);
      if (!mounted) return;
      if (info.resumable && info.step != null) {
        setState(() {
          _resumable = info;
          _resumableId = id;
        });
      } else {
        await _forgetSavedRequest();
      }
    } on ApiException catch (e) {
      debugPrint('Could not check the saved request: $e');
      if (e.status == 404) await _forgetSavedRequest();
    }
  }

  Future<void> _forgetSavedRequest() async {
    try {
      await widget.savedRequest.clear();
    } on Exception catch (e) {
      debugPrint('Could not forget the saved request: $e');
    }
  }

  Future<void> _keepRequest(String id) async {
    try {
      await widget.savedRequest.save(id);
    } on Exception catch (e) {
      debugPrint('Could not keep the request: $e');
    }
  }

  /// VDI-87: «Continuar mi solicitud». The screens are filled with what the API saved and it goes to the step where it
  /// was. The privacy notice was already accepted. The device is not read again: new signals would replace the ones
  /// already saved.
  void _resumeRequest() {
    final info = _resumable!;
    _firstNames.text = info.firstNames ?? '';
    _lastNames.text = info.lastNames ?? '';
    _dui.text = info.dui ?? '';
    _phone.text = info.mobilePhone ?? '';
    _incomeSourceDetail.text = info.incomeDetail ?? '';
    setState(() {
      _form
        ..id = _resumableId
        ..privacyAcceptedAt = widget.clock()
        ..accepted = true
        ..termsAccepted = true
        ..firstNames = info.firstNames ?? ''
        ..lastNames = info.lastNames ?? ''
        ..dui = info.dui ?? ''
        ..phone = info.mobilePhone ?? ''
        ..incomeSource = info.incomeSource ?? ''
        ..incomeSourceDetail = info.incomeDetail ?? ''
        ..incomeRange = info.incomeRange ?? ''
        ..transactionType = info.transactionType ?? ''
        ..amountRange = info.amountRange ?? '';
      _createdRequestId = _resumableId;
      _resumable = null;
    });
    // The DUI photos are not kept in the phone. If the step was not confirmed it is done again, even if the API moved on.
    final afterDui = const [Screen.income, Screen.expectedActivity, Screen.review].contains(info.step);
    final askAgain = info.step == Screen.identityDocument || (afterDui && !info.identityConfirmed);
    _duiConfirmedBeforeResume = afterDui && info.identityConfirmed;
    _duiConfirmed = info.identityConfirmed;
    _goTo(askAgain ? Screen.identityDocument : info.step!);
    if (askAgain) unawaited(_explainPhotosAgain());
  }

  Future<void> _explainPhotosAgain() => showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          backgroundColor: Colors.white,
          title: const Text('Vuelve a tomar las fotos de tu DUI'),
          content: const Text(
              'Por tu seguridad, las fotos de tu DUI no se guardan en el teléfono. Tómalas otra vez para continuar con tu solicitud.'),
          actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Entendido'))],
        ),
      );

  /// VDI-87: «Empezar de nuevo». The request is abandoned so a new one can be opened; if the API cannot do it (it was
  /// already closed, or there is no connection) the person starts over anyway.
  Future<void> _startOver() async {
    final id = _resumableId!;
    setState(() => _resumable = null);
    await _forgetSavedRequest();
    try {
      await _api.abandon(id);
    } on ApiException catch (e) {
      debugPrint('Could not abandon the request: $e');
    }
  }

  void _goTo(Screen s, {bool keepDui = false}) {
    FocusScope.of(context).unfocus();
    setState(() {
      _screen = s;
      if (s != Screen.identityDocument) _retakingDui = false;
      if (s != Screen.basicData && s != Screen.identityConfirmation) _correctingBasicData = false;
      _submitFailed = false;
      _startError = null;
      _saveError = null;
    });
    if (_scroll.hasClients) _scroll.jumpTo(0);
    // VDI-43: the step start stays in memory; it is only sent after the notice is accepted.
    _signals.steps.start(s, widget.clock());
    if (s == Screen.identityDocument && !keepDui) _resetDuiCapture();
    if (_signalsPending) unawaited(_sendSignals());
  }

  /// VDI-43: marks the step as completed and sends the signals.
  void _completeStep(Screen s) {
    _signals.steps.complete(s, widget.clock());
    unawaited(_sendSignals());
  }

  /// VDI-43: typing speed in the text fields (only after the notice is accepted).
  void _onTyped(String field, String text) {
    if (_form.captureAllowed) _signals.recordTyping(_signalStep, field, text, widget.clock());
  }

  /// The DUI capture and its confirmation are one step for the API and the signals.
  Screen get _signalStep => _signalStepOf(_screen);

  static Screen _signalStepOf(Screen s) => s == Screen.identityConfirmation ? Screen.identityDocument : s;

  void _continue() {
    _signals.steps.attempt(_signalStep);
    if (_screen == Screen.review) {
      _submit();
      return;
    }
    if (!_form.isScreenValid(_screen)) {
      setState(() => _attempted.add(_screen));
      return;
    }
    if (_screen == Screen.privacy && !_form.captureAllowed) {
      _startRequest();
      return;
    }
    if (_screen == Screen.basicData) {
      _saveAndContinue(_sendBasicData);
      return;
    }
    if (_screen == Screen.income) {
      _saveAndContinue(_declareIncome);
      return;
    }
    if (_screen == Screen.expectedActivity) {
      _saveAndContinue(_declareExpectedActivity);
      return;
    }
    if (_screen == Screen.identityConfirmation) {
      // The data is valid; if it is another person than the one who filled the form, the person is told first.
      final differences = _form.identityMismatches();
      if (differences.isNotEmpty && _acceptedDifference != _form.identityDifferenceSignature) {
        _warnIdentityMismatch(differences);
        return;
      }
      _saveAndContinue(_confirmIdentity, conflictMessage: 'No pudimos guardar los datos de tu DUI. Vuelve a tomar las fotos e inténtalo de nuevo.');
      return;
    }
    _completeStep(_screen);
    _goTo(_next[_screen]!);
  }

  /// Saves first names, last names, DUI and mobile number in the API.
  Future<void> _sendBasicData() => _api.sendBasicData(
        _form.id!,
        firstNames: normalizePersonName(_form.firstNames),
        lastNames: normalizePersonName(_form.lastNames),
        dui: _form.dui,
        mobilePhone: _form.phone,
      );

  /// VDI-78: confirms the DUI data. The dates were already checked, so they always parse.
  Future<void> _confirmIdentity() => _api.confirmIdentityDocument(
        _form.id!,
        dui: _form.idDui,
        firstNames: normalizePersonName(_form.idFirstNames),
        lastNames: normalizePersonName(_form.idLastNames),
        birthDate: parseDate(_form.idBirthDate)!,
        expiryDate: parseDate(_form.idExpiryDate)!,
      );

  /// VDI-47: saves the income source and range in the API.
  Future<void> _declareIncome() => _api.declareIncome(
        _form.id!,
        source: _form.incomeSource,
        range: _form.incomeRange,
        detail: _form.incomeSource == otherIncomeSource ? normalizeName(_form.incomeSourceDetail) : null,
      );

  /// VDI-51: saves the transaction type and the monthly amount range in the API.
  Future<void> _declareExpectedActivity() => _api.declareExpectedActivity(
        _form.id!,
        transactionType: _form.transactionType,
        amountRange: _form.amountRange,
      );

  /// Starts the DUI capture from scratch: both photos, nothing pending.
  void _resetDuiCapture() {
    setState(() {
      _duiPhotos.clear();
      _duiToCapture = const [DuiSide.front, DuiSide.back];
      _unreadable = null;
      _duiError = null;
      _retakingDui = false;
    });
  }

  /// The basic data and the DUI do not say the same: it warns and lets the person correct the basic data or go on.
  Future<void> _warnIdentityMismatch(List<IdentityMismatch> differences) async {
    final choice = await showModalBottomSheet<_MismatchChoice>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: Colors.white,
      builder: (context) => _MismatchWarning(differences: differences),
    );
    if (!mounted || choice == null) return;
    switch (choice) {
      case _MismatchChoice.correct:
        _correctingBasicData = true;
        _goTo(Screen.basicData);
      case _MismatchChoice.goOn:
        _acceptedDifference = _form.identityDifferenceSignature;
        _continue();
    }
  }

  /// VDI-78: «Repetir» on a photo of the confirmation: the camera asks for that side only.
  void _repeatPhoto(DuiSide side) {
    _goTo(Screen.identityDocument, keepDui: true);
    setState(() {
      _duiToCapture = [side];
      _retakingDui = true;
    });
  }

  /// VDI-77: the camera took the photos of [sides]; they replace those of the same side and both go to the API.
  void _onDuiCaptured(Map<DuiSide, Uint8List> sides) {
    _duiPhotos.addAll(sides);
    _uploadDui();
  }

  /// VDI-77: sends both photos. If they can be read, it moves on to confirm the data; if not, it says what to fix.
  Future<void> _uploadDui() async {
    setState(() {
      _duiConfirmed = false;
      _uploadingDui = true;
      _duiError = null;
      _unreadable = null;
    });
    try {
      final reading = await _api.uploadIdentityDocument(
        _form.id!,
        front: _duiPhotos[DuiSide.front]!,
        back: _duiPhotos[DuiSide.back]!,
      );
      if (!mounted) return;
      setState(() {
        _uploadingDui = false;
        _form.identityReading = reading;
        _form.applyReading(reading);
        _syncIdControllers();
        _retakingDui = false;
      });
      _goTo(Screen.identityConfirmation);
    } on ApiException catch (e) {
      debugPrint('Could not send the DUI photos: $e');
      if (!mounted) return;
      setState(() {
        _uploadingDui = false;
        switch (e.status) {
          case 422:
            _unreadable = UnreadablePhotos.fromError(e);
          case 413:
            _unreadable = const UnreadablePhotos.tooLarge();
          case 400:
            _unreadable = const UnreadablePhotos(UnreadableReason.other, Retake.both);
          case 409:
            _duiError = 'No pudimos guardar tu DUI. Revisa que tus datos básicos estén completos e inténtalo de nuevo.';
          default:
            _duiError = 'Revisa tu conexión a internet e inténtalo de nuevo.';
        }
      });
    }
  }

  /// The person takes again the photos the API asked for.
  void _retakeDui() {
    final unreadable = _unreadable!;
    setState(() {
      _duiToCapture = unreadable.sides;
      _unreadable = null;
    });
  }

  /// Saves the current step in the API and only moves on if it was saved.
  Future<void> _saveAndContinue(Future<void> Function() save, {String? conflictMessage}) async {
    final returnToConfirmation = _correctingBasicData && _screen == Screen.basicData;
    final step = _screen;
    setState(() {
      _saving = true;
      _saveError = null;
    });
    try {
      await save();
      if (!mounted) return;
      setState(() => _saving = false);
      if (step == Screen.identityConfirmation) _duiConfirmed = true;
      _completeStep(_signalStepOf(step));
      _correctingBasicData = false;
      _goTo(returnToConfirmation ? Screen.identityConfirmation : _next[step]!);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _saveError = switch (e.status) {
          409 => conflictMessage ?? 'Tu solicitud ya fue enviada y no se puede modificar.',
          400 => _invalidDataMessage(e.fields),
          _ => 'Revisa tu conexión a internet e inténtalo de nuevo.',
        };
      });
    }
  }

  /// Message in Spanish for a 400: names the rejected fields, never the server text.
  static String _invalidDataMessage(List<String> fields) {
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

  bool get _busy => _submitting || _starting || _saving || _uploadingDui || _locating;

  /// VDI-40: reads the device and sends its fingerprint. It only runs after the notice is accepted.
  /// If it fails it does not interrupt the customer: signals feed the score, they do not block the flow.
  Future<void> _captureDevice() async {
    if (!_form.captureAllowed) return;
    try {
      _signals.device = await _deviceInfo.read();
    } on Exception catch (e) {
      debugPrint('Could not read the device: $e');
      return;
    }
    await _sendSignals();
  }

  /// VDI-73: "Permitir ubicación aproximada" asks the system for the permission and looks up the city.
  Future<void> _allowLocation() async {
    if (_busy) return;
    setState(() => _locating = true);
    final location = await _location.read();
    if (!mounted) return;
    setState(() => _locating = false);
    _decideLocation(location);
  }

  /// VDI-73: with or without permission the request goes on; without it the location is sent as not
  /// available (`DENIED`), never left out.
  void _decideLocation(DeviceLocation location) {
    if (_busy) return;
    _signals.location = location;
    // Sent before moving on, so the screen change does not send the same signals twice.
    unawaited(_sendSignals());
    _goTo(Screen.basicData);
  }

  /// Sends all captured signals; if it fails, it is retried on the next screen change.
  ///
  /// Requests go one at a time: since the backend replaces everything on each request, an old one
  /// arriving late would erase newer data. If another one is requested while one is in flight, it
  /// is sent once more when that one finishes, with the latest data.
  Future<void> _sendSignals() {
    if (_signalsInFlight != null) {
      _resendSignals = true;
      return _signalsInFlight!;
    }
    return _signalsInFlight = _sendSignalsInOrder().whenComplete(() => _signalsInFlight = null);
  }

  Future<void> _sendSignalsInOrder() async {
    do {
      _resendSignals = false;
      final id = _form.id;
      final signals = _signals;
      if (id == null || signals.device == null) return;
      _signalsPending = false;
      try {
        await _api.sendSignals(id, signals);
      } on ApiException catch (e) {
        debugPrint('Could not send the signals: $e');
        _signalsPending = true;
        return;
      }
    } while (_resendSignals);
  }

  /// Creates the request in the backend when the notice is accepted and records the acceptance. Nothing
  /// is captured before this. If the request was created but recording the notice failed, the retry
  /// reuses the same request.
  Future<void> _startRequest() async {
    setState(() {
      _starting = true;
      _startError = null;
    });
    try {
      final id = _createdRequestId ??= await _api.startRequest();
      await _api.acceptPrivacyNotice(id);
      if (!mounted) return;
      setState(() {
        _form
          ..id = id
          ..privacyAcceptedAt = widget.clock();
        _starting = false;
      });
      unawaited(_keepRequest(id));
      _signals.steps.complete(Screen.privacy, _form.privacyAcceptedAt!);
      _goTo(Screen.location);
      unawaited(_captureDevice());
    } on ApiException {
      if (!mounted) return;
      setState(() {
        _starting = false;
        _startError = 'Revisa tu conexión a internet e inténtalo de nuevo.';
      });
    }
  }

  /// Submits the request; the server assigns its unique number (SOL-YYYY-NNNNN).
  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _submitFailed = false;
    });
    // VDI-43: signals go before submitting; a submitted request no longer accepts them.
    _signals.steps.complete(Screen.review, widget.clock());
    await _sendSignals();
    try {
      final number = await _api.submitRequest(_form.id!);
      if (!mounted) return;
      setState(() {
        _form.number = number;
        _submitting = false;
      });
      unawaited(_forgetSavedRequest());
      _goTo(Screen.confirmation);
    } on ApiException catch (e) {
      debugPrint('Could not submit the request: $e');
      if (!mounted) return;
      setState(() {
        _submitting = false;
        _submitFailed = true;
      });
    }
  }

  void _restart() {
    for (final c in [_firstNames, _lastNames, _dui, _phone, _incomeSourceDetail, ..._idControllers]) {
      c.clear();
    }
    setState(() {
      _form = ApplicationForm();
      _duiConfirmed = false;
      _duiConfirmedBeforeResume = false;
      _createdRequestId = null;
      _duiPhotos.clear();
      _unreadable = null;
      _duiError = null;
      _uploadingDui = false;
      _retakingDui = false;
      _signals = Signals();
      _signalsPending = false;
      _resendSignals = false;
      _attempted.clear();
      _startError = null;
      _saveError = null;
      _copied = false;
    });
    _goTo(Screen.welcome);
  }

  /// Visible error of a field: only after trying to continue on that screen.
  String _errorFor(String field) {
    if (!_attempted.contains(_screen) || !ApplicationForm.fieldsOf(_screen).contains(field)) return '';
    return _form.messages()[field]!;
  }

  @override
  Widget build(BuildContext context) {
    final previous = _previousScreen;
    final step = _stepNumber[_screen];
    final capturing = _capturingDui;

    // On Android the system "back" button goes to the previous step instead of closing the app.
    return PopScope(
      canPop: previous == null && !_busy,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && previous != null && !_busy) _goTo(previous);
      },
      // The camera screen is dark and takes the whole screen: the status bar blends in with light icons.
      child: AnnotatedRegion<SystemUiOverlayStyle>(
        value: SystemUiOverlayStyle(
          statusBarIconBrightness: capturing ? Brightness.light : Brightness.dark,
          statusBarBrightness: capturing ? Brightness.dark : Brightness.light,
        ),
        child: Scaffold(
        backgroundColor: capturing ? DuiCaptureView.background : null,
        body: SafeArea(
          bottom: false,
          child: Stack(
            children: [
              Column(
                children: [
                  // On the welcome screen the brand is shown large (full logo), not in the header.
                  if (_screen != Screen.welcome && !capturing) _header(previous),
                  if (step != null && !capturing) StepProgress(step: step),
                  Expanded(
                    // The camera takes all the space; the other screens scroll.
                    child: _capturingDui
                        ? _duiCapture()
                        : SingleChildScrollView(
                            controller: _scroll,
                            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                            child: AnimatedSwitcher(
                              duration: const Duration(milliseconds: 200),
                              child: KeyedSubtree(key: ValueKey(_screen), child: _content()),
                            ),
                          ),
                  ),
                  // The DUI step has its own buttons: the shutter and "Tomar otra foto".
                  if (_screen != Screen.confirmation && _screen != Screen.identityDocument) _actionBar(),
                ],
              ),
              if (_submitting)
                const LoadingOverlay(title: 'Enviando tu solicitud…', detail: 'No cierres la aplicación.'),
              if (_saving)
                const LoadingOverlay(title: 'Guardando tu información…', detail: 'Un momento, por favor.'),
              if (_uploadingDui)
                const LoadingOverlay(title: 'Revisando tu DUI…', detail: 'Estamos leyendo tus fotos. Puede tardar unos segundos.'),
              if (_starting)
                const LoadingOverlay(title: 'Preparando tu solicitud…', detail: 'Esto puede tardar unos segundos.'),
              if (_locating)
                const LoadingOverlay(title: 'Buscando tu ciudad…', detail: 'Esto puede tardar unos segundos.'),
            ],
          ),
        ),
      ),
      ),
    );
  }

  /// Back goes to the previous step; repeating a photo goes back to the confirmation it came from.
  Screen? get _previousScreen {
    if (_screen == Screen.identityDocument && _retakingDui) return Screen.identityConfirmation;
    if (_screen == Screen.income && _duiConfirmedBeforeResume) return Screen.basicData;
    return _previous[_screen];
  }

  Widget _header(Screen? previous) {
    return SizedBox(
      height: 60,
      width: double.infinity,
      child: Stack(
        alignment: Alignment.center,
        children: [
          const Logo(),
          if (previous != null && !_busy)
            Positioned(
              left: 8,
              child: IconButton(
                tooltip: 'Volver al paso anterior',
                onPressed: () => _goTo(previous),
                icon: const Icon(Icons.arrow_back_ios_new, size: 22, color: AppColors.ink),
              ),
            ),
        ],
      ),
    );
  }

  Widget _actionBar() {
    final text = switch (_screen) {
      Screen.welcome => 'Empezar',
      Screen.review => 'Enviar solicitud',
      Screen.privacy => PrivacyNotice.acceptButton,
      Screen.identityConfirmation => 'Mis datos son correctos',
      _ => 'Continuar',
    };
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      padding: EdgeInsets.fromLTRB(24, 12, 24, 20 + MediaQuery.paddingOf(context).bottom),
      child: _screen == Screen.welcome && _resumable != null
          ? Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                PrimaryButton(text: 'Continuar mi solicitud', onPressed: _resumeRequest),
                const SizedBox(height: 12),
                SecondaryButton(text: 'Empezar de nuevo', onPressed: _startOver),
              ],
            )
          : _screen == Screen.location
          ? Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                PrimaryButton(text: 'Permitir ubicación aproximada', onPressed: _busy ? null : _allowLocation),
                const SizedBox(height: 12),
                SecondaryButton(
                  text: 'Ahora no',
                  onPressed: _busy ? null : () => _decideLocation(const DeviceLocation.denied()),
                ),
              ],
            )
          : Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_screen == Screen.basicData) ...const [_SavedNote(), SizedBox(height: 10)],
                PrimaryButton(text: text, onPressed: _busy ? null : _continue),
                if (_screen == Screen.privacy) ...const [
                  SizedBox(height: 10),
                  Text(PrivacyNotice.recordedNote, style: AppText.small, textAlign: TextAlign.center),
                ],
              ],
            ),
    );
  }

  Widget _content() => switch (_screen) {
        Screen.welcome => _Welcome(resumable: _resumable),
        Screen.privacy => _privacy(),
        Screen.location => const _LocationPermission(),
        Screen.basicData => _basicData(),
        Screen.identityDocument => _duiProblem(),
        Screen.identityConfirmation => _identityConfirmation(),
        Screen.income => _income(),
        Screen.expectedActivity => _expectedActivity(),
        Screen.review => _review(),
        Screen.confirmation => _confirmation(),
      };

  // ---------------------------------------------------------------- 2. Privacy

  Widget _privacy() {
    final alreadyAccepted = _form.captureAllowed;
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const ScreenTitle(PrivacyNotice.title),
          const SizedBox(height: 12),
          const _VersionBadge(),
          const SizedBox(height: 16),
          const Text(PrivacyNotice.intro, style: AppText.body),
          const SizedBox(height: 20),
          for (final item in PrivacyNotice.signals) ...[
            IconRow(
              leading: _NoticeIcon(item),
              title: item.title,
              description: item.description,
            ),
            const SizedBox(height: 16),
          ],
          LinkButton(text: PrivacyNotice.readFullNotice, onPressed: _showFullNotice),
          const SizedBox(height: 8),
          const Divider(height: 1, color: AppColors.border),
          const SizedBox(height: 8),
          // Once the request exists, the consent is already recorded: it cannot be unchecked.
          _AcceptanceCheckbox(
            text: PrivacyNotice.acceptance,
            value: _form.accepted,
            onChanged: alreadyAccepted ? null : (v) => setState(() => _form.accepted = v),
          ),
          _AcceptanceCheckbox(
            text: PrivacyNotice.termsAcceptance,
            value: _form.termsAccepted,
            onChanged: alreadyAccepted ? null : (v) => setState(() => _form.termsAccepted = v),
          ),
          if (_errorFor('accepted').isNotEmpty) ErrorText(_errorFor('accepted')),
          if (_startError != null) ...[
            const SizedBox(height: 16),
            _ConnectionError(
              title: 'No pudimos iniciar tu solicitud',
              message: _startError!,
              onRetry: _startRequest,
            ),
          ],
        ],
      ),
    );
  }

  /// The full notice, in a sheet over the screen. Provisional text until Legal approves it.
  void _showFullNotice() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: Colors.white,
      builder: (context) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.85,
        maxChildSize: 0.95,
        builder: (context, scroll) => Column(
          children: [
            Expanded(
              child: ListView(
                controller: scroll,
                padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
                children: [
                  const ScreenTitle(PrivacyNotice.fullTitle, size: 24),
                  const SizedBox(height: 12),
                  const _VersionBadge(),
                  const SizedBox(height: 16),
                  const Text(PrivacyNotice.fullIntro, style: AppText.body, textAlign: TextAlign.justify),
                  for (final section in PrivacyNotice.fullSections) ...[
                    const SizedBox(height: 20),
                    Semantics(header: true, child: Text(section.title, style: AppText.heading(18))),
                    for (final paragraph in section.paragraphs) ...[
                      const SizedBox(height: 8),
                      Text(paragraph, style: AppText.body, textAlign: TextAlign.justify),
                    ],
                  ],
                ],
              ),
            ),
            Padding(
              padding: EdgeInsets.fromLTRB(24, 8, 24, 16 + MediaQuery.paddingOf(context).bottom),
              child: PrimaryButton(text: 'Cerrar', onPressed: () => Navigator.of(context).pop()),
            ),
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------- 3. Basic data

  Widget _basicData() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 28),
      child: AutofillGroup(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const ScreenTitle('Cuéntanos quién eres'),
            const SizedBox(height: 20),
            LabeledTextField(
              controller: _firstNames,
              label: 'Nombres',
              helper: 'Hasta $maxNameLength caracteres.',
              placeholder: 'Por ejemplo, Marta Alejandra',
              autofillHints: const [AutofillHints.givenName],
              error: _errorFor('firstNames'),
              formatters: [LengthLimitingTextInputFormatter(maxNameLength)],
              onChanged: (v) {
                _onTyped('firstNames', v);
                setState(() => _form.firstNames = v);
              },
            ),
            const SizedBox(height: 20),
            LabeledTextField(
              controller: _lastNames,
              label: 'Apellidos',
              helper: 'Hasta $maxNameLength caracteres.',
              placeholder: 'Por ejemplo, Rivas Cruz',
              autofillHints: const [AutofillHints.familyName],
              error: _errorFor('lastNames'),
              formatters: [LengthLimitingTextInputFormatter(maxNameLength)],
              onChanged: (v) {
                _onTyped('lastNames', v);
                setState(() => _form.lastNames = v);
              },
            ),
            const SizedBox(height: 20),
            LabeledTextField(
              controller: _dui,
              label: 'Número de DUI',
              placeholder: '00000000-0',
              keyboardType: TextInputType.number,
              formatters: [maskFormatter(formatDui)],
              error: _errorFor('dui'),
              onChanged: (v) {
                _onTyped('dui', v);
                setState(() => _form.dui = v);
              },
            ),
            const SizedBox(height: 20),
            LabeledTextField(
              controller: _phone,
              label: 'Teléfono celular',
              placeholder: '0000-0000',
              keyboardType: TextInputType.phone,
              autofillHints: const [AutofillHints.telephoneNumberNational],
              formatters: [maskFormatter(formatPhone)],
              error: _errorFor('phone'),
              onChanged: (v) {
                _onTyped('phone', v);
                setState(() => _form.phone = v);
              },
            ),
            if (_saveError != null) ...[
              const SizedBox(height: 16),
              _ConnectionError(
                title: 'No pudimos guardar tus datos',
                message: _saveError!,
                onRetry: _continue,
              ),
            ],
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------- 4. DUI

  /// The camera is shown when there is nothing to fix: no unreadable photos and no error.
  bool get _capturingDui => _screen == Screen.identityDocument && _unreadable == null && _duiError == null;

  Widget _duiCapture() => DuiCaptureView(
        // A new key per round, so the camera asks only for the sides to take again.
        key: ValueKey(_duiToCapture),
        camera: widget.camera,
        sides: _duiToCapture,
        onCaptured: _onDuiCaptured,
        onBack: _busy || _previousScreen == null ? null : () => _goTo(_previousScreen!),
      );

  /// Unreadable photos (what happened, how to take them better) or an error sending them.
  Widget _duiProblem() {
    final unreadable = _unreadable;
    if (unreadable == null) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(24, 16, 24, 28),
        child: _ConnectionError(title: 'No pudimos revisar tu DUI', message: _duiError ?? '', onRetry: _uploadDui),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ScreenTitle(unreadable.title),
          const SizedBox(height: 16),
          for (final side in unreadable.sides)
            if (_duiPhotos[side] case final photo?)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.memory(
                    photo,
                    height: 160,
                    width: double.infinity,
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => const SizedBox.shrink(),
                  ),
                ),
              ),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: AppColors.errorBg, borderRadius: BorderRadius.circular(12)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('No pudimos leer tu DUI', style: AppText.heading(18, color: AppColors.errorDark)),
                const SizedBox(height: 4),
                Text(unreadable.message, style: AppText.body),
              ],
            ),
          ),
          const SizedBox(height: 24),
          const SectionTitle('Para que salga bien'),
          const SizedBox(height: 12),
          for (var i = 0; i < UnreadablePhotos.tips.length; i++) ...[
            IconRow(leading: NumberBadge(i + 1), title: UnreadablePhotos.tips[i], description: ''),
            const SizedBox(height: 12),
          ],
          const SizedBox(height: 12),
          PrimaryButton(
            text: unreadable.sides.length > 1 ? 'Tomar otras fotos' : 'Tomar otra foto',
            onPressed: _retakeDui,
          ),
        ],
      ),
    );
  }

  /// VDI-78: «¿Leímos bien tus datos?»: the five data read from the photos, to confirm or correct. What was not
  /// read clearly is marked; each photo can be repeated.
  Widget _identityConfirmation() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const ScreenTitle('¿Leímos bien tus datos?'),
          const SizedBox(height: 8),
          Text(
            _form.readerFailed
                ? 'No pudimos leer las fotos de tu DUI. Escribe tus datos tal como aparecen en él.'
                : 'Los sacamos de las fotos de tu DUI. Corrige lo que no esté bien.',
            style: AppText.body,
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(child: _duiPhotoCard(DuiSide.front, 'Frente')),
              const SizedBox(width: 12),
              Expanded(child: _duiPhotoCard(DuiSide.back, 'Reverso')),
            ],
          ),
          const SizedBox(height: 20),
          _idField('idDui', _idDui, 'Número de DUI', '00000000-0',
              keyboardType: TextInputType.number, mask: formatDui, set: (v) => _form.idDui = v),
          const SizedBox(height: 20),
          _idField('idFirstNames', _idFirstNames, 'Nombres', 'Por ejemplo, Marta Alejandra',
              limit: maxNameLength, set: (v) => _form.idFirstNames = v),
          const SizedBox(height: 20),
          _idField('idLastNames', _idLastNames, 'Apellidos', 'Por ejemplo, Rivas Cruz',
              limit: maxNameLength, set: (v) => _form.idLastNames = v),
          const SizedBox(height: 20),
          _idField('idBirthDate', _idBirthDate, 'Fecha de nacimiento', 'dd/mm/aaaa',
              keyboardType: TextInputType.number, mask: formatDate, set: (v) => _form.idBirthDate = v),
          const SizedBox(height: 20),
          _idField('idExpiryDate', _idExpiryDate, 'Vence el', 'dd/mm/aaaa',
              keyboardType: TextInputType.number, mask: formatDate, set: (v) => _form.idExpiryDate = v),
          if (_saveError != null) ...[
            const SizedBox(height: 16),
            _ConnectionError(title: 'No pudimos guardar los datos de tu DUI', message: _saveError!, onRetry: _continue),
          ],
        ],
      ),
    );
  }

  /// A field of the DUI data; if the reader did not read it clearly, it says so below.
  Widget _idField(
    String field,
    TextEditingController controller,
    String label,
    String placeholder, {
    required void Function(String) set,
    String Function(String)? mask,
    TextInputType keyboardType = TextInputType.text,
    int? limit,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        LabeledTextField(
          controller: controller,
          label: label,
          placeholder: placeholder,
          keyboardType: keyboardType,
          formatters: [if (mask != null) maskFormatter(mask), if (limit != null) LengthLimitingTextInputFormatter(limit)],
          error: _errorFor(field),
          onChanged: (v) {
            _onTyped(field, v);
            setState(() {
              set(v);
              _form.markEdited(field);
            });
          },
        ),
        if (_form.idUnclear.contains(field))
          const Padding(
            padding: EdgeInsets.only(top: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.error_outline, size: 18, color: AppColors.errorDark),
                SizedBox(width: 6),
                Expanded(child: Text('No lo leímos con claridad. Revísalo.', style: AppText.error)),
              ],
            ),
          ),
      ],
    );
  }

  /// Thumbnail of a photo with «Repetir».
  Widget _duiPhotoCard(DuiSide side, String label) {
    final photo = _duiPhotos[side];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: AspectRatio(
            aspectRatio: 1.586,
            child: photo == null
                ? const ColoredBox(color: AppColors.neutral200)
                : Image.memory(photo, fit: BoxFit.cover, errorBuilder: (_, _, _) => const ColoredBox(color: AppColors.neutral200)),
          ),
        ),
        Row(
          children: [
            Expanded(child: Text(label, style: AppText.bodyBold)),
            LinkButton(
              text: 'Repetir',
              semanticLabel: 'Repetir la foto del ${side == DuiSide.front ? 'frente' : 'reverso'}',
              onPressed: _busy ? () {} : () => _repeatPhoto(side),
            ),
          ],
        ),
      ],
    );
  }

  // ---------------------------------------------------------------- 5. Income

  Widget _income() {
    final sourceError = _errorFor('incomeSource');
    final detailError = _errorFor('incomeSourceDetail');
    final rangeError = _errorFor('incomeRange');
    final catalogs = _catalogs;
    return Column(
      children: [
        const ColorHeader(
          color: AppColors.brandSoft,
          eyebrow: 'Tus ingresos',
          title: '¿De dónde viene tu dinero?',
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (catalogs == null)
                _optionsPlaceholder()
              else ...[
                const SectionTitle('Origen de tus ingresos'),
                const SizedBox(height: 12),
                ..._options(catalogs.incomeSources, _form.incomeSource, (v) => _form.incomeSource = v),
                if (sourceError.isNotEmpty) ErrorText(sourceError),
                if (_form.incomeSource == otherIncomeSource) ...[
                  const SizedBox(height: 8),
                  LabeledTextField(
                    controller: _incomeSourceDetail,
                    label: '¿De dónde vienen tus ingresos?',
                    placeholder: 'Por ejemplo, venta de artesanías',
                    formatters: [LengthLimitingTextInputFormatter(maxIncomeDetailLength)],
                    error: detailError,
                    onChanged: (v) {
                      _onTyped('incomeSourceDetail', v);
                      setState(() => _form.incomeSourceDetail = v);
                    },
                  ),
                ],
                const SizedBox(height: 28),
                const SectionTitle('¿Cuánto ganas al mes?'),
                const SizedBox(height: 12),
                ..._options(catalogs.incomeRanges, _form.incomeRange, (v) => _form.incomeRange = v),
                if (rangeError.isNotEmpty) ErrorText(rangeError),
                if (_saveError != null) ...[
                  const SizedBox(height: 16),
                  _ConnectionError(
                    title: 'No pudimos guardar tus ingresos',
                    message: _saveError!,
                    onRetry: _continue,
                  ),
                ],
              ],
            ],
          ),
        ),
      ],
    );
  }

  /// While the API options load, or if they failed.
  Widget _optionsPlaceholder() {
    if (_catalogsFailed) {
      return _ConnectionError(
        title: 'No pudimos cargar las opciones',
        message: 'Revisa tu conexión a internet e inténtalo de nuevo.',
        onRetry: _loadCatalogs,
      );
    }
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 32),
      child: Center(child: CircularProgressIndicator(color: AppColors.brandGreen)),
    );
  }

  List<Widget> _options(List<Option> options, String current, void Function(String) choose) {
    return [
      for (final o in options)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: OptionCard(
            title: o.label,
            description: o.description,
            selected: current == o.value,
            onTap: () => setState(() => choose(o.value)),
          ),
        ),
    ];
  }

  // ---------------------------------------------------------------- 5. Expected activity

  Widget _expectedActivity() {
    final typeError = _errorFor('transactionType');
    final rangeError = _errorFor('amountRange');
    final catalogs = _catalogs;
    return Column(
      children: [
        const ColorHeader(
          color: AppColors.brandSoft,
          eyebrow: 'El dinero de tu cuenta',
          title: '¿Qué dinero pasará por esta cuenta?',
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const InfoBox(
                highlight: 'No es lo mismo que tus ingresos.',
                text: 'Aquí nos dices qué dinero esperas mover en esta cuenta cada mes.',
              ),
              const SizedBox(height: 24),
              if (catalogs == null)
                _optionsPlaceholder()
              else ...[
                const SectionTitle('Tipo de dinero que manejarás'),
                const SizedBox(height: 12),
                ..._options(
                  [
                    for (final t in catalogs.transactionTypes)
                      Option(t.value, t.label, transactionTypeDescriptions[t.value] ?? ''),
                  ],
                  _form.transactionType,
                  (v) => _form.transactionType = v,
                ),
                if (typeError.isNotEmpty) ErrorText(typeError),
                const SizedBox(height: 28),
                const SectionTitle('Monto mensual estimado'),
                const SizedBox(height: 4),
                const Text(
                  'Un cálculo aproximado de lo que moverás en un mes, en dólares.',
                  style: AppText.small,
                ),
                const SizedBox(height: 12),
                ..._options(catalogs.amountRanges, _form.amountRange, (v) => _form.amountRange = v),
                if (rangeError.isNotEmpty) ErrorText(rangeError),
                if (_saveError != null) ...[
                  const SizedBox(height: 16),
                  _ConnectionError(
                    title: 'No pudimos guardar esta información',
                    message: _saveError!,
                    onRetry: _continue,
                  ),
                ],
              ],
            ],
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------- 6. Review

  Widget _review() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const ScreenTitle('Revisa tu solicitud'),
          const SizedBox(height: 6),
          const Text('Si algo no está bien, puedes corregirlo antes de enviar.', style: AppText.bodyMuted),
          const SizedBox(height: 20),
          if (_submitFailed) ...[
            _ConnectionError(
              title: 'No pudimos enviar tu solicitud',
              message: 'Parece que se perdió la conexión. Tus datos siguen aquí. '
                  'Revisa tu internet e inténtalo de nuevo.',
              onRetry: _submit,
            ),
            const SizedBox(height: 20),
          ],
          _SummaryCard(
            title: 'Datos básicos',
            editSemanticLabel: 'Editar datos básicos',
            line1: _form.fullName,
            line2: 'DUI ${_form.dui} · Cel. ${_form.phone}',
            onEdit: () => _goTo(Screen.basicData),
          ),
          if (_duiConfirmed) ...[
            const SizedBox(height: 14),
            _SummaryCard(
              title: 'Tu DUI',
              line1: _form.idDui.isEmpty
                  ? 'Ya guardamos las fotos y los datos de tu DUI'
                  : 'DUI ${_form.idDui} · Vence ${_form.idExpiryDate}',
              line2: _form.idDui.isEmpty
                  ? 'Confirmado · las fotos no se guardan en tu teléfono'
                  : 'Confirmado · foto del frente y del reverso',
            ),
          ],
          const SizedBox(height: 14),
          _SummaryCard(
            title: 'Tus ingresos',
            editSemanticLabel: 'Editar ingresos',
            line1: _form.incomeSource == otherIncomeSource
                ? 'Otro: ${_form.incomeSourceDetail.trim()}'
                : labelOf(_catalogs?.incomeSources ?? const [], _form.incomeSource),
            line2: '${labelOf(_catalogs?.incomeRanges ?? const [], _form.incomeRange)} al mes',
            onEdit: () => _goTo(Screen.income),
          ),
          const SizedBox(height: 14),
          _SummaryCard(
            title: 'Dinero de tu cuenta',
            editSemanticLabel: 'Editar movimiento esperado',
            line1: labelOf(_catalogs?.transactionTypes ?? const [], _form.transactionType),
            line2: '${labelOf(_catalogs?.amountRanges ?? const [], _form.amountRange)} al mes',
            onEdit: () => _goTo(Screen.expectedActivity),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------- 7. Confirmation

  Widget _confirmation() {
    return Padding(
      padding: EdgeInsets.fromLTRB(24, 16, 24, 32 + MediaQuery.paddingOf(context).bottom),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(color: AppColors.brand, shape: BoxShape.circle),
            child: const Icon(Icons.check_rounded, size: 40, color: AppColors.ink),
          ),
          const SizedBox(height: 20),
          const ScreenTitle('¡Recibimos tu solicitud!', size: 32),
          const SizedBox(height: 8),
          const Text('Te avisaremos cuando tu cuenta esté lista.', style: AppText.body),
          const SizedBox(height: 20),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.neutral100,
              border: Border.all(color: AppColors.border),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Tu número de solicitud', style: AppText.label),
                const SizedBox(height: 2),
                Text(
                  _form.number ?? '',
                  style: AppText.heading(28).copyWith(letterSpacing: 0.5),
                ),
                const SizedBox(height: 12),
                Semantics(
                  liveRegion: true,
                  child: SecondaryButton(
                    text: _copied ? '¡Número copiado!' : 'Copiar número',
                    onPressed: () async {
                      await Clipboard.setData(ClipboardData(text: _form.number ?? ''));
                      if (mounted) setState(() => _copied = true);
                    },
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          const SectionTitle('¿Qué sigue?', size: 20),
          const SizedBox(height: 12),
          for (final (i, text) in const [
            (1, 'Revisamos tu solicitud con calma.'),
            (2, 'Te avisaremos cuando tu cuenta esté lista.'),
            (3, 'Guarda tu número de solicitud por si necesitas consultarlo.'),
          ]) ...[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                NumberBadge(i, size: 28),
                const SizedBox(width: 12),
                Expanded(child: Text(text, style: AppText.body)),
              ],
            ),
            const SizedBox(height: 12),
          ],
          const SizedBox(height: 16),
          const Divider(color: AppColors.border, height: 1),
          const SizedBox(height: 12),
          Center(child: LinkButton(text: 'Volver al inicio', onPressed: _restart)),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- 2b. Location permission (VDI-73)

/// Asks for the approximate location before the form. It can be declined: the request goes on and the
/// console shows «Ubicación no disponible». The buttons are in the action bar.
class _LocationPermission extends StatelessWidget {
  const _LocationPermission();

  /// What is kept and what is not; the part in [bold] is highlighted.
  static const _points = [
    ('Solo guardamos ', 'ciudad y país', '.'),
    ('No guardamos tu dirección ni tus coordenadas.', '', ''),
    ('Si prefieres no compartirla, puedes completar tu solicitud igual.', '', ''),
  ];

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              key: const Key('location-illustration'),
              width: 128,
              height: 128,
              decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.brandSoft),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Icon(Icons.map_outlined, size: 76, color: AppColors.brandGreen.withValues(alpha: 0.35)),
                  const Padding(
                    padding: EdgeInsets.only(bottom: 18),
                    child: Icon(Icons.location_on, size: 52, color: AppColors.brandGreen),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const ScreenTitle('¿Desde qué ciudad nos escribes?'),
          const SizedBox(height: 12),
          const Text(
            'Saber tu ciudad nos ayuda a detectar si alguien intenta abrir una cuenta a tu nombre desde otro lugar.',
            style: AppText.body,
          ),
          const SizedBox(height: 20),
          Container(
            key: const Key('location-points'),
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: AppColors.neutral100, borderRadius: BorderRadius.circular(4)),
            child: Column(
              children: [
                for (var i = 0; i < _points.length; i++) ...[
                  if (i > 0) const SizedBox(height: 12),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Padding(
                        padding: EdgeInsets.only(top: 2),
                        child: Icon(Icons.check, size: 20, color: AppColors.brandGreen),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text.rich(
                          TextSpan(
                            style: AppText.body,
                            children: [
                              TextSpan(text: _points[i].$1),
                              if (_points[i].$2.isNotEmpty)
                                TextSpan(text: _points[i].$2, style: const TextStyle(fontWeight: FontWeight.w800)),
                              if (_points[i].$3.isNotEmpty) TextSpan(text: _points[i].$3),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- 1. Welcome

class _Welcome extends StatelessWidget {
  const _Welcome({this.resumable});

  /// A request left halfway: the welcome offers to pick it up (VDI-87).
  final ResumeInfo? resumable;

  static const _stepNames = {
    Screen.basicData: 'Datos básicos',
    Screen.identityDocument: 'Tu DUI',
    Screen.income: 'Tus ingresos',
    Screen.expectedActivity: 'El dinero de tu cuenta',
    Screen.review: 'Revisar y enviar',
  };

  static const _steps = [
    ('Datos básicos', 'Tu nombre, DUI y celular.'),
    ('Tu DUI', 'Una foto del frente y otra del reverso.'),
    ('Tus ingresos', 'De dónde viene tu dinero y cuánto ganas.'),
    ('El dinero de tu cuenta', 'Qué dinero esperas mover cada mes.'),
    ('Revisar y enviar', 'Confirmas todo antes de mandarlo.'),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(24, 16, 24, 20),
          child: Center(child: FullLogo()),
        ),
        Container(
          width: double.infinity,
          color: AppColors.brandGreen,
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 36),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Abre tu cuenta en línea',
                style: AppText.heading(16, color: AppColors.brand).copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 12),
              Semantics(
                header: true,
                child: Text('Tu cuenta, desde tu teléfono.', style: AppText.heading(36, color: Colors.white, height: 1.1)),
              ),
              const SizedBox(height: 16),
              const Text(
                'Sin filas y sin ir a una agencia. Ten tu DUI a la mano: te toma unos 7 minutos.',
                style: TextStyle(fontSize: 18, height: 26 / 18, color: Colors.white),
              ),
              const SizedBox(height: 20),
              Container(
                constraints: const BoxConstraints(minHeight: 44),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: const ShapeDecoration(color: AppColors.brand, shape: StadiumBorder()),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.schedule, size: 20, color: AppColors.ink),
                    const SizedBox(width: 8),
                    Text('Unos 7 minutos', style: AppText.heading(16)),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (resumable != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 0),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.brand, width: 2),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Tienes una solicitud a medias',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '${resumable!.firstName == null ? 'Te' : '${resumable!.firstName}, te'} quedaste en '
                    '«${_stepNames[resumable!.step] ?? 'tu solicitud'}». Lo que ya guardamos sigue aquí.'
                    '${resumable!.step == Screen.identityDocument || (!resumable!.identityConfirmed && resumable!.step != Screen.basicData) ? ' Tendrás que volver a tomar las fotos de tu DUI.' : ''}',
                    style: AppText.bodyMuted,
                  ),
                ],
              ),
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 28, 24, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SectionTitle('Esto es lo que te vamos a pedir', size: 22),
              const SizedBox(height: 16),
              for (var i = 0; i < _steps.length; i++) ...[
                IconRow(leading: NumberBadge(i + 1, soft: true), title: _steps[i].$1, description: _steps[i].$2),
                const SizedBox(height: 14),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// Checkbox with its text; the whole row can be tapped. [onChanged] is `null` when it cannot change.
class _AcceptanceCheckbox extends StatelessWidget {
  const _AcceptanceCheckbox({required this.text, required this.value, required this.onChanged});

  final String text;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    final onChanged = this.onChanged;
    return InkWell(
      onTap: onChanged == null ? null : () => onChanged(!value),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 56),
        child: Row(
          children: [
            Transform.scale(
              scale: 1.3,
              child: Checkbox(value: value, onChanged: onChanged == null ? null : (v) => onChanged(v ?? false)),
            ),
            const SizedBox(width: 8),
            Expanded(child: Text(text, style: AppText.body)),
          ],
        ),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({
    required this.title,
    this.editSemanticLabel,
    required this.line1,
    required this.line2,
    this.onEdit,
  });

  final String title;
  final String? editSemanticLabel;
  final String line1;
  final String line2;

  /// `null` for a card that only informs: it has no «Editar».
  final VoidCallback? onEdit;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.border),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(child: SectionTitle(title)),
              if (onEdit != null) LinkButton(text: 'Editar', semanticLabel: editSemanticLabel, onPressed: onEdit!),
            ],
          ),
          Text(line1, style: AppText.body),
          Text(line2, style: AppText.bodyMuted),
        ],
      ),
    );
  }
}

/// «Guardamos tu avance en cada paso.», above the button of the first step.
class _SavedNote extends StatelessWidget {
  const _SavedNote();

  @override
  Widget build(BuildContext context) {
    return const Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.save_outlined, size: 18, color: AppColors.muted),
        SizedBox(width: 6),
        Flexible(child: Text('Guardamos tu avance en cada paso.', style: AppText.small, textAlign: TextAlign.center)),
      ],
    );
  }
}

enum _MismatchChoice { correct, goOn }

/// Warning that the basic data and the DUI do not match: what was written against what the DUI says.
class _MismatchWarning extends StatelessWidget {
  const _MismatchWarning({required this.differences});

  final List<IdentityMismatch> differences;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const ScreenTitle('Tus datos no coinciden con tu DUI', size: 24),
            const SizedBox(height: 12),
            const Text('Lo que escribiste al inicio es distinto de lo que dice tu DUI.', style: AppText.body),
            const SizedBox(height: 16),
            for (final d in differences) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(color: AppColors.neutral100, borderRadius: BorderRadius.circular(8)),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(d.label, style: AppText.bodyBold),
                    Text('Escribiste: ${d.declared}', style: AppText.body),
                    Text('Tu DUI dice: ${d.read}', style: AppText.body),
                  ],
                ),
              ),
              const SizedBox(height: 10),
            ],
            const SizedBox(height: 4),
            const Text(
              'Si tu DUI es el correcto, corrige tus datos. Si continúas así, la diferencia quedará registrada.',
              style: AppText.bodyMuted,
            ),
            const SizedBox(height: 20),
            PrimaryButton(text: 'Corregir mis datos', onPressed: () => Navigator.of(context).pop(_MismatchChoice.correct)),
            const SizedBox(height: 10),
            SecondaryButton(text: 'Continuar de todos modos', onPressed: () => Navigator.of(context).pop(_MismatchChoice.goOn)),
          ],
        ),
      ),
    );
  }
}

/// «Aviso aprobado por Legal · versión [X.Y]» (provisional until Legal approves the notice).
class _VersionBadge extends StatelessWidget {
  const _VersionBadge();

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: const ShapeDecoration(color: AppColors.brandSoft, shape: StadiumBorder()),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.verified_user_outlined, size: 18, color: AppColors.brandGreen),
            SizedBox(width: 6),
            Flexible(
              child: Text(
                PrivacyNotice.badge,
                style: TextStyle(fontSize: 14, height: 1.3, fontWeight: FontWeight.w700, color: AppColors.brandGreen),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Round icon of a data item of the notice; the DUI photos use the blue tint.
class _NoticeIcon extends StatelessWidget {
  const _NoticeIcon(this.item);

  final NoticeItem item;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 44,
      height: 44,
      decoration: BoxDecoration(shape: BoxShape.circle, color: item.document ? AppColors.blueSoft : AppColors.brandSoft),
      child: Icon(item.icon, size: 24, color: item.document ? AppColors.blue : AppColors.brandGreen),
    );
  }
}

class _ConnectionError extends StatelessWidget {
  const _ConnectionError({required this.title, required this.message, required this.onRetry});

  final String title;
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: AppColors.errorBg,
          border: Border.all(color: AppColors.error, width: 2),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(
              fontSize: 16, height: 1.5, fontWeight: FontWeight.w700, color: AppColors.errorDark,
            )),
            const SizedBox(height: 2),
            Text(message, style: AppText.body),
            const SizedBox(height: 12),
            SecondaryButton(text: 'Reintentar', onPressed: onRetry),
          ],
        ),
      ),
    );
  }
}

