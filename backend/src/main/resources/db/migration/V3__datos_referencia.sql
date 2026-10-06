-- =====================================================================
--  CEIBA · Datos de referencia: catálogos, aviso de privacidad, regla R-01
--  y tipos de alerta. Necesarios en todos los ambientes.
-- =====================================================================
SET LOCAL search_path TO ceiba, public;

INSERT INTO cat_origen_ingreso (codigo, etiqueta, orden) VALUES
  ('SALARIO','Salario',1), ('NEGOCIO_PROPIO','Negocio propio',2), ('REMESAS','Remesas',3),
  ('PENSION','Pensión',4), ('OTRO','Otro',5);

INSERT INTO cat_rango_ingreso (codigo, etiqueta, min_usd, max_usd, orden) VALUES
  ('MENOS_500','Menos de USD 500', NULL, 500, 1),
  ('500_1500','USD 500 a 1,500', 500, 1500, 2),
  ('1500_5000','USD 1,500 a 5,000', 1500, 5000, 3),
  ('MAS_5000','Más de USD 5,000', 5000, NULL, 4);

INSERT INTO cat_tipo_movimiento (codigo, etiqueta, orden) VALUES
  ('PAGO_SALARIO','Pago de salario',1), ('COBROS_NEGOCIO','Cobros de su negocio',2),
  ('REMESAS','Remesas familiares',3), ('AHORRO','Ahorro',4);

INSERT INTO aviso_privacidad (version, hash_texto, vigente_desde)
VALUES ('2026.1', repeat('0',64), '2026-09-01');

INSERT INTO regla_score (codigo, version, nombre, descripcion, campo_evaluado, operador, umbral,
                         resultado_si, resultado_no, estado, nota_estado, vigente_desde, actualizado_en)
VALUES ('R-01', 1, 'Monto mensual bajo',
        'Si el monto mensual declarado es menor a USD 500, el riesgo es bajo.',
        'movimiento_esperado.monto_mensual_usd', '<', 500, 'BAJO', 'PENDIENTE_EVALUACION',
        'PROVISIONAL', 'Valor provisional, pendiente de confirmar con Conozca a su Cliente.',
        '2026-10-02 00:00-06', '2026-10-02 00:00-06');

INSERT INTO tipo_alerta (codigo, descripcion, criticidad_default) VALUES
  ('PERFIL_EXCEDIDO','Movimientos por encima del perfil declarado','CRITICA'),
  ('ORIGEN_DISTINTO','Ingresos de origen distinto al declarado','CRITICA'),
  ('HORARIO_NOCTURNO','Actividad inusual en horario nocturno','ALTA'),
  ('DISPOSITIVO_NUEVO','Dispositivo nuevo con ubicación distinta','ALTA'),
  ('MULTI_SOLICITUD_DISPOSITIVO','Varias solicitudes desde el mismo dispositivo','ALTA'),
  ('RITMO_ATIPICO','Ritmo de escritura atípico','MEDIA'),
  ('MONTO_CERCA_UMBRAL','Monto declarado cercano al umbral','MEDIA'),
  ('CAMBIO_TELEFONO','Cambio de teléfono durante la solicitud','BAJA'),
  ('ACCESO_FUERA_HORARIO','Acceso fuera del horario habitual','BAJA');
