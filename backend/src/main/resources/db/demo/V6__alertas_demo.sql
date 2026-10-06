-- =====================================================================
--  CEIBA · Alertas de ejemplo para la bandeja del analista (VDI-63).
--  Mientras no exista el motor de alertas (VDI-29). Espejo de las 9 alertas de
--  la consola web (webconsole/src/lib/consola/datos.ts); la de la cuenta 4821
--  ya la crea V4. Solo dev/qa: Prod no carga db/demo.
-- =====================================================================

-- Clientes y cuentas de las otras 8 alertas (sin solicitud: solo hacen falta para enmascarar la cuenta).
INSERT INTO cliente (dui, nombres, apellidos, celular) VALUES
  ('03377841-2', 'Roberto Antonio',  'Menjívar',     '7021-5543'),
  ('05190462-8', 'Karla Vanessa',    'Hernández',    '7312-0087'),
  ('04905513-3', 'Daniela Beatriz',  'Ortiz',        '7788-4120'),
  ('01833920-6', 'Carlos Eduardo',   'Aguilar',      '7655-9012'),
  ('04177305-9', 'Ana Lucía',        'Portillo',     '7210-6678'),
  ('02984416-1', 'Mauricio Ernesto', 'Villalta',     '7499-3321'),
  ('03640258-7', 'José Armando',     'Quintanilla',  '7933-1450'),
  ('02266518-0', 'Josué Mauricio',   'Flores',       '6190-3374');

INSERT INTO cuenta (cliente_id, numero_token, ultimos4)
SELECT c.id, 'tok_demo_' || v.ultimos4, v.ultimos4
FROM (VALUES
  ('03377841-2', '7730'), ('05190462-8', '1156'), ('04905513-3', '9042'), ('01833920-6', '3318'),
  ('04177305-9', '6205'), ('02984416-1', '5871'), ('03640258-7', '2094'), ('02266518-0', '8467')
) AS v(dui, ultimos4)
JOIN cliente c ON c.dui = v.dui;

-- Alertas: 4 sin asignar, 4 asignadas y 1 en revisión (las asignadas respetan ck_alerta_responsable).
INSERT INTO alerta (cuenta_id, tipo_codigo, criticidad, motivo, estado, responsable_id, generada_en, asignada_en)
SELECT cu.id, v.tipo, v.criticidad::criticidad_alerta, v.motivo, v.estado::estado_alerta, u.id,
       v.generada::timestamptz,
       CASE WHEN u.id IS NOT NULL THEN v.generada::timestamptz + interval '20 minutes' END
FROM (VALUES
  ('7730', 'ORIGEN_DISTINTO',             'CRITICA', 'Ingresos recibidos de origen distinto al declarado',          'ASIGNADA',    'abeltran@ceiba.example',  '2026-10-05 07:40-06'),
  ('1156', 'HORARIO_NOCTURNO',            'ALTA',    'Actividad inusual en horario nocturno',                       'SIN_ASIGNAR', NULL,                      '2026-10-05 02:18-06'),
  ('9042', 'DISPOSITIVO_NUEVO',           'ALTA',    'Dispositivo nuevo con ubicación distinta a la declarada',     'EN_REVISION', 'lbarahona@ceiba.example', '2026-10-04 17:55-06'),
  ('3318', 'MULTI_SOLICITUD_DISPOSITIVO', 'ALTA',    'Varias solicitudes desde el mismo dispositivo',               'ASIGNADA',    'abeltran@ceiba.example',  '2026-10-04 15:21-06'),
  ('6205', 'RITMO_ATIPICO',               'MEDIA',   'Ritmo de escritura atípico durante el formulario',            'SIN_ASIGNAR', NULL,                      '2026-10-04 10:03-06'),
  ('5871', 'MONTO_CERCA_UMBRAL',          'MEDIA',   'Monto declarado muy cercano al umbral de riesgo',             'ASIGNADA',    'kmolina@ceiba.example',   '2026-10-03 16:48-06'),
  ('2094', 'CAMBIO_TELEFONO',             'BAJA',    'Cambio de teléfono de contacto durante la solicitud',         'SIN_ASIGNAR', NULL,                      '2026-10-03 19:30-06'),
  ('8467', 'ACCESO_FUERA_HORARIO',        'BAJA',    'Intento de acceso fuera del horario habitual',                'ASIGNADA',    'kmolina@ceiba.example',   '2026-10-03 13:12-06')
) AS v(ultimos4, tipo, criticidad, motivo, estado, email, generada)
JOIN cuenta cu ON cu.ultimos4 = v.ultimos4
LEFT JOIN usuario_consola u ON u.email = v.email;

-- Historial: cada alerta asignada pasó por SIN_ASIGNAR → ASIGNADA; la de revisión, además por EN_REVISION.
INSERT INTO alerta_historial (alerta_id, estado_anterior, estado_nuevo, responsable_id, actor_id, ocurrido_en)
SELECT id, 'SIN_ASIGNAR', 'ASIGNADA', responsable_id, responsable_id, asignada_en
FROM alerta WHERE responsable_id IS NOT NULL;

INSERT INTO alerta_historial (alerta_id, estado_anterior, estado_nuevo, responsable_id, actor_id, ocurrido_en)
SELECT id, 'ASIGNADA', 'EN_REVISION', responsable_id, responsable_id, asignada_en + interval '15 minutes'
FROM alerta WHERE estado = 'EN_REVISION';
