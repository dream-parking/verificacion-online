import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme.dart';

/// Botón primario amarillo en píldora (`.btn` del diseño).
class BotonPrimario extends StatelessWidget {
  const BotonPrimario({super.key, required this.texto, required this.onPressed});

  final String texto;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FilledButton(
        onPressed: onPressed,
        style: ButtonStyle(
          minimumSize: const WidgetStatePropertyAll(Size.fromHeight(52)),
          padding: const WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: 30)),
          shape: const WidgetStatePropertyAll(StadiumBorder()),
          elevation: const WidgetStatePropertyAll(0),
          backgroundColor: WidgetStateProperty.resolveWith((s) {
            if (s.contains(WidgetState.disabled)) return AppColors.disabled;
            if (s.contains(WidgetState.pressed) || s.contains(WidgetState.hovered)) return AppColors.yellowHover;
            return AppColors.yellow;
          }),
          foregroundColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.disabled) ? AppColors.muted : AppColors.ink,
          ),
          textStyle: const WidgetStatePropertyAll(
            TextStyle(fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: 0.3),
          ),
        ),
        child: Text(texto.toUpperCase(), textAlign: TextAlign.center),
      ),
    );
  }
}

/// Botón secundario con borde (`.btn2` del diseño).
class BotonSecundario extends StatelessWidget {
  const BotonSecundario({super.key, required this.texto, required this.onPressed});

  final String texto;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton(
        onPressed: onPressed,
        style: ButtonStyle(
          minimumSize: const WidgetStatePropertyAll(Size.fromHeight(48)),
          padding: const WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: 24)),
          shape: const WidgetStatePropertyAll(StadiumBorder()),
          side: const WidgetStatePropertyAll(BorderSide(color: AppColors.ink)),
          backgroundColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.pressed) ? AppColors.dark : Colors.transparent,
          ),
          foregroundColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.pressed) ? Colors.white : AppColors.ink,
          ),
          textStyle: const WidgetStatePropertyAll(TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
        ),
        child: Text(texto.toUpperCase(), textAlign: TextAlign.center),
      ),
    );
  }
}

/// Enlace azul subrayado (`.lnk` del diseño).
class BotonEnlace extends StatelessWidget {
  const BotonEnlace({super.key, required this.texto, required this.onPressed, this.semanticLabel});

  final String texto;
  final VoidCallback onPressed;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticLabel,
      button: true,
      excludeSemantics: semanticLabel != null,
      child: TextButton(
        onPressed: onPressed,
        style: TextButton.styleFrom(
          minimumSize: const Size(44, 44),
          padding: const EdgeInsets.symmetric(horizontal: 4),
          foregroundColor: AppColors.blue,
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
            decoration: TextDecoration.underline,
            decorationColor: AppColors.blue,
          ),
        ),
        child: Text(texto),
      ),
    );
  }
}

/// Mensaje de error de validación bajo un campo.
class TextoError extends StatelessWidget {
  const TextoError(this.mensaje, {super.key});

  final String mensaje;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Semantics(liveRegion: true, child: Text(mensaje, style: AppText.error)),
    );
  }
}

/// Formateador que reemplaza el texto por `formato(texto)` y deja el cursor al final.
TextInputFormatter mascara(String Function(String) formato) {
  return TextInputFormatter.withFunction((_, nuevo) {
    final t = formato(nuevo.text);
    return TextEditingValue(text: t, selection: TextSelection.collapsed(offset: t.length));
  });
}

/// Campo de texto con línea inferior (`.fld` del diseño).
class CampoTexto extends StatelessWidget {
  const CampoTexto({
    super.key,
    required this.controller,
    required this.placeholder,
    required this.error,
    this.etiqueta,
    this.teclado = TextInputType.text,
    this.autofill,
    this.formatters = const [],
    this.onChanged,
  });

  final TextEditingController controller;
  final String? etiqueta;
  final String placeholder;
  final String error;
  final TextInputType teclado;
  final Iterable<String>? autofill;
  final List<TextInputFormatter> formatters;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    final hayError = error.isNotEmpty;
    UnderlineInputBorder linea(Color c, double w) =>
        UnderlineInputBorder(borderSide: BorderSide(color: c, width: w));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (etiqueta != null) Text(etiqueta!, style: AppText.label),
        TextField(
          controller: controller,
          keyboardType: teclado,
          autofillHints: autofill,
          inputFormatters: formatters,
          onChanged: onChanged,
          style: AppText.body,
          decoration: InputDecoration(
            hintText: placeholder,
            hintStyle: const TextStyle(color: AppColors.placeholder, fontSize: 16),
            isDense: true,
            contentPadding: const EdgeInsets.symmetric(vertical: 12),
            enabledBorder: hayError ? linea(AppColors.error, 2) : linea(AppColors.dark, 1),
            focusedBorder: hayError ? linea(AppColors.error, 2) : linea(AppColors.blue, 2),
          ),
        ),
        if (hayError) TextoError(error),
      ],
    );
  }
}

/// Opción seleccionable tipo radio en tarjeta (`.opt` del diseño).
class OpcionTarjeta extends StatelessWidget {
  const OpcionTarjeta({
    super.key,
    required this.titulo,
    required this.seleccionada,
    required this.onTap,
    this.descripcion = '',
  });

