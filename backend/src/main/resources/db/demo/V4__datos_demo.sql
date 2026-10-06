-- =====================================================================
--  CEIBA · Datos de demostración (muestra tomada de Consola.dc.html).
--  Solo para local/dev/qa: Prod debe usar
--  SPRING_FLYWAY_LOCATIONS=classpath:db/migration para no cargarlos.
-- =====================================================================
SET LOCAL search_path TO ceiba, public;

INSERT INTO usuario_consola (email, nombre, cargo, iniciales, rol) VALUES
  ('grosales@ceiba.example','Gerardo Rosales','Líder de Conocimiento del Cliente','GR','LIDER_KYC'),
  ('abeltran@ceiba.example','Ana Beltrán','Analista de fraude y cumplimiento','AB','ANALISTA_FRAUDE'),
  ('lbarahona@ceiba.example','Luis Barahona','Analista de fraude y cumplimiento','LB','ANALISTA_FRAUDE'),
  ('kmolina@ceiba.example','Karen Molina','Analista de fraude y cumplimiento','KM','ANALISTA_FRAUDE');

-- ---- Ejemplo completo: SOL-2026-00418 (Marta Alejandra Rivas Cruz, USD 320 → BAJO)
WITH c AS (
  INSERT INTO cliente (dui, nombres, apellidos, celular)
  VALUES ('04812377-5','Marta Alejandra','Rivas Cruz','7845-2310') RETURNING id
), d AS (
  INSERT INTO dispositivo (huella, modelo, sistema_op) VALUES ('d4f1·9a3c·e7b2','iPhone 15','iOS') RETURNING id
)
INSERT INTO solicitud (id, cliente_id, dispositivo_id, nombres, apellidos, dui, celular, etapas_completadas, iniciada_en)
SELECT '00000000-0000-0000-0000-000000000418', c.id, d.id, 'Marta Alejandra','Rivas Cruz','04812377-5','7845-2310', 4,
       '2026-10-05 22:08:03-06'
FROM c, d;

INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip)
VALUES ('00000000-0000-0000-0000-000000000418', 1, true, '2026-10-05 22:09:11-06', '190.5.142.77');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en)
VALUES ('00000000-0000-0000-0000-000000000418','SALARIO','500_1500','2026-10-05 22:11:11-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, monto_mensual_usd, registrado_en)
VALUES ('00000000-0000-0000-0000-000000000418','PAGO_SALARIO',320,'2026-10-05 22:12:14-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, ritmo_cpm, ritmo_categoria, iniciada_en)
SELECT '00000000-0000-0000-0000-000000000418', dispositivo_id, '190.5.142.77','San Salvador, El Salvador','SV',185,'NORMAL', iniciada_en
FROM solicitud WHERE id = '00000000-0000-0000-0000-000000000418';
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES
  ('00000000-0000-0000-0000-000000000418','AVISO_PRIVACIDAD',   '2026-10-05 22:08:03-06','2026-10-05 22:09:11-06'),
  ('00000000-0000-0000-0000-000000000418','DATOS_BASICOS',      '2026-10-05 22:09:11-06','2026-10-05 22:10:23-06'),
  ('00000000-0000-0000-0000-000000000418','INGRESOS',           '2026-10-05 22:10:23-06','2026-10-05 22:11:11-06'),
  ('00000000-0000-0000-0000-000000000418','MOVIMIENTO_ESPERADO','2026-10-05 22:11:11-06','2026-10-05 22:12:14-06'),
  ('00000000-0000-0000-0000-000000000418','REVISION',           '2026-10-05 22:12:14-06','2026-10-05 22:12:55-06');

-- Envío: asigna número, completa y evalúa riesgo
UPDATE solicitud SET numero = 'SOL-2026-00418', estado = 'COMPLETADA', etapas_completadas = 5,
       enviada_en = '2026-10-05 22:12:55-06'
WHERE id = '00000000-0000-0000-0000-000000000418';
INSERT INTO secuencia_solicitud VALUES (2026, 418);
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000418');

-- ---- Ejemplo de alerta: cuenta •••• 4821, Crítica, sin asignar
INSERT INTO cuenta (cliente_id, solicitud_id, numero_token, ultimos4)
SELECT cliente_id, id, 'tok_demo_4821', '4821' FROM solicitud WHERE numero = 'SOL-2026-00418';
INSERT INTO alerta (cuenta_id, tipo_codigo, criticidad, motivo, generada_en)
SELECT id, 'PERFIL_EXCEDIDO', 'CRITICA', 'Movimientos 4 veces por encima del perfil declarado', '2026-10-05 08:12-06'
FROM cuenta WHERE ultimos4 = '4821';
