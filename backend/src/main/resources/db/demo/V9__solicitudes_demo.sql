-- =====================================================================
--  CEIBA · Solicitudes de ejemplo para la consola (espejo de webconsole/src/lib/consola/datos.ts).
--  Completa la solicitud SOL-2026-00418 de V4 con su línea de tiempo y agrega las otras 9, con sus pasos,
--  señales de dispositivo, ingresos, movimiento esperado, score (evaluar_riesgo) y línea de tiempo.
--  Solo dev/qa/local: Prod no carga db/demo. Generado a partir de los datos de la consola.
-- =====================================================================

-- Cliente que aún no existe (los otros 8 los crea V6).
INSERT INTO cliente (dui, nombres, apellidos, celular) VALUES ('05522087-4', 'Sofía Guadalupe', 'Martínez', '6078-2245');

-- ---- SOL-2026-00418: línea de tiempo (V4 solo dejó el evento del score, con la hora de la migración)
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000418', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-05 22:08:03-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000418', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-05 22:09:11-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000418', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-05 22:10:23-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000418', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-05 22:11:11-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000418', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-05 22:12:14-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000418', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-05 22:12:55-06', 'CLIENTE');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-05 22:12:56-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000418' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-05 22:12:56-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000418';

-- ---- SOL-2026-00417 (Roberto Antonio Menjívar, 5 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('b81e·42d0·5fa9', 'Samsung Galaxy A54', 'Android', '2026-10-05 16:35:12-06', '2026-10-05 16:41:03-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000417', (SELECT id FROM dispositivo WHERE huella = 'b81e·42d0·5fa9'), 'EN_PROGRESO', 5, '2026-10-05 16:35:12-06', '2026-10-05 16:41:03-06', (SELECT id FROM cliente WHERE dui = '03377841-2'), 'Roberto Antonio', 'Menjívar', '03377841-2', '7021-5543');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000417', 1, true, '2026-10-05 16:36:34-06', '190.5.88.140');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000417', 'NEGOCIO_PROPIO', '1000_2500', '2026-10-05 16:38:58-06', '2026-10-05 16:38:58-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, rango_monto_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000417', 'COBROS_NEGOCIO', 'MAS_1000', '2026-10-05 16:40:14-06', '2026-10-05 16:40:14-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000417', (SELECT id FROM dispositivo WHERE huella = 'b81e·42d0·5fa9'), '190.5.88.140', 'Santa Ana, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 160, 'NORMAL', '2026-10-05 16:35:12-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000417', 'AVISO_PRIVACIDAD', '2026-10-05 16:35:12-06', '2026-10-05 16:36:34-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000417', 'DATOS_BASICOS', '2026-10-05 16:36:34-06', '2026-10-05 16:38:00-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000417', 'INGRESOS', '2026-10-05 16:38:00-06', '2026-10-05 16:38:58-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000417', 'MOVIMIENTO_ESPERADO', '2026-10-05 16:38:58-06', '2026-10-05 16:40:14-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000417', 'REVISION', '2026-10-05 16:40:14-06', '2026-10-05 16:41:03-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000417', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-05 16:35:12-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000417', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-05 16:36:34-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000417', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-05 16:38:00-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000417', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-05 16:38:58-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000417', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-05 16:40:14-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000417', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-05 16:41:03-06', 'CLIENTE');
UPDATE solicitud SET numero = 'SOL-2026-00417', estado = 'COMPLETADA', etapas_completadas = 5, enviada_en = '2026-10-05 16:41:03-06' WHERE id = '00000000-0000-0000-0000-000000000417';
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000417');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-05 16:41:04-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000417' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-05 16:41:04-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000417';

-- ---- SOL-2026-00415 (Karla Vanessa Hernández, 5 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('a27c·e1b6·90d4', 'iPhone 13', 'iOS', '2026-10-05 11:00:20-06', '2026-10-05 11:04:43-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000415', (SELECT id FROM dispositivo WHERE huella = 'a27c·e1b6·90d4'), 'EN_PROGRESO', 5, '2026-10-05 11:00:20-06', '2026-10-05 11:04:43-06', (SELECT id FROM cliente WHERE dui = '05190462-8'), 'Karla Vanessa', 'Hernández', '05190462-8', '7312-0087');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000415', 1, true, '2026-10-05 11:01:21-06', '190.86.31.9');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000415', 'REMESAS', '500_1000', '2026-10-05 11:03:09-06', '2026-10-05 11:03:09-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, rango_monto_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000415', 'REMESAS', '200_500', '2026-10-05 11:04:06-06', '2026-10-05 11:04:06-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000415', (SELECT id FROM dispositivo WHERE huella = 'a27c·e1b6·90d4'), '190.86.31.9', 'San Miguel, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 150, 'NORMAL', '2026-10-05 11:00:20-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000415', 'AVISO_PRIVACIDAD', '2026-10-05 11:00:20-06', '2026-10-05 11:01:21-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000415', 'DATOS_BASICOS', '2026-10-05 11:01:21-06', '2026-10-05 11:02:26-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000415', 'INGRESOS', '2026-10-05 11:02:26-06', '2026-10-05 11:03:09-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000415', 'MOVIMIENTO_ESPERADO', '2026-10-05 11:03:09-06', '2026-10-05 11:04:06-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000415', 'REVISION', '2026-10-05 11:04:06-06', '2026-10-05 11:04:43-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000415', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-05 11:00:20-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000415', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-05 11:01:21-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000415', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-05 11:02:26-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000415', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-05 11:03:09-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000415', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-05 11:04:06-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000415', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-05 11:04:43-06', 'CLIENTE');
UPDATE solicitud SET numero = 'SOL-2026-00415', estado = 'COMPLETADA', etapas_completadas = 5, enviada_en = '2026-10-05 11:04:43-06' WHERE id = '00000000-0000-0000-0000-000000000415';
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000415');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-05 11:04:44-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000415' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-05 11:04:44-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000415';

-- ---- sin número aún (Josué Mauricio Flores, 3 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('3cc9·f0a1·7e55', 'Xiaomi Redmi Note 12', 'Android', '2026-10-05 09:28:40-06', '2026-10-05 09:32:07-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000414', (SELECT id FROM dispositivo WHERE huella = '3cc9·f0a1·7e55'), 'EN_PROGRESO', 3, '2026-10-05 09:28:40-06', '2026-10-05 09:32:07-06', (SELECT id FROM cliente WHERE dui = '02266518-0'), 'Josué Mauricio', 'Flores', '02266518-0', '6190-3374');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000414', 1, true, '2026-10-05 09:29:55-06', '190.5.201.33');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000414', 'NEGOCIO_PROPIO', '500_1000', '2026-10-05 09:32:07-06', '2026-10-05 09:32:07-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000414', (SELECT id FROM dispositivo WHERE huella = '3cc9·f0a1·7e55'), '190.5.201.33', 'Soyapango, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 140, 'NORMAL', '2026-10-05 09:28:40-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000414', 'AVISO_PRIVACIDAD', '2026-10-05 09:28:40-06', '2026-10-05 09:29:55-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000414', 'DATOS_BASICOS', '2026-10-05 09:29:55-06', '2026-10-05 09:31:14-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000414', 'INGRESOS', '2026-10-05 09:31:14-06', '2026-10-05 09:32:07-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000414', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-05 09:28:40-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000414', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-05 09:29:55-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000414', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-05 09:31:14-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000414', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-05 09:32:07-06', 'CLIENTE');

-- ---- SOL-2026-00412 (Daniela Beatriz Ortiz, 5 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('7d02·b5e8·1c39', 'iPhone 14', 'iOS', '2026-10-04 20:42:30-06', '2026-10-04 20:46:23-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000412', (SELECT id FROM dispositivo WHERE huella = '7d02·b5e8·1c39'), 'EN_PROGRESO', 5, '2026-10-04 20:42:30-06', '2026-10-04 20:46:23-06', (SELECT id FROM cliente WHERE dui = '04905513-3'), 'Daniela Beatriz', 'Ortiz', '04905513-3', '7788-4120');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000412', 1, true, '2026-10-04 20:43:24-06', '190.86.120.64');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000412', 'SALARIO', 'HASTA_500', '2026-10-04 20:45:00-06', '2026-10-04 20:45:00-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, rango_monto_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000412', 'AHORRO', 'HASTA_200', '2026-10-04 20:45:50-06', '2026-10-04 20:45:50-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000412', (SELECT id FROM dispositivo WHERE huella = '7d02·b5e8·1c39'), '190.86.120.64', 'Santa Tecla, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 170, 'NORMAL', '2026-10-04 20:42:30-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000412', 'AVISO_PRIVACIDAD', '2026-10-04 20:42:30-06', '2026-10-04 20:43:24-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000412', 'DATOS_BASICOS', '2026-10-04 20:43:24-06', '2026-10-04 20:44:22-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000412', 'INGRESOS', '2026-10-04 20:44:22-06', '2026-10-04 20:45:00-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000412', 'MOVIMIENTO_ESPERADO', '2026-10-04 20:45:00-06', '2026-10-04 20:45:50-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000412', 'REVISION', '2026-10-04 20:45:50-06', '2026-10-04 20:46:23-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000412', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-04 20:42:30-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000412', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-04 20:43:24-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000412', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-04 20:44:22-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000412', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-04 20:45:00-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000412', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-04 20:45:50-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000412', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-04 20:46:23-06', 'CLIENTE');
UPDATE solicitud SET numero = 'SOL-2026-00412', estado = 'COMPLETADA', etapas_completadas = 5, enviada_en = '2026-10-04 20:46:23-06' WHERE id = '00000000-0000-0000-0000-000000000412';
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000412');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-04 20:46:24-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000412' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-04 20:46:24-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000412';

-- ---- SOL-2026-00410 (Carlos Eduardo Aguilar, 5 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('ee49·0b7a·c613', 'Samsung Galaxy S23', 'Android', '2026-10-04 15:03:50-06', '2026-10-04 15:10:09-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000410', (SELECT id FROM dispositivo WHERE huella = 'ee49·0b7a·c613'), 'EN_PROGRESO', 5, '2026-10-04 15:03:50-06', '2026-10-04 15:10:09-06', (SELECT id FROM cliente WHERE dui = '01833920-6'), 'Carlos Eduardo', 'Aguilar', '01833920-6', '7655-9012');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000410', 1, true, '2026-10-04 15:05:18-06', '190.5.14.200');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000410', 'REMESAS', '1000_2500', '2026-10-04 15:07:54-06', '2026-10-04 15:07:54-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, rango_monto_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000410', 'REMESAS', 'MAS_1000', '2026-10-04 15:09:16-06', '2026-10-04 15:09:16-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000410', (SELECT id FROM dispositivo WHERE huella = 'ee49·0b7a·c613'), '190.5.14.200', 'La Libertad, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 310, 'RAPIDO', '2026-10-04 15:03:50-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000410', 'AVISO_PRIVACIDAD', '2026-10-04 15:03:50-06', '2026-10-04 15:05:18-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000410', 'DATOS_BASICOS', '2026-10-04 15:05:18-06', '2026-10-04 15:06:52-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000410', 'INGRESOS', '2026-10-04 15:06:52-06', '2026-10-04 15:07:54-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000410', 'MOVIMIENTO_ESPERADO', '2026-10-04 15:07:54-06', '2026-10-04 15:09:16-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000410', 'REVISION', '2026-10-04 15:09:16-06', '2026-10-04 15:10:09-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000410', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-04 15:03:50-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000410', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-04 15:05:18-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000410', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-04 15:06:52-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000410', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-04 15:07:54-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000410', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-04 15:09:16-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000410', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-04 15:10:09-06', 'CLIENTE');
UPDATE solicitud SET numero = 'SOL-2026-00410', estado = 'COMPLETADA', etapas_completadas = 5, enviada_en = '2026-10-04 15:10:09-06' WHERE id = '00000000-0000-0000-0000-000000000410';
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000410');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-04 15:10:10-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000410' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-04 15:10:10-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000410';

-- ---- SOL-2026-00409 (Ana Lucía Portillo, 5 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('58a3·d94f·2e01', 'iPhone 15 Pro', 'iOS', '2026-10-03 18:19:40-06', '2026-10-03 18:24:32-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000409', (SELECT id FROM dispositivo WHERE huella = '58a3·d94f·2e01'), 'EN_PROGRESO', 5, '2026-10-03 18:19:40-06', '2026-10-03 18:24:32-06', (SELECT id FROM cliente WHERE dui = '04177305-9'), 'Ana Lucía', 'Portillo', '04177305-9', '7210-6678');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000409', 1, true, '2026-10-03 18:20:48-06', '190.86.77.18');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000409', 'SALARIO', '500_1000', '2026-10-03 18:22:48-06', '2026-10-03 18:22:48-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, rango_monto_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000409', 'PAGO_SALARIO', '500_1000', '2026-10-03 18:23:51-06', '2026-10-03 18:23:51-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000409', (SELECT id FROM dispositivo WHERE huella = '58a3·d94f·2e01'), '190.86.77.18', 'San Salvador, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 190, 'NORMAL', '2026-10-03 18:19:40-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000409', 'AVISO_PRIVACIDAD', '2026-10-03 18:19:40-06', '2026-10-03 18:20:48-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000409', 'DATOS_BASICOS', '2026-10-03 18:20:48-06', '2026-10-03 18:22:00-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000409', 'INGRESOS', '2026-10-03 18:22:00-06', '2026-10-03 18:22:48-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000409', 'MOVIMIENTO_ESPERADO', '2026-10-03 18:22:48-06', '2026-10-03 18:23:51-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000409', 'REVISION', '2026-10-03 18:23:51-06', '2026-10-03 18:24:32-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000409', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-03 18:19:40-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000409', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-03 18:20:48-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000409', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-03 18:22:00-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000409', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-03 18:22:48-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000409', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-03 18:23:51-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000409', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-03 18:24:32-06', 'CLIENTE');
UPDATE solicitud SET numero = 'SOL-2026-00409', estado = 'COMPLETADA', etapas_completadas = 5, enviada_en = '2026-10-03 18:24:32-06' WHERE id = '00000000-0000-0000-0000-000000000409';
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000409');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-03 18:24:33-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000409' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-03 18:24:33-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000409';

-- ---- SOL-2026-00406 (Mauricio Ernesto Villalta, 5 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('c10b·77e2·a8d5', 'Motorola Moto G', 'Android', '2026-10-03 11:57:10-06', '2026-10-03 12:03:58-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000406', (SELECT id FROM dispositivo WHERE huella = 'c10b·77e2·a8d5'), 'EN_PROGRESO', 5, '2026-10-03 11:57:10-06', '2026-10-03 12:03:58-06', (SELECT id FROM cliente WHERE dui = '02984416-1'), 'Mauricio Ernesto', 'Villalta', '02984416-1', '7499-3321');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000406', 1, true, '2026-10-03 11:58:45-06', '190.5.65.31');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000406', 'PENSION', 'HASTA_500', '2026-10-03 12:01:33-06', '2026-10-03 12:01:33-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, rango_monto_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000406', 'AHORRO', 'HASTA_200', '2026-10-03 12:03:01-06', '2026-10-03 12:03:01-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000406', (SELECT id FROM dispositivo WHERE huella = 'c10b·77e2·a8d5'), '190.5.65.31', 'Sonsonate, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 95, 'PAUSADO', '2026-10-03 11:57:10-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000406', 'AVISO_PRIVACIDAD', '2026-10-03 11:57:10-06', '2026-10-03 11:58:45-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000406', 'DATOS_BASICOS', '2026-10-03 11:58:45-06', '2026-10-03 12:00:26-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000406', 'INGRESOS', '2026-10-03 12:00:26-06', '2026-10-03 12:01:33-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000406', 'MOVIMIENTO_ESPERADO', '2026-10-03 12:01:33-06', '2026-10-03 12:03:01-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000406', 'REVISION', '2026-10-03 12:03:01-06', '2026-10-03 12:03:58-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000406', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-03 11:57:10-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000406', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-03 11:58:45-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000406', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-03 12:00:26-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000406', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-03 12:01:33-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000406', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-03 12:03:01-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000406', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-03 12:03:58-06', 'CLIENTE');
UPDATE solicitud SET numero = 'SOL-2026-00406', estado = 'COMPLETADA', etapas_completadas = 5, enviada_en = '2026-10-03 12:03:58-06' WHERE id = '00000000-0000-0000-0000-000000000406';
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000406');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-03 12:03:59-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000406' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-03 12:03:59-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000406';

-- ---- sin número aún (Sofía Guadalupe Martínez, 2 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('91fd·3a60·be27', 'iPhone 12', 'iOS', '2026-10-03 09:12:05-06', '2026-10-03 09:14:25-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000408', (SELECT id FROM dispositivo WHERE huella = '91fd·3a60·be27'), 'EN_PROGRESO', 2, '2026-10-03 09:12:05-06', '2026-10-03 09:14:25-06', (SELECT id FROM cliente WHERE dui = '05522087-4'), 'Sofía Guadalupe', 'Martínez', '05522087-4', '6078-2245');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000408', 1, true, '2026-10-03 09:13:13-06', '190.86.9.250');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000408', (SELECT id FROM dispositivo WHERE huella = '91fd·3a60·be27'), '190.86.9.250', 'Apopa, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 165, 'NORMAL', '2026-10-03 09:12:05-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000408', 'AVISO_PRIVACIDAD', '2026-10-03 09:12:05-06', '2026-10-03 09:13:13-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000408', 'DATOS_BASICOS', '2026-10-03 09:13:13-06', '2026-10-03 09:14:25-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000408', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-03 09:12:05-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000408', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-03 09:13:13-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000408', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-03 09:14:25-06', 'CLIENTE');

-- ---- SOL-2026-00401 (José Armando Quintanilla, 5 de 5 etapas)
INSERT INTO dispositivo (huella, modelo, sistema_op, primera_vez_en, ultima_vez_en) VALUES ('2b6e·f813·4d90', 'Samsung Galaxy A34', 'Android', '2026-10-02 08:36:00-06', '2026-10-02 08:41:21-06');
INSERT INTO solicitud (id, dispositivo_id, estado, etapas_completadas, iniciada_en, ultima_actividad_en, cliente_id, nombres, apellidos, dui, celular) VALUES ('00000000-0000-0000-0000-000000000401', (SELECT id FROM dispositivo WHERE huella = '2b6e·f813·4d90'), 'EN_PROGRESO', 5, '2026-10-02 08:36:00-06', '2026-10-02 08:41:21-06', (SELECT id FROM cliente WHERE dui = '03640258-7'), 'José Armando', 'Quintanilla', '03640258-7', '7933-1450');
INSERT INTO consentimiento_privacidad (solicitud_id, aviso_id, acepta_senales, aceptado_en, ip) VALUES ('00000000-0000-0000-0000-000000000401', 1, true, '2026-10-02 08:37:15-06', '190.5.130.12');
INSERT INTO declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000401', 'NEGOCIO_PROPIO', 'MAS_2500', '2026-10-02 08:39:27-06', '2026-10-02 08:39:27-06');
INSERT INTO movimiento_esperado (solicitud_id, tipo_codigo, rango_monto_codigo, registrado_en, actualizado_en) VALUES ('00000000-0000-0000-0000-000000000401', 'COBROS_NEGOCIO', 'MAS_1000', '2026-10-02 08:40:36-06', '2026-10-02 08:40:36-06');
INSERT INTO sesion_onboarding (solicitud_id, dispositivo_id, ip, ubicacion_aprox, pais_iso, user_agent, app_version, ritmo_cpm, ritmo_categoria, iniciada_en) VALUES ('00000000-0000-0000-0000-000000000401', (SELECT id FROM dispositivo WHERE huella = '2b6e·f813·4d90'), '190.5.130.12', 'Chalatenango, El Salvador', 'SV', 'verificacion-app/1.0', '1.0.0+1', 175, 'NORMAL', '2026-10-02 08:36:00-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000401', 'AVISO_PRIVACIDAD', '2026-10-02 08:36:00-06', '2026-10-02 08:37:15-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000401', 'DATOS_BASICOS', '2026-10-02 08:37:15-06', '2026-10-02 08:38:34-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000401', 'INGRESOS', '2026-10-02 08:38:34-06', '2026-10-02 08:39:27-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000401', 'MOVIMIENTO_ESPERADO', '2026-10-02 08:39:27-06', '2026-10-02 08:40:36-06');
INSERT INTO paso_solicitud (solicitud_id, paso, iniciado_en, completado_en) VALUES ('00000000-0000-0000-0000-000000000401', 'REVISION', '2026-10-02 08:40:36-06', '2026-10-02 08:41:21-06');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000401', 'SOLICITUD_INICIADA', 'Solicitud iniciada', '2026-10-02 08:36:00-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000401', 'PRIVACIDAD_ACEPTADA', 'Aviso de privacidad aceptado', '2026-10-02 08:37:15-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000401', 'DATOS_BASICOS_COMPLETADOS', 'Datos básicos completados', '2026-10-02 08:38:34-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000401', 'INGRESOS_REGISTRADOS', 'Ingresos registrados', '2026-10-02 08:39:27-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000401', 'MOVIMIENTO_REGISTRADO', 'Movimiento esperado registrado', '2026-10-02 08:40:36-06', 'CLIENTE');
INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, ocurrido_en, actor) VALUES ('00000000-0000-0000-0000-000000000401', 'SOLICITUD_ENVIADA', 'Solicitud enviada', '2026-10-02 08:41:21-06', 'CLIENTE');
UPDATE solicitud SET numero = 'SOL-2026-00401', estado = 'COMPLETADA', etapas_completadas = 5, enviada_en = '2026-10-02 08:41:21-06' WHERE id = '00000000-0000-0000-0000-000000000401';
SELECT evaluar_riesgo('00000000-0000-0000-0000-000000000401');
UPDATE evento_solicitud SET ocurrido_en = '2026-10-02 08:41:22-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000401' AND tipo = 'SCORE_ASIGNADO';
UPDATE evaluacion_riesgo SET evaluado_en = '2026-10-02 08:41:22-06' WHERE solicitud_id = '00000000-0000-0000-0000-000000000401';

