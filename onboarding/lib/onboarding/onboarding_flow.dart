import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/catalogs.dart';
import '../api/onboarding_api.dart';
import '../env.dart';
import '../signals/device_fingerprint.dart';
import '../signals/signals.dart';
import '../theme.dart';
import 'application_form.dart';
import 'privacy_notice.dart';
import 'widgets.dart';

/// Account opening flow: welcome → privacy → 4 steps → confirmation.
class OnboardingFlow extends StatefulWidget {
  const OnboardingFlow({
    super.key,
    this.api,
    this.deviceInfo,
    this.clock = DateTime.now,
  });

  /// API client; tests pass a fake one.
  final OnboardingApi? api;

  /// Device reader (VDI-40); tests pass a fake one.
  final DeviceInfoSource? deviceInfo;

  /// Current time used to measure timings (VDI-43); tests pass a controlled one.
  final DateTime Function() clock;

  @override
  State<OnboardingFlow> createState() => _OnboardingFlowState();
}

class _OnboardingFlowState extends State<OnboardingFlow> {
  static const _next = {
    Screen.welcome: Screen.privacy,
    Screen.privacy: Screen.basicData,
    Screen.basicData: Screen.income,
    Screen.income: Screen.expectedActivity,
    Screen.expectedActivity: Screen.review,
  };
  static const _previous = {
    Screen.privacy: Screen.welcome,
    Screen.basicData: Screen.privacy,
    Screen.income: Screen.basicData,
    Screen.expectedActivity: Screen.income,
    Screen.review: Screen.expectedActivity,
  };
  static const _stepNumber = {
    Screen.basicData: 1,
    Screen.income: 2,
    Screen.expectedActivity: 3,
    Screen.review: 4,
  };

  final _scroll = ScrollController();
  final _firstNames = TextEditingController();
  final _lastNames = TextEditingController();
  final _dui = TextEditingController();
  final _phone = TextEditingController();
  final _incomeSourceDetail = TextEditingController();

  late final OnboardingApi _api = widget.api ?? OnboardingApi();
  late final DeviceInfoSource _deviceInfo = widget.deviceInfo ?? PlatformDeviceInfoSource();
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
  String? _startError;
  var _saving = false;
  String? _saveError;
  var _submitFailed = false;
  var _copied = false;

  @override
  void dispose() {
    for (final c in [_scroll, _firstNames, _lastNames, _dui, _phone, _incomeSourceDetail]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _loadCatalogs();
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

  void _goTo(Screen s) {
    FocusScope.of(context).unfocus();
    setState(() {
      _screen = s;
      _submitFailed = false;
      _startError = null;
      _saveError = null;
    });
    if (_scroll.hasClients) _scroll.jumpTo(0);
    // VDI-43: the step start stays in memory; it is only sent after the notice is accepted.
    _signals.steps.start(s, widget.clock());
    if (_signalsPending) unawaited(_sendSignals());
  }

  /// VDI-43: marks the step as completed and sends the signals.
  void _completeStep(Screen s) {
    _signals.steps.complete(s, widget.clock());
    unawaited(_sendSignals());
  }

  /// VDI-43: typing speed in the text fields (only after the notice is accepted).
  void _onTyped(String field, String text) {
    if (_form.captureAllowed) _signals.typing.record(field, text, widget.clock());
  }

  void _continue() {
    _signals.steps.attempt(_screen);
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
    _completeStep(_screen);
    _goTo(_next[_screen]!);
  }

  /// Saves first names, last names, DUI and mobile number in the API.
  Future<void> _sendBasicData() => _api.sendBasicData(
        _form.id!,
        firstNames: normalizeName(_form.firstNames),
        lastNames: normalizeName(_form.lastNames),
        dui: _form.dui,
        mobilePhone: _form.phone,
      );

  /// VDI-47: saves the income source and range in the API.
  Future<void> _declareIncome() => _api.declareIncome(
        _form.id!,
        source: _form.incomeSource,
        range: _form.incomeRange,
        detail: _form.incomeSource == otherIncomeSource ? _form.incomeSourceDetail.trim() : null,
      );

  /// VDI-51: saves the transaction type and the monthly amount range in the API.
  Future<void> _declareExpectedActivity() => _api.declareExpectedActivity(
        _form.id!,
        transactionType: _form.transactionType,
        amountRange: _form.amountRange,
      );

  /// Saves the current step in the API and only moves on if it was saved.
  Future<void> _saveAndContinue(Future<void> Function() save) async {
    final step = _screen;
    setState(() {
      _saving = true;
      _saveError = null;
    });
    try {
      await save();
      if (!mounted) return;
      setState(() => _saving = false);
      _completeStep(step);
      _goTo(_next[step]!);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _saveError = switch (e.status) {
          409 => 'Tu solicitud ya fue enviada y no se puede modificar.',
          400 => 'Revisa tus datos: ${e.message}',
          _ => 'Revisa tu conexión a internet e inténtalo de nuevo.',
        };
      });
    }
  }

