import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/catalogos.dart';
import '../api/onboarding_api.dart';
import '../env.dart';
import '../senales/huella_dispositivo.dart';
import '../senales/senales.dart';
import '../theme.dart';
import 'aviso_privacidad.dart';
import 'solicitud.dart';
import 'widgets.dart';

/// Flujo de apertura de cuenta: bienvenida → privacidad → 4 pasos → confirmación.
class OnboardingFlow extends StatefulWidget {
  const OnboardingFlow({
    super.key,
    this.api,
    this.dispositivo,
    this.reloj = DateTime.now,
  });

  /// Cliente de la API; los tests pasan uno falso.
  final OnboardingApi? api;

  /// Lector del dispositivo (VDI-40); los tests pasan uno falso.
  final FuenteDispositivo? dispositivo;

  /// Hora actual para medir tiempos (VDI-43); los tests pasan una controlada.
  final DateTime Function() reloj;

  @override
  State<OnboardingFlow> createState() => _OnboardingFlowState();
}

class _OnboardingFlowState extends State<OnboardingFlow> {
  static const _siguiente = {
    Pantalla.bienvenida: Pantalla.privacidad,
    Pantalla.privacidad: Pantalla.basicos,
    Pantalla.basicos: Pantalla.ingresos,
    Pantalla.ingresos: Pantalla.movimiento,
    Pantalla.movimiento: Pantalla.revision,
  };
  static const _anterior = {
    Pantalla.privacidad: Pantalla.bienvenida,
    Pantalla.basicos: Pantalla.privacidad,
    Pantalla.ingresos: Pantalla.basicos,
    Pantalla.movimiento: Pantalla.ingresos,
    Pantalla.revision: Pantalla.movimiento,
  };
  static const _paso = {
    Pantalla.basicos: 1,
    Pantalla.ingresos: 2,
    Pantalla.movimiento: 3,
    Pantalla.revision: 4,
  };

  final _scroll = ScrollController();
  final _nombres = TextEditingController();
  final _apellidos = TextEditingController();
  final _dui = TextEditingController();
  final _tel = TextEditingController();
  final _detalleOrigen = TextEditingController();

  late final OnboardingApi _api = widget.api ?? OnboardingApi();
  late final FuenteDispositivo _dispositivo = widget.dispositivo ?? HuellaDispositivo();
  var _s = Solicitud();

  /// Solicitud creada en el backend cuyo aviso todavía no se pudo registrar: el reintento la reutiliza.
  String? _idCreado;

  /// Opciones de la API (VDI-48). No son datos personales: se piden al abrir la app.
  Catalogos? _catalogos;
  var _errorCatalogos = false;
  var _senales = Senales();
  var _senalesPendientes = false;
  Future<void>? _envioEnCurso;
  var _reenviar = false;
  var _pantalla = Pantalla.bienvenida;
  final _intentado = <Pantalla>{};
  var _enviando = false;
  var _preparando = false;
  String? _errorInicio;
  var _guardando = false;
  String? _errorGuardar;
  var _errorConexion = false;
  var _copiado = false;