  final String titulo;
  final String descripcion;
  final bool seleccionada;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final conDescripcion = descripcion.isNotEmpty;
    return Semantics(
      inMutuallyExclusiveGroup: true,
      checked: seleccionada,
      child: Material(
        color: seleccionada ? AppColors.blueSoft : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: seleccionada
              ? const BorderSide(color: AppColors.blue, width: 2)
              : const BorderSide(color: AppColors.borderStrong),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 56),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              child: Row(
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 150),
                    width: 22,
                    height: 22,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.white,
                      border: Border.all(
                        color: seleccionada ? AppColors.blue : AppColors.muted,
                        width: seleccionada ? 7 : 2,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: conDescripcion
                        ? Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(titulo, style: AppText.bodyBold),
                              Text(descripcion, style: AppText.small),
                            ],
                          )
                        : Text(titulo, style: AppText.body),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Barra de progreso segmentada "Paso N de 4".
class ProgresoPasos extends StatelessWidget {
  const ProgresoPasos({super.key, required this.paso, this.total = 4});

  final int paso;
  final int total;

  @override
  Widget build(BuildContext context) {
    final texto = 'Paso $paso de $total';
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 14),
      child: Semantics(
        label: texto,
        excludeSemantics: true,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(texto, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AppColors.muted)),
            const SizedBox(height: 8),
            Row(
              children: [
                for (var i = 1; i <= total; i++) ...[
                  if (i > 1) const SizedBox(width: 6),
                  Expanded(
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 250),
                      height: 6,
                      decoration: BoxDecoration(
                        color: i <= paso ? AppColors.blue : AppColors.border,
                        borderRadius: BorderRadius.circular(3),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Marca Banco Tangamandapio para el encabezado: emblema redondo y nombre del banco,
/// como en la barra lateral de la consola web (Logo.tsx), adaptado a fondo blanco.
class Logo extends StatelessWidget {
  const Logo({super.key});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Banco Tangamandapio',
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
              border: Border.all(color: AppColors.yellow, width: 2),
            ),
            child: ClipOval(child: Image.asset('assets/marca/emblema.png', fit: BoxFit.cover)),
          ),
          const SizedBox(width: 10),
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('BANCO', style: AppText.marca(10, color: AppColors.marcaVerde, peso: 600, espaciado: 3.2)),
              Text('TANGAMANDAPIO', style: AppText.marca(15, color: AppColors.marcaAzul, peso: 700)),
            ],
          ),
        ],
      ),
    );
  }
}

/// Logo completo con el lema, para la bienvenida (mismo archivo que el login de la consola).
class LogoCompleto extends StatelessWidget {
  const LogoCompleto({super.key, this.ancho = 220});

  final double ancho;

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/marca/logo-tangamandapio.webp',
      width: ancho,
      semanticLabel: 'Banco Tangamandapio. Confianza que nos une, futuro que construimos',
    );
  }
}

/// Círculo negro numerado para listas de pasos.
class Numero extends StatelessWidget {
  const Numero(this.n, {super.key, this.size = 32});

  final int n;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: const BoxDecoration(color: AppColors.ink, shape: BoxShape.circle),
      child: Text('$n', style: AppText.heading(size >= 32 ? 16 : 14, color: Colors.white)),
    );
  }
}

/// Recuadro gris claro con texto explicativo.
class Aviso extends StatelessWidget {
  const Aviso({super.key, required this.destacado, required this.texto});

  final String destacado;
  final String texto;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(color: AppColors.neutral100, borderRadius: BorderRadius.circular(8)),
      child: Text.rich(
        TextSpan(children: [
          TextSpan(text: destacado, style: const TextStyle(fontWeight: FontWeight.w700)),
          TextSpan(text: ' $texto'),
        ]),
        style: AppText.body,
      ),
    );
  }
}

/// Encabezado de color con "eyebrow" y título (pantallas de ingresos/movimiento).
class Cabecera extends StatelessWidget {
  const Cabecera({super.key, required this.color, required this.eyebrow, required this.titulo});

  final Color color;
  final String eyebrow;
  final String titulo;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      color: color,
      padding: const EdgeInsets.fromLTRB(24, 22, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(eyebrow, style: AppText.heading(16)),
          const SizedBox(height: 6),
          Semantics(header: true, child: Text(titulo, style: AppText.heading(28, height: 1.15))),
        ],
      ),
    );
  }
}

/// Título de pantalla (h1).
class Titulo extends StatelessWidget {
  const Titulo(this.texto, {super.key, this.size = 28});

  final String texto;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(header: true, child: Text(texto, style: AppText.heading(size, height: 1.15)));
  }
}

/// Subtítulo de sección (h2).
class Subtitulo extends StatelessWidget {
  const Subtitulo(this.texto, {super.key, this.size = 18});

  final String texto;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(header: true, child: Text(texto, style: AppText.heading(size)));
  }
}

/// Fila icono + título + descripción.
class FilaIcono extends StatelessWidget {
  const FilaIcono({super.key, required this.leading, required this.titulo, required this.descripcion});

  final Widget leading;
  final String titulo;
  final String descripcion;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        leading,
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(titulo, style: AppText.bodyBold),
              if (descripcion.isNotEmpty) Text(descripcion, style: AppText.bodyMuted),
            ],
          ),
        ),
      ],
    );
  }
}
