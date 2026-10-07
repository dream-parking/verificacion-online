import '../onboarding/solicitud.dart';

/// Opciones de `GET /api/catalogs` (VDI-46 y VDI-50). Los códigos se envían tal cual a la API.
class Catalogos {
  const Catalogos({
    required this.origenesIngreso,
    required this.rangosIngreso,
    required this.tiposMovimiento,
    required this.rangosMonto,
  });

  factory Catalogos.fromJson(Map<String, dynamic> json) {
    List<Opcion> lista(String clave) => [
          for (final item in (json[clave] as List? ?? const []).cast<Map<String, dynamic>>())
            Opcion(item['code'] as String, item['label'] as String),
        ];
    return Catalogos(
      origenesIngreso: lista('incomeSources'),
      rangosIngreso: lista('incomeRanges'),
      tiposMovimiento: lista('transactionTypes'),
      rangosMonto: lista('monthlyAmountRanges'),
    );
  }

  final List<Opcion> origenesIngreso;
  final List<Opcion> rangosIngreso;
  final List<Opcion> tiposMovimiento;
  final List<Opcion> rangosMonto;
}