  @override
  void dispose() {
    for (final c in [_scroll, _nombres, _apellidos, _dui, _tel, _detalleOrigen]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _cargarCatalogos();
  }

  Future<void> _cargarCatalogos() async {
    if (_errorCatalogos) setState(() => _errorCatalogos = false);
    try {
      final catalogos = await _api.catalogos();
      if (mounted) setState(() => _catalogos = catalogos);
    } on ApiException catch (e) {
      debugPrint('No se pudieron cargar los catálogos: $e');
      if (mounted) setState(() => _errorCatalogos = true);
    }
  }

  void _ir(Pantalla p) {
    FocusScope.of(context).unfocus();
    setState(() {
      _pantalla = p;
      _errorConexion = false;
      _errorInicio = null;
      _errorGuardar = null;
    });
    if (_scroll.hasClients) _scroll.jumpTo(0);
    // VDI-43: el inicio del paso queda en memoria; solo se envía después de aceptar el aviso.
    _senales.pasos.iniciar(p, widget.reloj());
    if (_senalesPendientes) unawaited(_enviarSenales());
  }

  /// VDI-43: marca el paso actual como completado y envía las señales.
  void _completarPaso(Pantalla p) {
    _senales.pasos.completar(p, widget.reloj());
    unawaited(_enviarSenales());
  }

  /// VDI-43: ritmo de escritura en los campos de texto (solo después de aceptar el aviso).
  void _escribio(String campo, String texto) {
    if (_s.capturaPermitida) _senales.ritmo.registrar(campo, texto, widget.reloj());
  }

  void _continuar() {
    _senales.pasos.intento(_pantalla);
    if (_pantalla == Pantalla.revision) {
      _enviar();
      return;
    }
    if (!_s.pantallaValida(_pantalla)) {
      setState(() => _intentado.add(_pantalla));
      return;
    }
    if (_pantalla == Pantalla.privacidad && !_s.capturaPermitida) {
      _iniciarSolicitud();
      return;
    }
    if (_pantalla == Pantalla.basicos) {
      _guardarYSeguir(_enviarDatosBasicos);
      return;
    }
    if (_pantalla == Pantalla.ingresos) {
      _guardarYSeguir(_declararIngresos);
      return;
    }
    if (_pantalla == Pantalla.movimiento) {
      _guardarYSeguir(_declararMovimiento);
      return;
    }
    _completarPaso(_pantalla);
    _ir(_siguiente[_pantalla]!);
  }

  /// Guarda nombres, apellidos, DUI y celular en la API.
  Future<void> _enviarDatosBasicos() => _api.enviarDatosBasicos(
        _s.id!,
        nombres: _s.nombres.trim(),
        apellidos: _s.apellidos.trim(),
        dui: _s.dui,
        celular: _s.tel,
      );

  /// VDI-47: guarda el origen y el rango de ingresos en la API.
  Future<void> _declararIngresos() => _api.declararIngresos(
        _s.id!,
        origen: _s.origen,
        rango: _s.nivel,
        detalle: _s.origen == origenOtro ? _s.detalleOrigen.trim() : null,
      );

  /// VDI-51: guarda el tipo de movimiento y el rango de monto mensual en la API.
  Future<void> _declararMovimiento() =>
      _api.declararMovimiento(_s.id!, tipo: _s.tipo, rangoMonto: _s.rangoMonto);

  /// Guarda el paso actual en la API y solo avanza si se guardó.
  Future<void> _guardarYSeguir(Future<void> Function() guardar) async {
    final paso = _pantalla;
    setState(() {
      _guardando = true;
      _errorGuardar = null;
    });
    try {
      await guardar();
      if (!mounted) return;
      setState(() => _guardando = false);
      _completarPaso(paso);
      _ir(_siguiente[paso]!);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _guardando = false;
        _errorGuardar = switch (e.status) {
          409 => 'Tu solicitud ya fue enviada y no se puede modificar.',
          400 => 'Revisa tus datos: ${e.mensaje}',
          _ => 'Revisa tu conexión a internet e inténtalo de nuevo.',
        };
      });
    }
  }

  bool get _ocupado => _enviando || _preparando || _guardando;

  /// VDI-40: lee el dispositivo y envía su huella. Solo corre después de aceptar el aviso.
  /// Si falla no interrumpe al cliente: las señales sirven para el score, no para continuar.
  Future<void> _capturarDispositivo() async {
    if (!_s.capturaPermitida) return;
    try {
      _senales.dispositivo = await _dispositivo.leer();
    } on Exception catch (e) {
      debugPrint('No se pudo leer el dispositivo: $e');
      return;
    }
    await _enviarSenales();
  }

  /// Envía todas las señales capturadas; si falla, se reintenta en el siguiente cambio de pantalla.
  ///
  /// Los envíos van de uno en uno: como el backend reemplaza todo en cada envío, uno viejo que
  /// llegara tarde borraría datos nuevos. Si se pide otro mientras hay uno en curso, se manda
  /// una vez más al terminar, con lo último capturado.
  Future<void> _enviarSenales() {
    if (_envioEnCurso != null) {
      _reenviar = true;
      return _envioEnCurso!;
    }
    return _envioEnCurso = _enviarEnOrden().whenComplete(() => _envioEnCurso = null);
  }

  Future<void> _enviarEnOrden() async {
    do {
      _reenviar = false;
      final id = _s.id;
      final senales = _senales;
      if (id == null || senales.dispositivo == null) return;
      _senalesPendientes = false;
      try {
        await _api.enviarSenales(id, senales);
      } on ApiException catch (e) {
        debugPrint('No se pudieron enviar las señales: $e');
        _senalesPendientes = true;
        return;
      }
    } while (_reenviar);
  }

  /// Crea la solicitud en el backend al aceptar el aviso y registra la aceptación. Hasta este momento
  /// no se captura nada. Si se creó pero falló el registro del aviso, el reintento usa la misma solicitud.
  Future<void> _iniciarSolicitud() async {
    setState(() {
      _preparando = true;
      _errorInicio = null;
    });
    try {
      final id = _idCreado ??= await _api.iniciarSolicitud();
      await _api.aceptarAviso(id);
      if (!mounted) return;
      setState(() {
        _s
          ..id = id
          ..avisoAceptadoEn = widget.reloj();
        _preparando = false;
      });
      _senales.pasos.completar(Pantalla.privacidad, _s.avisoAceptadoEn!);
      _ir(Pantalla.basicos);
      unawaited(_capturarDispositivo());
    } on ApiException {
      if (!mounted) return;
      setState(() {
        _preparando = false;
        _errorInicio = 'Revisa tu conexión a internet e inténtalo de nuevo.';
      });
    }
  }

  /// Envía la solicitud; el servidor le asigna su número único (SOL-AAAA-NNNNN).
  Future<void> _enviar() async {
    setState(() {
      _enviando = true;
      _errorConexion = false;
    });
    // VDI-43: las señales van antes del envío; una solicitud enviada ya no las acepta.
    _senales.pasos.completar(Pantalla.revision, widget.reloj());
    await _enviarSenales();
    try {
      final numero = await _api.enviarSolicitud(_s.id!);
      if (!mounted) return;
      setState(() {
        _s.numero = numero;
        _enviando = false;
      });
      _ir(Pantalla.confirmacion);
    } on ApiException catch (e) {
      debugPrint('No se pudo enviar la solicitud: $e');
      if (!mounted) return;
      setState(() {
        _enviando = false;
        _errorConexion = true;
      });
    }
  }

  void _reiniciar() {
    for (final c in [_nombres, _apellidos, _dui, _tel, _detalleOrigen]) {
      c.clear();
    }
    setState(() {
      _s = Solicitud();
      _idCreado = null;
      _senales = Senales();
      _senalesPendientes = false;
      _reenviar = false;
      _intentado.clear();
      _errorInicio = null;
      _errorGuardar = null;
      _copiado = false;
    });
    _ir(Pantalla.bienvenida);
  }

  /// Error visible de un campo: solo después de intentar continuar en esa pantalla.
  String _error(String campo) {
    if (!_intentado.contains(_pantalla) || !Solicitud.camposDe(_pantalla).contains(campo)) return '';
    return _s.mensajes()[campo]!;
  }

  @override
  Widget build(BuildContext context) {
    final anterior = _anterior[_pantalla];
    final paso = _paso[_pantalla];

    // En Android el botón "atrás" del sistema regresa al paso anterior en vez de cerrar la app.
    return PopScope(
      canPop: anterior == null && !_ocupado,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && anterior != null && !_ocupado) _ir(anterior);
      },
      child: Scaffold(
        body: SafeArea(
          bottom: false,
          child: Stack(
            children: [
              Column(
                children: [
                  // En la bienvenida la marca va en grande (logo completo), no en el encabezado.
                  if (_pantalla != Pantalla.bienvenida) _encabezado(anterior),
                  if (paso != null) ProgresoPasos(paso: paso),
                  Expanded(
                    child: SingleChildScrollView(
                      controller: _scroll,
                      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 200),
                        child: KeyedSubtree(key: ValueKey(_pantalla), child: _contenido()),
                      ),
                    ),
                  ),
                  if (_pantalla != Pantalla.confirmacion) _barraAccion(),
                ],
              ),
              if (_enviando)
                const _Cargando(titulo: 'Enviando tu solicitud…', detalle: 'No cierres la aplicación.'),
              if (_guardando)
                const _Cargando(titulo: 'Guardando tu información…', detalle: 'Un momento, por favor.'),
              if (_preparando)
                const _Cargando(titulo: 'Preparando tu solicitud…', detalle: 'Esto puede tardar unos segundos.'),
            ],
          ),
        ),
      ),
    );
  }

  Widget _encabezado(Pantalla? anterior) {
    return SizedBox(
      height: 60,
      child: Padding(
        padding: const EdgeInsets.only(left: 8, right: 12),
        child: Row(
          children: [
            if (anterior != null && !_ocupado)
              IconButton(
                tooltip: 'Volver al paso anterior',
                onPressed: () => _ir(anterior),
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

  Widget _barraAccion() {
    final texto = switch (_pantalla) {
      Pantalla.bienvenida => 'Empezar',
      Pantalla.revision => 'Enviar solicitud',
      _ => 'Continuar',
    };
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      padding: EdgeInsets.fromLTRB(24, 12, 24, 20 + MediaQuery.paddingOf(context).bottom),
      child: BotonPrimario(texto: texto, onPressed: _ocupado ? null : _continuar),
    );
  }

  Widget _contenido() => switch (_pantalla) {
        Pantalla.bienvenida => const _Bienvenida(),
        Pantalla.privacidad => _privacidad(),
        Pantalla.basicos => _basicos(),
        Pantalla.ingresos => _ingresos(),
        Pantalla.movimiento => _movimiento(),
        Pantalla.revision => _revision(),
        Pantalla.confirmacion => _confirmacion(),
      };

  // ---------------------------------------------------------------- 2. Privacidad

  Widget _privacidad() {
    final error = _error('aceptado');
    final yaAceptado = _s.capturaPermitida;
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (AvisoPrivacidad.esProvisional) ...[
            const _EtiquetaProvisional(),
            const SizedBox(height: 14),
          ],
          const Titulo(AvisoPrivacidad.titulo),
          const SizedBox(height: 12),
          const Text(AvisoPrivacidad.introduccion, style: AppText.body),
          const SizedBox(height: 20),
          for (final (icono, titulo, desc) in AvisoPrivacidad.senales) ...[
            FilaIcono(
              leading: Icon(icono, size: 32, color: AppColors.blue),
              titulo: titulo,
              descripcion: desc,
            ),
            const SizedBox(height: 16),
          ],
          const SizedBox(height: 8),
          const Text(AvisoPrivacidad.cierre, style: AppText.body),
          const SizedBox(height: 16),
          // Una vez creada la solicitud el consentimiento ya se registró: no se puede desmarcar.
          InkWell(
            onTap: yaAceptado ? null : () => setState(() => _s.aceptado = !_s.aceptado),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 56),
              child: Row(
                children: [
                  Transform.scale(
                    scale: 1.3,
                    child: Checkbox(
                      value: _s.aceptado,
                      onChanged: yaAceptado ? null : (v) => setState(() => _s.aceptado = v ?? false),
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Expanded(child: Text(AvisoPrivacidad.aceptacion, style: AppText.body)),
                ],
              ),
            ),
          ),
          if (error.isNotEmpty) TextoError(error),
          if (_errorInicio != null) ...[
            const SizedBox(height: 16),
            _ErrorConexion(
              titulo: 'No pudimos iniciar tu solicitud',
              mensaje: _errorInicio!,
              onReintentar: _iniciarSolicitud,
            ),
          ],
        ],
      ),
    );
  }

  // ---------------------------------------------------------------- 3. Datos básicos

  Widget _basicos() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 28),
      child: AutofillGroup(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Titulo('Cuéntanos quién eres'),
            const SizedBox(height: 20),
            CampoTexto(
              controller: _nombres,
              etiqueta: 'Nombres',
              placeholder: 'Por ejemplo, Marta Alejandra',
              autofill: const [AutofillHints.givenName],
              error: _error('nombres'),
              onChanged: (v) {
                _escribio('nombres', v);
                setState(() => _s.nombres = v);
              },
            ),
            const SizedBox(height: 20),
            CampoTexto(
              controller: _apellidos,
              etiqueta: 'Apellidos',
              placeholder: 'Por ejemplo, Rivas Cruz',
              autofill: const [AutofillHints.familyName],
              error: _error('apellidos'),
              onChanged: (v) {
                _escribio('apellidos', v);
                setState(() => _s.apellidos = v);
              },
            ),
            const SizedBox(height: 20),
            CampoTexto(
              controller: _dui,
              etiqueta: 'Número de DUI',
              placeholder: '00000000-0',
              teclado: TextInputType.number,
              formatters: [mascara(formatearDui)],
              error: _error('dui'),
              onChanged: (v) {
                _escribio('dui', v);
                setState(() => _s.dui = v);
              },
            ),
            const SizedBox(height: 20),
            CampoTexto(
              controller: _tel,
              etiqueta: 'Teléfono celular',
              placeholder: '0000-0000',
              teclado: TextInputType.phone,
              autofill: const [AutofillHints.telephoneNumberNational],
              formatters: [mascara(formatearTel)],
              error: _error('tel'),
              onChanged: (v) {
                _escribio('tel', v);
                setState(() => _s.tel = v);
              },
            ),
            if (appEnv != 'prod') ...[
              const SizedBox(height: 12),
              BotonEnlace(texto: 'Rellenar con datos de ejemplo (demo)', onPressed: _rellenarDemo),
            ],
            if (_errorGuardar != null) ...[
              const SizedBox(height: 16),
              _ErrorConexion(
                titulo: 'No pudimos guardar tus datos',
                mensaje: _errorGuardar!,
                onReintentar: _continuar,
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _rellenarDemo() {
    _nombres.text = 'Marta Alejandra';
    _apellidos.text = 'Rivas Cruz';
    _dui.text = '04812377-5';
    _tel.text = '7845-2310';
    setState(() {
      _s
        ..nombres = _nombres.text
        ..apellidos = _apellidos.text
        ..dui = _dui.text
        ..tel = _tel.text;
    });
  }

  // ---------------------------------------------------------------- 4. Ingresos

  Widget _ingresos() {
    final errOrigen = _error('origen');
    final errDetalle = _error('detalleOrigen');
    final errNivel = _error('nivel');
    final catalogos = _catalogos;
    return Column(
      children: [
        const Cabecera(
          color: AppColors.skyblue,
          eyebrow: 'Tus ingresos',
          titulo: '¿De dónde viene tu dinero?',
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Aviso(
                destacado: '¿Por qué lo preguntamos?',
                texto: 'La ley exige que el banco conozca el origen de tus ingresos. '
                    'Se llama «Conozca a su Cliente».',
              ),
              const SizedBox(height: 24),
              if (catalogos == null)
                _cargandoOpciones()
              else ...[
                const Subtitulo('Origen de tus ingresos'),
                const SizedBox(height: 12),
                ..._opciones(catalogos.origenesIngreso, _s.origen, (v) => _s.origen = v),
                if (errOrigen.isNotEmpty) TextoError(errOrigen),
                if (_s.origen == origenOtro) ...[
                  const SizedBox(height: 8),
                  CampoTexto(
                    controller: _detalleOrigen,
                    etiqueta: '¿De dónde vienen tus ingresos?',
                    placeholder: 'Por ejemplo, venta de artesanías',
                    formatters: [LengthLimitingTextInputFormatter(150)],
                    error: errDetalle,
                    onChanged: (v) {
                      _escribio('detalleOrigen', v);
                      setState(() => _s.detalleOrigen = v);
                    },
                  ),
                ],
                const SizedBox(height: 28),
                const Subtitulo('¿Cuánto ganas al mes?'),
                const SizedBox(height: 12),
                ..._opciones(catalogos.rangosIngreso, _s.nivel, (v) => _s.nivel = v),
                if (errNivel.isNotEmpty) TextoError(errNivel),
                if (_errorGuardar != null) ...[
                  const SizedBox(height: 16),
                  _ErrorConexion(
                    titulo: 'No pudimos guardar tus ingresos',
                    mensaje: _errorGuardar!,
                    onReintentar: _continuar,
                  ),
                ],
              ],
            ],
          ),
        ),
      ],
    );
  }

  /// Mientras llegan las opciones de la API, o si fallaron.
  Widget _cargandoOpciones() {
    if (_errorCatalogos) {
      return _ErrorConexion(
        titulo: 'No pudimos cargar las opciones',
        mensaje: 'Revisa tu conexión a internet e inténtalo de nuevo.',
        onReintentar: _cargarCatalogos,
      );
    }
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 32),
      child: Center(child: CircularProgressIndicator(color: AppColors.blue)),
    );
  }

  List<Widget> _opciones(List<Opcion> lista, String actual, void Function(String) elegir) {
    return [
      for (final o in lista)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: OpcionTarjeta(
            titulo: o.etiqueta,
            descripcion: o.descripcion,
            seleccionada: actual == o.valor,
            onTap: () => setState(() => elegir(o.valor)),
          ),
        ),
    ];
  }

  // ---------------------------------------------------------------- 5. Movimiento esperado

  Widget _movimiento() {
    final errTipo = _error('tipo');
    final errRango = _error('rangoMonto');
    final catalogos = _catalogos;
    return Column(
      children: [
        const Cabecera(
          color: AppColors.pink,
          eyebrow: 'El dinero de tu cuenta',
          titulo: '¿Qué dinero pasará por esta cuenta?',
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Aviso(
                destacado: 'No es lo mismo que tus ingresos.',
                texto: 'Aquí nos dices qué dinero esperas mover en esta cuenta cada mes.',
              ),
              const SizedBox(height: 24),
              if (catalogos == null)
                _cargandoOpciones()
              else ...[
                const Subtitulo('Tipo de dinero que manejarás'),
                const SizedBox(height: 12),
                ..._opciones(
                  [
                    for (final t in catalogos.tiposMovimiento)
                      Opcion(t.valor, t.etiqueta, descripcionesMovimiento[t.valor] ?? ''),
                  ],
                  _s.tipo,
                  (v) => _s.tipo = v,
                ),
                if (errTipo.isNotEmpty) TextoError(errTipo),
                const SizedBox(height: 28),
                const Subtitulo('Monto mensual estimado'),
                const SizedBox(height: 4),
                const Text(
                  'Un cálculo aproximado de lo que moverás en un mes, en dólares.',
                  style: AppText.small,
                ),
                const SizedBox(height: 12),
                ..._opciones(catalogos.rangosMonto, _s.rangoMonto, (v) => _s.rangoMonto = v),
                if (errRango.isNotEmpty) TextoError(errRango),
                if (_errorGuardar != null) ...[
                  const SizedBox(height: 16),
                  _ErrorConexion(
                    titulo: 'No pudimos guardar esta información',
                    mensaje: _errorGuardar!,
                    onReintentar: _continuar,
                  ),
                ],
              ],
            ],
          ),
        ),
      ],
    );
  }

  // ---------------------------------------------------------------- 6. Revisión

  Widget _revision() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 28),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Titulo('Revisa tu solicitud'),
          const SizedBox(height: 6),
          const Text('Si algo no está bien, puedes corregirlo antes de enviar.', style: AppText.bodyMuted),
          const SizedBox(height: 20),
          if (_errorConexion) ...[
            _ErrorConexion(
              titulo: 'No pudimos enviar tu solicitud',
              mensaje: 'Parece que se perdió la conexión. Tus datos siguen aquí. '
                  'Revisa tu internet e inténtalo de nuevo.',
              onReintentar: _enviar,
            ),
            const SizedBox(height: 20),
          ],
          _Resumen(
            titulo: 'Datos básicos',
            semanticaEditar: 'Editar datos básicos',
            linea1: _s.nombreCompleto,
            linea2: 'DUI ${_s.dui} · Cel. ${_s.tel}',
            onEditar: () => _ir(Pantalla.basicos),
          ),
          const SizedBox(height: 14),
          _Resumen(
            titulo: 'Tus ingresos',
            semanticaEditar: 'Editar ingresos',
            linea1: _s.origen == origenOtro
                ? 'Otro: ${_s.detalleOrigen.trim()}'
                : etiquetaDe(_catalogos?.origenesIngreso ?? const [], _s.origen),
            linea2: '${etiquetaDe(_catalogos?.rangosIngreso ?? const [], _s.nivel)} al mes',
            onEditar: () => _ir(Pantalla.ingresos),
          ),
          const SizedBox(height: 14),
          _Resumen(
            titulo: 'Dinero de tu cuenta',
            semanticaEditar: 'Editar movimiento esperado',
            linea1: etiquetaDe(_catalogos?.tiposMovimiento ?? const [], _s.tipo),
            linea2: '${etiquetaDe(_catalogos?.rangosMonto ?? const [], _s.rangoMonto)} al mes',
            onEditar: () => _ir(Pantalla.movimiento),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------- 7. Confirmación

  Widget _confirmacion() {
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
          const Titulo('¡Recibimos tu solicitud!', size: 32),
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
                  _s.numero ?? '',
                  style: AppText.heading(28).copyWith(letterSpacing: 0.5),
                ),
                const SizedBox(height: 12),
                Semantics(
                  liveRegion: true,
                  child: BotonSecundario(
                    texto: _copiado ? '¡Número copiado!' : 'Copiar número',
                    onPressed: () async {
                      await Clipboard.setData(ClipboardData(text: _s.numero ?? ''));
                      if (mounted) setState(() => _copiado = true);
                    },
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          const Subtitulo('¿Qué sigue?', size: 20),
          const SizedBox(height: 12),
          for (final (i, t) in const [
            (1, 'Revisamos tu solicitud con calma.'),
            (2, 'Te avisaremos cuando tu cuenta esté lista.'),
            (3, 'Guarda tu número de solicitud por si necesitas consultarlo.'),
          ]) ...[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Numero(i, size: 28),
                const SizedBox(width: 12),
                Expanded(child: Text(t, style: AppText.body)),
              ],
            ),
            const SizedBox(height: 12),
          ],
          const SizedBox(height: 16),
          const Divider(color: AppColors.border, height: 1),
          const SizedBox(height: 12),
          Center(child: BotonEnlace(texto: 'Volver al inicio', onPressed: _reiniciar)),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------- 1. Bienvenida

class _Bienvenida extends StatelessWidget {
  const _Bienvenida();

  static const _pasos = [
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
          child: Center(child: LogoCompleto()),
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
              const Subtitulo('Esto es lo que te vamos a pedir', size: 22),
              const SizedBox(height: 16),
              for (var i = 0; i < _pasos.length; i++) ...[
                FilaIcono(leading: Numero(i + 1), titulo: _pasos[i].$1, descripcion: _pasos[i].$2),
                const SizedBox(height: 14),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _EtiquetaProvisional extends StatelessWidget {
  const _EtiquetaProvisional();

  @override
  Widget build(BuildContext context) {
    // Recuadro punteado del diseño; se aproxima con borde sólido gris.
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

class _Resumen extends StatelessWidget {
  const _Resumen({
    required this.titulo,
    required this.semanticaEditar,
    required this.linea1,
    required this.linea2,
    required this.onEditar,
  });

  final String titulo;
  final String semanticaEditar;
  final String linea1;
  final String linea2;
  final VoidCallback onEditar;

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
              Expanded(child: Subtitulo(titulo)),
              BotonEnlace(texto: 'Editar', semanticLabel: semanticaEditar, onPressed: onEditar),
            ],
          ),
          Text(linea1, style: AppText.body),
          Text(linea2, style: AppText.bodyMuted),
        ],
      ),
    );
  }
}

class _ErrorConexion extends StatelessWidget {
  const _ErrorConexion({required this.titulo, required this.mensaje, required this.onReintentar});

  final String titulo;
  final String mensaje;
  final VoidCallback onReintentar;

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
            Text(titulo, style: const TextStyle(
              fontSize: 16, height: 1.5, fontWeight: FontWeight.w700, color: AppColors.errorDark,
            )),
            const SizedBox(height: 2),
            Text(mensaje, style: AppText.body),
            const SizedBox(height: 12),
            BotonSecundario(texto: 'Reintentar', onPressed: onReintentar),
          ],
        ),
      ),
    );
  }
}

class _Cargando extends StatelessWidget {
  const _Cargando({required this.titulo, required this.detalle});

  final String titulo;
  final String detalle;

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
              Text(titulo, style: AppText.heading(20)),
              const SizedBox(height: 16),
              Text(detalle, style: AppText.bodyMuted),
            ],
          ),
        ),
      ),
    );
  }
}
