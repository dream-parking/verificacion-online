import 'package:flutter/material.dart';

/// One kind of data the app collects, as the notice lists it.
class NoticeItem {
  const NoticeItem(this.icon, this.title, this.description, {this.document = false});

  final IconData icon;
  final String title;
  final String description;

  /// The photos of the DUI: shown with the blue tint of the identity document in the design.
  final bool document;
}

/// A section of the full privacy notice: a heading and its paragraphs.
class NoticeSection {
  const NoticeSection(this.title, this.paragraphs);

  final String title;
  final List<String> paragraphs;
}

/// Privacy notice shown before any signal is captured (VDI-45, VDI-92).
///
/// The bank is fictional, so the text is its own: written for this project and not taken from a real institution.
abstract final class PrivacyNotice {
  static const title = 'Cuidamos tu cuenta desde el primer paso';

  /// Version of the notice shown: the one the server has in force and records when the notice is accepted
  /// (`privacy_notice.version`, VDI-93). If the server publishes a new version, change it here too.
  static const version = '2026.1';

  static const badge = 'Aviso aprobado por Legal · versión $version';

  static const intro =
      'Mientras llenas tu solicitud, recopilamos algunos datos para protegerte contra el fraude:';

  /// Data collected once the notice is accepted.
  static const signals = [
    NoticeItem(
      Icons.place_outlined,
      'Tu ubicación aproximada',
      'La ciudad desde donde haces la solicitud, no tu dirección exacta.',
    ),
    NoticeItem(
      Icons.smartphone_outlined,
      'El tipo de dispositivo',
      'Modelo de teléfono y sistema, para reconocer si es el mismo en otra ocasión.',
    ),
    NoticeItem(
      Icons.touch_app_outlined,
      'Cómo usas la aplicación',
      'Por ejemplo, el tiempo que tardas en cada paso y tu ritmo al escribir.',
    ),
    NoticeItem(
      Icons.badge_outlined,
      'Las fotos de tu DUI',
      'Frente y reverso, guardadas de forma segura en tu expediente.',
      document: true,
    ),
  ];

  static const readFullNotice = 'Leer el aviso de privacidad completo';

  static const closing = 'Solo usamos esta información para proteger tu cuenta.';

  static const acceptance = 'He leído y acepto el aviso de privacidad.';

  static const termsAcceptance = 'He leído y acepto los términos y condiciones.';

  static const acceptButton = 'Aceptar y continuar';

  static const recordedNote = 'Guardamos la fecha y la versión del aviso que aceptas.';

  // Full notice (provisional).
  static const fullTitle = 'Aviso de privacidad';

  static const fullIntro =
      'Este aviso explica qué datos recopilamos cuando abres tu cuenta en línea, para qué los usamos y cuáles son tus '
      'derechos.';

  static const fullSections = [
    NoticeSection('Quién es el responsable', [
      'Banco Tangamandapio es el responsable de tus datos personales y de decidir para qué se usan.',
    ]),
    NoticeSection('Qué datos recopilamos y para qué', [
      'Tus datos de identidad: nombres, apellidos, número de DUI, fecha de nacimiento, fecha de vencimiento del DUI y '
          'número de celular. Los usamos para saber quién eres y abrir tu cuenta.',
      'Las fotos de tu DUI, de frente y de reverso. Las usamos para leer tus datos y comprobar el documento, y se '
          'guardan cifradas en tu expediente.',
      'Tu ubicación aproximada: solo la ciudad y el país, nunca tu dirección ni tus coordenadas. Nos ayuda a '
          'detectar si alguien intenta abrir una cuenta a tu nombre desde otro lugar. Compartirla es opcional.',
      'El tipo de dispositivo: modelo de teléfono, sistema, idioma y tamaño de pantalla, para reconocer si es el '
          'mismo en otra ocasión.',
      'Cómo usas la aplicación: el tiempo que tardas en cada paso y tu ritmo al escribir, sin guardar lo que '
          'escribes, para protegerte contra el fraude.',
    ]),
    NoticeSection('Con quién los compartimos', [
      'No vendemos tus datos. Solo los compartimos con proveedores que nos ayudan a leer el documento y a proteger '
          'tu cuenta, y con las autoridades cuando la ley lo exige.',
    ]),
    NoticeSection('Cuánto tiempo los guardamos', [
      'El tiempo que la ley exija para los expedientes de vinculación de clientes.',
    ]),
    NoticeSection('Tus derechos', [
      'Puedes pedirnos acceso a tus datos, que los corrijamos, que los eliminemos cuando la ley lo permita y oponerte '
          'a su uso. Escríbenos a privacidad@tangamandapio.example.',
    ]),
    NoticeSection('Aceptación y versión', [
      'Al aceptar guardamos la fecha y la versión de este aviso. Esta es la versión $version.',
    ]),
  ];
}