  bool get _busy => _submitting || _starting || _saving;

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
      _signals.steps.complete(Screen.privacy, _form.privacyAcceptedAt!);
      _goTo(Screen.basicData);
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
    for (final c in [_firstNames, _lastNames, _dui, _phone, _incomeSourceDetail]) {
      c.clear();
    }
    setState(() {
      _form = ApplicationForm();
      _createdRequestId = null;
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
    final previous = _previous[_screen];
    final step = _stepNumber[_screen];

    // On Android the system "back" button goes to the previous step instead of closing the app.
    return PopScope(
      canPop: previous == null && !_busy,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && previous != null && !_busy) _goTo(previous);
      },
      child: Scaffold(
        body: SafeArea(
          bottom: false,
          child: Stack(
            children: [
              Column(
                children: [
                  // On the welcome screen the brand is shown large (full logo), not in the header.
                  if (_screen != Screen.welcome) _header(previous),
                  if (step != null) StepProgress(step: step),
                  Expanded(
                    child: SingleChildScrollView(
                      controller: _scroll,
                      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 200),
                        child: KeyedSubtree(key: ValueKey(_screen), child: _content()),
                      ),
                    ),
                  ),
                  if (_screen != Screen.confirmation) _actionBar(),
                ],
              ),
              if (_submitting)
                const _LoadingOverlay(title: 'Enviando tu solicitud…', detail: 'No cierres la aplicación.'),
              if (_saving)
                const _LoadingOverlay(title: 'Guardando tu información…', detail: 'Un momento, por favor.'),
              if (_starting)
                const _LoadingOverlay(title: 'Preparando tu solicitud…', detail: 'Esto puede tardar unos segundos.'),
            ],
          ),
        ),
      ),
    );
  }

  Widget _header(Screen? previous) {
    return SizedBox(
      height: 60,
      child: Padding(
        padding: const EdgeInsets.only(left: 8, right: 12),
        child: Row(
          children: [
            if (previous != null && !_busy)
              IconButton(
                tooltip: 'Volver al paso anterior',
                onPressed: () => _goTo(previous),
                icon: const Icon(Icons.arrow_back_ios_new, size: 22, color: AppColors.ink),
              )
            else
              const SizedBox(width: 12),
            const SizedBox(width: 4),
            const Logo(),
          ],
        ),
      ),
    );
  }

  Widget _actionBar() {
    final text = switch (_screen) {
      Screen.welcome => 'Empezar',
      Screen.review => 'Enviar solicitud',
      _ => 'Continuar',
    };
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      padding: EdgeInsets.fromLTRB(24, 12, 24, 20 + MediaQuery.paddingOf(context).bottom),
      child: PrimaryButton(text: text, onPressed: _busy ? null : _continue),
    );
  }

  Widget _content() => switch (_screen) {
        Screen.welcome => const _Welcome(),
        Screen.privacy => _privacy(),
        Screen.basicData => _basicData(),
        Screen.income => _income(),
        Screen.expectedActivity => _expectedActivity(),
        Screen.review => _review(),
        Screen.confirmation => _confirmation(),
      };

  // ---------------------------------------------------------------- 2. Privacy

  Widget _privacy() {
    final error = _errorFor('accepted');
    final alreadyAccepted = _form.captureAllowed;
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (PrivacyNotice.isProvisional) ...[
            const _ProvisionalLabel(),
            const SizedBox(height: 14),
          ],
          const ScreenTitle(PrivacyNotice.title),
          const SizedBox(height: 12),
          const Text(PrivacyNotice.intro, style: AppText.body),
          const SizedBox(height: 20),
          for (final (icon, title, description) in PrivacyNotice.signals) ...[
            IconRow(
              leading: Icon(icon, size: 32, color: AppColors.blue),
              title: title,
              description: description,
            ),
            const SizedBox(height: 16),
          ],
          const SizedBox(height: 8),
          const Text(PrivacyNotice.closing, style: AppText.body),
          const SizedBox(height: 16),
          // Once the request exists, the consent is already recorded: it cannot be unchecked.
          InkWell(
            onTap: alreadyAccepted ? null : () => setState(() => _form.accepted = !_form.accepted),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 56),
              child: Row(
                children: [
                  Transform.scale(
                    scale: 1.3,
                    child: Checkbox(
                      value: _form.accepted,
                      onChanged: alreadyAccepted ? null : (v) => setState(() => _form.accepted = v ?? false),
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Expanded(child: Text(PrivacyNotice.acceptance, style: AppText.body)),
                ],
              ),
            ),
          ),
          if (error.isNotEmpty) ErrorText(error),
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
            if (appEnv != 'prod') ...[
              const SizedBox(height: 12),
              LinkButton(text: 'Rellenar con datos de ejemplo (demo)', onPressed: _fillDemoData),
            ],
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

  void _fillDemoData() {
    _firstNames.text = 'Marta Alejandra';
    _lastNames.text = 'Rivas Cruz';
    _dui.text = '04812377-5';
    _phone.text = '7845-2310';
    setState(() {
      _form
        ..firstNames = _firstNames.text
        ..lastNames = _lastNames.text
        ..dui = _dui.text
        ..phone = _phone.text;
    });
  }

  // ---------------------------------------------------------------- 4. Income

  Widget _income() {
    final sourceError = _errorFor('incomeSource');
    final detailError = _errorFor('incomeSourceDetail');
    final rangeError = _errorFor('incomeRange');
    final catalogs = _catalogs;
    return Column(
      children: [
        const ColorHeader(
          color: AppColors.skyblue,
          eyebrow: 'Tus ingresos',
          title: '¿De dónde viene tu dinero?',
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const InfoBox(
                highlight: '¿Por qué lo preguntamos?',
                text: 'La ley exige que el banco conozca el origen de tus ingresos. '
                    'Se llama «Conozca a su Cliente».',
              ),
              const SizedBox(height: 24),
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
                    formatters: [LengthLimitingTextInputFormatter(150)],
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
      child: Center(child: CircularProgressIndicator(color: AppColors.blue)),
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
          color: AppColors.pink,
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
            decoration: const BoxDecoration(color: AppColors.yellow, shape: BoxShape.circle),
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

// ---------------------------------------------------------------- 1. Welcome

class _Welcome extends StatelessWidget {
  const _Welcome();

  static const _steps = [
    ('Datos básicos', 'Tu nombre, DUI y celular.'),
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
          color: AppColors.orange,
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 36),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Abre tu cuenta en línea', style: AppText.heading(16).copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 12),
              Semantics(
                header: true,
                child: Text('Tu cuenta, desde tu teléfono.', style: AppText.heading(36, height: 1.1)),
              ),
              const SizedBox(height: 16),
              const Text(
                'Sin filas y sin ir a una agencia. Te toma unos 5 minutos.',
                style: TextStyle(fontSize: 18, height: 26 / 18, color: AppColors.ink),
              ),
              const SizedBox(height: 20),
              Container(
                constraints: const BoxConstraints(minHeight: 44),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: const ShapeDecoration(color: Colors.white, shape: StadiumBorder()),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.schedule, size: 20, color: AppColors.ink),
                    const SizedBox(width: 8),
                    Text('Unos 5 minutos', style: AppText.heading(16)),
                  ],
                ),
              ),
            ],
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
                IconRow(leading: NumberBadge(i + 1), title: _steps[i].$1, description: _steps[i].$2),
                const SizedBox(height: 14),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _ProvisionalLabel extends StatelessWidget {
  const _ProvisionalLabel();

  @override
  Widget build(BuildContext context) {
    // Dashed box in the design; approximated with a solid gray border.
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.muted),
        borderRadius: BorderRadius.circular(4),
      ),
      child: const Text('Texto provisional · pendiente de revisión legal', style: AppText.small),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({
    required this.title,
    required this.editSemanticLabel,
    required this.line1,
    required this.line2,
    required this.onEdit,
  });

  final String title;
  final String editSemanticLabel;
  final String line1;
  final String line2;
  final VoidCallback onEdit;

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
              LinkButton(text: 'Editar', semanticLabel: editSemanticLabel, onPressed: onEdit),
            ],
          ),
          Text(line1, style: AppText.body),
          Text(line2, style: AppText.bodyMuted),
        ],
      ),
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

class _LoadingOverlay extends StatelessWidget {
  const _LoadingOverlay({required this.title, required this.detail});

  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return Positioned.fill(
      child: ColoredBox(
        color: Colors.white.withValues(alpha: 0.92),
        child: Semantics(
          liveRegion: true,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const SizedBox(
                width: 48,
                height: 48,
                child: CircularProgressIndicator(
                  strokeWidth: 5,
                  color: AppColors.blue,
                  backgroundColor: AppColors.border,
                  strokeCap: StrokeCap.round,
                ),
              ),
              const SizedBox(height: 16),
              Text(title, style: AppText.heading(20)),
              const SizedBox(height: 16),
              Text(detail, style: AppText.bodyMuted),
            ],
          ),
        ),
      ),
    );
  }
}
