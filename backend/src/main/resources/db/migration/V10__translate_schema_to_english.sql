-- =====================================================================
--  Translates the whole schema to English: tables, columns, views, functions,
--  triggers, enum types and their values, constraints, indexes and sequences.
--  Renames keep the data, foreign keys, defaults, triggers and generated columns
--  (PostgreSQL tracks them by OID). Function bodies and views are stored as text,
--  so those are recreated. Earlier migrations stay untouched (Flyway checksums).
--  Catalog codes (SALARIO, HASTA_200, R-01…) and texts shown to users are business
--  data and stay as they are.
-- =====================================================================

-- 1. Objects stored as SQL text and not referenced by other objects: recreated at the end.
DROP VIEW v_solicitud_listado;
DROP VIEW v_solicitud_senales;
DROP VIEW v_bandeja_alertas;
DROP FUNCTION evaluar_riesgo(uuid);
DROP FUNCTION siguiente_numero_solicitud(timestamptz);
DROP FUNCTION tomar_alerta(uuid, uuid);

-- 2. Enum types and values. Stored rows, CHECK constraints and partial indexes follow automatically.
ALTER TYPE estado_solicitud RENAME TO request_status;
ALTER TYPE request_status RENAME VALUE 'EN_PROGRESO' TO 'IN_PROGRESS';
ALTER TYPE request_status RENAME VALUE 'COMPLETADA' TO 'COMPLETED';
ALTER TYPE request_status RENAME VALUE 'ABANDONADA' TO 'ABANDONED';
ALTER TYPE nivel_riesgo RENAME TO risk_level;
ALTER TYPE risk_level RENAME VALUE 'SIN_EVALUAR' TO 'NOT_EVALUATED';
ALTER TYPE risk_level RENAME VALUE 'BAJO' TO 'LOW';
ALTER TYPE risk_level RENAME VALUE 'PENDIENTE_EVALUACION' TO 'PENDING_REVIEW';
ALTER TYPE risk_level RENAME VALUE 'MEDIO' TO 'MEDIUM';
ALTER TYPE risk_level RENAME VALUE 'ALTO' TO 'HIGH';
ALTER TYPE paso_onboarding RENAME TO onboarding_step;
ALTER TYPE onboarding_step RENAME VALUE 'AVISO_PRIVACIDAD' TO 'PRIVACY_NOTICE';
ALTER TYPE onboarding_step RENAME VALUE 'DATOS_BASICOS' TO 'BASIC_DATA';
ALTER TYPE onboarding_step RENAME VALUE 'INGRESOS' TO 'INCOME';
ALTER TYPE onboarding_step RENAME VALUE 'MOVIMIENTO_ESPERADO' TO 'EXPECTED_ACTIVITY';
ALTER TYPE onboarding_step RENAME VALUE 'REVISION' TO 'REVIEW';
ALTER TYPE tipo_evento_sol RENAME TO request_event_type;
ALTER TYPE request_event_type RENAME VALUE 'SOLICITUD_INICIADA' TO 'REQUEST_STARTED';
ALTER TYPE request_event_type RENAME VALUE 'PRIVACIDAD_ACEPTADA' TO 'PRIVACY_ACCEPTED';
ALTER TYPE request_event_type RENAME VALUE 'DATOS_BASICOS_COMPLETADOS' TO 'BASIC_DATA_COMPLETED';
ALTER TYPE request_event_type RENAME VALUE 'INGRESOS_REGISTRADOS' TO 'INCOME_REGISTERED';
ALTER TYPE request_event_type RENAME VALUE 'MOVIMIENTO_REGISTRADO' TO 'EXPECTED_ACTIVITY_REGISTERED';
ALTER TYPE request_event_type RENAME VALUE 'SOLICITUD_ENVIADA' TO 'REQUEST_SUBMITTED';
ALTER TYPE request_event_type RENAME VALUE 'ENVIO_FALLIDO' TO 'SUBMISSION_FAILED';
ALTER TYPE request_event_type RENAME VALUE 'SCORE_ASIGNADO' TO 'SCORE_ASSIGNED';
ALTER TYPE request_event_type RENAME VALUE 'SOLICITUD_ABANDONADA' TO 'REQUEST_ABANDONED';
ALTER TYPE criticidad_alerta RENAME TO alert_criticality;
ALTER TYPE alert_criticality RENAME VALUE 'CRITICA' TO 'CRITICAL';
ALTER TYPE alert_criticality RENAME VALUE 'ALTA' TO 'HIGH';
ALTER TYPE alert_criticality RENAME VALUE 'MEDIA' TO 'MEDIUM';
ALTER TYPE alert_criticality RENAME VALUE 'BAJA' TO 'LOW';
ALTER TYPE estado_alerta RENAME TO alert_status;
ALTER TYPE alert_status RENAME VALUE 'SIN_ASIGNAR' TO 'UNASSIGNED';
ALTER TYPE alert_status RENAME VALUE 'ASIGNADA' TO 'ASSIGNED';
ALTER TYPE alert_status RENAME VALUE 'EN_REVISION' TO 'IN_REVIEW';
ALTER TYPE alert_status RENAME VALUE 'CERRADA' TO 'CLOSED';
ALTER TYPE rol_consola RENAME TO console_role;
ALTER TYPE console_role RENAME VALUE 'LIDER_KYC' TO 'KYC_LEAD';
ALTER TYPE console_role RENAME VALUE 'ANALISTA_FRAUDE' TO 'FRAUD_ANALYST';
ALTER TYPE estado_regla RENAME TO rule_status;
ALTER TYPE rule_status RENAME VALUE 'BORRADOR' TO 'DRAFT';
ALTER TYPE rule_status RENAME VALUE 'CONFIRMADA' TO 'CONFIRMED';
ALTER TYPE rule_status RENAME VALUE 'RETIRADA' TO 'RETIRED';
ALTER TYPE ritmo_escritura RENAME TO typing_pace;
ALTER TYPE typing_pace RENAME VALUE 'PAUSADO' TO 'SLOW';
ALTER TYPE typing_pace RENAME VALUE 'RAPIDO' TO 'FAST';
ALTER TYPE estado_cuenta RENAME TO account_status;
ALTER TYPE account_status RENAME VALUE 'ACTIVA' TO 'ACTIVE';
ALTER TYPE account_status RENAME VALUE 'BLOQUEADA' TO 'BLOCKED';
ALTER TYPE account_status RENAME VALUE 'CERRADA' TO 'CLOSED';

-- 3. Tables and columns.
ALTER TABLE cat_origen_ingreso RENAME TO income_source;
ALTER TABLE income_source RENAME COLUMN codigo TO code;
ALTER TABLE income_source RENAME COLUMN etiqueta TO label;
ALTER TABLE income_source RENAME COLUMN orden TO sort_order;
ALTER TABLE income_source RENAME COLUMN activo TO active;
ALTER TABLE cat_rango_ingreso RENAME TO income_range;
ALTER TABLE income_range RENAME COLUMN codigo TO code;
ALTER TABLE income_range RENAME COLUMN etiqueta TO label;
ALTER TABLE income_range RENAME COLUMN orden TO sort_order;
ALTER TABLE income_range RENAME COLUMN activo TO active;
ALTER TABLE cat_tipo_movimiento RENAME TO transaction_type;
ALTER TABLE transaction_type RENAME COLUMN codigo TO code;
ALTER TABLE transaction_type RENAME COLUMN etiqueta TO label;
ALTER TABLE transaction_type RENAME COLUMN orden TO sort_order;
ALTER TABLE transaction_type RENAME COLUMN activo TO active;
ALTER TABLE cat_rango_monto_mensual RENAME TO monthly_amount_range;
ALTER TABLE monthly_amount_range RENAME COLUMN codigo TO code;
ALTER TABLE monthly_amount_range RENAME COLUMN etiqueta TO label;
ALTER TABLE monthly_amount_range RENAME COLUMN orden TO sort_order;
ALTER TABLE monthly_amount_range RENAME COLUMN activo TO active;
ALTER TABLE aviso_privacidad RENAME TO privacy_notice;
ALTER TABLE privacy_notice RENAME COLUMN hash_texto TO text_hash;
ALTER TABLE privacy_notice RENAME COLUMN vigente_desde TO valid_from;
ALTER TABLE privacy_notice RENAME COLUMN vigente_hasta TO valid_to;
ALTER TABLE cliente RENAME TO customer;
ALTER TABLE customer RENAME COLUMN nombres TO first_names;
ALTER TABLE customer RENAME COLUMN apellidos TO last_names;
ALTER TABLE customer RENAME COLUMN celular TO mobile_phone;
ALTER TABLE customer RENAME COLUMN creado_en TO created_at;
ALTER TABLE customer RENAME COLUMN actualizado_en TO updated_at;
ALTER TABLE usuario_consola RENAME TO console_user;
ALTER TABLE console_user RENAME COLUMN nombre TO full_name;
ALTER TABLE console_user RENAME COLUMN cargo TO job_title;
ALTER TABLE console_user RENAME COLUMN iniciales TO initials;
ALTER TABLE console_user RENAME COLUMN rol TO role;
ALTER TABLE console_user RENAME COLUMN activo TO active;
ALTER TABLE console_user RENAME COLUMN creado_en TO created_at;
ALTER TABLE console_user RENAME COLUMN clave_hash TO password_hash;
ALTER TABLE console_user RENAME COLUMN clave_cambiada_en TO password_changed_at;
ALTER TABLE dispositivo RENAME TO device;
ALTER TABLE device RENAME COLUMN huella TO fingerprint;
ALTER TABLE device RENAME COLUMN modelo TO model;
ALTER TABLE device RENAME COLUMN sistema_op TO operating_system;
ALTER TABLE device RENAME COLUMN primera_vez_en TO first_seen_at;
ALTER TABLE device RENAME COLUMN ultima_vez_en TO last_seen_at;
ALTER TABLE secuencia_solicitud RENAME TO request_number_sequence;
ALTER TABLE request_number_sequence RENAME COLUMN anio TO year;
ALTER TABLE request_number_sequence RENAME COLUMN ultimo TO last_number;
ALTER TABLE solicitud RENAME TO onboarding_request;
ALTER TABLE onboarding_request RENAME COLUMN numero TO number;
ALTER TABLE onboarding_request RENAME COLUMN cliente_id TO customer_id;
ALTER TABLE onboarding_request RENAME COLUMN dispositivo_id TO device_id;
ALTER TABLE onboarding_request RENAME COLUMN estado TO status;
ALTER TABLE onboarding_request RENAME COLUMN etapas_completadas TO completed_steps;
ALTER TABLE onboarding_request RENAME COLUMN nivel_riesgo TO risk_level;
ALTER TABLE onboarding_request RENAME COLUMN nombres TO first_names;
ALTER TABLE onboarding_request RENAME COLUMN apellidos TO last_names;
ALTER TABLE onboarding_request RENAME COLUMN celular TO mobile_phone;
ALTER TABLE onboarding_request RENAME COLUMN canal TO channel;
ALTER TABLE onboarding_request RENAME COLUMN iniciada_en TO started_at;
ALTER TABLE onboarding_request RENAME COLUMN enviada_en TO submitted_at;
ALTER TABLE onboarding_request RENAME COLUMN ultima_actividad_en TO last_activity_at;
ALTER TABLE consentimiento_privacidad RENAME TO privacy_consent;
ALTER TABLE privacy_consent RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE privacy_consent RENAME COLUMN aviso_id TO notice_id;
ALTER TABLE privacy_consent RENAME COLUMN acepta_senales TO signals_accepted;
ALTER TABLE privacy_consent RENAME COLUMN aceptado_en TO accepted_at;
ALTER TABLE declaracion_ingresos RENAME TO income_declaration;
ALTER TABLE income_declaration RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE income_declaration RENAME COLUMN origen_codigo TO source_code;
ALTER TABLE income_declaration RENAME COLUMN origen_detalle TO source_detail;
ALTER TABLE income_declaration RENAME COLUMN rango_codigo TO range_code;
ALTER TABLE income_declaration RENAME COLUMN registrado_en TO registered_at;
ALTER TABLE income_declaration RENAME COLUMN actualizado_en TO updated_at;
ALTER TABLE movimiento_esperado RENAME TO expected_activity;
ALTER TABLE expected_activity RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE expected_activity RENAME COLUMN tipo_codigo TO transaction_type_code;
ALTER TABLE expected_activity RENAME COLUMN rango_monto_codigo TO monthly_amount_range_code;
ALTER TABLE expected_activity RENAME COLUMN registrado_en TO registered_at;
ALTER TABLE expected_activity RENAME COLUMN actualizado_en TO updated_at;
ALTER TABLE paso_solicitud RENAME TO request_step;
ALTER TABLE request_step RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE request_step RENAME COLUMN paso TO step;
ALTER TABLE request_step RENAME COLUMN iniciado_en TO started_at;
ALTER TABLE request_step RENAME COLUMN completado_en TO completed_at;
ALTER TABLE request_step RENAME COLUMN duracion_seg TO duration_seconds;
ALTER TABLE request_step RENAME COLUMN intentos TO attempts;
ALTER TABLE sesion_onboarding RENAME TO onboarding_session;
ALTER TABLE onboarding_session RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE onboarding_session RENAME COLUMN dispositivo_id TO device_id;
ALTER TABLE onboarding_session RENAME COLUMN ubicacion_aprox TO approximate_location;
ALTER TABLE onboarding_session RENAME COLUMN pais_iso TO country_iso;
ALTER TABLE onboarding_session RENAME COLUMN ritmo_cpm TO typing_speed_cpm;
ALTER TABLE onboarding_session RENAME COLUMN ritmo_categoria TO typing_pace;
ALTER TABLE onboarding_session RENAME COLUMN iniciada_en TO started_at;
ALTER TABLE onboarding_session RENAME COLUMN finalizada_en TO ended_at;
ALTER TABLE evento_solicitud RENAME TO request_event;
ALTER TABLE request_event RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE request_event RENAME COLUMN tipo TO type;
ALTER TABLE request_event RENAME COLUMN descripcion TO description;
ALTER TABLE request_event RENAME COLUMN datos TO data;
ALTER TABLE request_event RENAME COLUMN ocurrido_en TO occurred_at;
ALTER TABLE regla_score RENAME TO score_rule;
ALTER TABLE score_rule RENAME COLUMN codigo TO code;
ALTER TABLE score_rule RENAME COLUMN version TO rule_version;
ALTER TABLE score_rule RENAME COLUMN nombre TO name;
ALTER TABLE score_rule RENAME COLUMN descripcion TO description;
ALTER TABLE score_rule RENAME COLUMN campo_evaluado TO evaluated_field;
ALTER TABLE score_rule RENAME COLUMN operador TO operator;
ALTER TABLE score_rule RENAME COLUMN umbral TO threshold;
ALTER TABLE score_rule RENAME COLUMN moneda TO currency;
ALTER TABLE score_rule RENAME COLUMN resultado_si TO result_if_matched;
ALTER TABLE score_rule RENAME COLUMN resultado_no TO result_if_not_matched;
ALTER TABLE score_rule RENAME COLUMN prioridad TO priority;
ALTER TABLE score_rule RENAME COLUMN estado TO status;
ALTER TABLE score_rule RENAME COLUMN nota_estado TO status_note;
ALTER TABLE score_rule RENAME COLUMN vigente_desde TO valid_from;
ALTER TABLE score_rule RENAME COLUMN vigente_hasta TO valid_to;
ALTER TABLE score_rule RENAME COLUMN actualizado_en TO updated_at;
ALTER TABLE score_rule RENAME COLUMN actualizado_por TO updated_by;
ALTER TABLE evaluacion_riesgo RENAME TO risk_assessment;
ALTER TABLE risk_assessment RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE risk_assessment RENAME COLUMN regla_id TO rule_id;
ALTER TABLE risk_assessment RENAME COLUMN nivel TO level;
ALTER TABLE risk_assessment RENAME COLUMN valor_evaluado TO evaluated_value;
ALTER TABLE risk_assessment RENAME COLUMN explicacion TO explanation;
ALTER TABLE risk_assessment RENAME COLUMN evaluado_en TO evaluated_at;
ALTER TABLE risk_assessment RENAME COLUMN evaluado_por TO evaluated_by;
ALTER TABLE risk_assessment RENAME COLUMN es_vigente TO is_current;
ALTER TABLE cuenta RENAME TO account;
ALTER TABLE account RENAME COLUMN cliente_id TO customer_id;
ALTER TABLE account RENAME COLUMN solicitud_id TO request_id;
ALTER TABLE account RENAME COLUMN numero_token TO number_token;
ALTER TABLE account RENAME COLUMN ultimos4 TO last_four;
ALTER TABLE account RENAME COLUMN estado TO status;
ALTER TABLE account RENAME COLUMN abierta_en TO opened_at;
ALTER TABLE tipo_alerta RENAME TO alert_type;
ALTER TABLE alert_type RENAME COLUMN codigo TO code;
ALTER TABLE alert_type RENAME COLUMN descripcion TO description;
ALTER TABLE alert_type RENAME COLUMN criticidad_default TO default_criticality;
ALTER TABLE alerta RENAME TO alert;
ALTER TABLE alert RENAME COLUMN cuenta_id TO account_id;
ALTER TABLE alert RENAME COLUMN tipo_codigo TO type_code;
ALTER TABLE alert RENAME COLUMN criticidad TO criticality;
ALTER TABLE alert RENAME COLUMN severidad TO severity;
ALTER TABLE alert RENAME COLUMN motivo TO reason;
ALTER TABLE alert RENAME COLUMN evidencia TO evidence;
ALTER TABLE alert RENAME COLUMN estado TO status;
ALTER TABLE alert RENAME COLUMN responsable_id TO assignee_id;
ALTER TABLE alert RENAME COLUMN generada_en TO raised_at;
ALTER TABLE alert RENAME COLUMN asignada_en TO assigned_at;
ALTER TABLE alert RENAME COLUMN cerrada_en TO closed_at;
ALTER TABLE alert RENAME COLUMN resolucion TO resolution;
ALTER TABLE alerta_historial RENAME TO alert_history;
ALTER TABLE alert_history RENAME COLUMN alerta_id TO alert_id;
ALTER TABLE alert_history RENAME COLUMN estado_anterior TO previous_status;
ALTER TABLE alert_history RENAME COLUMN estado_nuevo TO new_status;
ALTER TABLE alert_history RENAME COLUMN responsable_id TO assignee_id;
ALTER TABLE alert_history RENAME COLUMN comentario TO comment;
ALTER TABLE alert_history RENAME COLUMN ocurrido_en TO occurred_at;
ALTER TABLE auditoria_acceso RENAME TO access_audit;
ALTER TABLE access_audit RENAME COLUMN usuario_id TO user_id;
ALTER TABLE access_audit RENAME COLUMN accion TO action;
ALTER TABLE access_audit RENAME COLUMN entidad TO entity_name;
ALTER TABLE access_audit RENAME COLUMN entidad_id TO entity_id;
ALTER TABLE access_audit RENAME COLUMN ocurrido_en TO occurred_at;

-- 4. Constraints, indexes, sequences and triggers.
ALTER TABLE alert RENAME CONSTRAINT alerta_cuenta_id_fkey TO alert_account_id_fkey;
ALTER TABLE alert RENAME CONSTRAINT alerta_pkey TO alert_pkey;
ALTER TABLE alert RENAME CONSTRAINT alerta_responsable_id_fkey TO alert_assignee_id_fkey;
ALTER TABLE alert RENAME CONSTRAINT alerta_tipo_codigo_fkey TO alert_type_code_fkey;
ALTER TABLE alert RENAME CONSTRAINT ck_alerta_responsable TO ck_alert_assignee;
ALTER TABLE alert_history RENAME CONSTRAINT alerta_historial_actor_id_fkey TO alert_history_actor_id_fkey;
ALTER TABLE alert_history RENAME CONSTRAINT alerta_historial_alerta_id_fkey TO alert_history_alert_id_fkey;
ALTER TABLE alert_history RENAME CONSTRAINT alerta_historial_pkey TO alert_history_pkey;
ALTER TABLE alert_history RENAME CONSTRAINT alerta_historial_responsable_id_fkey TO alert_history_assignee_id_fkey;
ALTER TABLE access_audit RENAME CONSTRAINT auditoria_acceso_pkey TO access_audit_pkey;
ALTER TABLE access_audit RENAME CONSTRAINT auditoria_acceso_usuario_id_fkey TO access_audit_user_id_fkey;
ALTER TABLE privacy_notice RENAME CONSTRAINT aviso_privacidad_pkey TO privacy_notice_pkey;
ALTER TABLE privacy_notice RENAME CONSTRAINT aviso_privacidad_version_key TO privacy_notice_version_key;
ALTER TABLE income_source RENAME CONSTRAINT cat_origen_ingreso_pkey TO income_source_pkey;
ALTER TABLE income_range RENAME CONSTRAINT cat_rango_ingreso_pkey TO income_range_pkey;
ALTER TABLE income_range RENAME CONSTRAINT ck_rango TO ck_income_range_bounds;
ALTER TABLE monthly_amount_range RENAME CONSTRAINT cat_rango_monto_mensual_pkey TO monthly_amount_range_pkey;
ALTER TABLE monthly_amount_range RENAME CONSTRAINT ck_rango_monto TO ck_monthly_amount_range_bounds;
ALTER TABLE transaction_type RENAME CONSTRAINT cat_tipo_movimiento_pkey TO transaction_type_pkey;
ALTER TABLE customer RENAME CONSTRAINT ck_cliente_celular TO ck_customer_mobile_phone;
ALTER TABLE customer RENAME CONSTRAINT ck_cliente_dui TO ck_customer_dui;
ALTER TABLE customer RENAME CONSTRAINT cliente_pkey TO customer_pkey;
ALTER TABLE customer RENAME CONSTRAINT uq_cliente_dui TO uq_customer_dui;
ALTER TABLE privacy_consent RENAME CONSTRAINT ck_consent TO ck_privacy_consent_accepted;
ALTER TABLE privacy_consent RENAME CONSTRAINT consentimiento_privacidad_aviso_id_fkey TO privacy_consent_notice_id_fkey;
ALTER TABLE privacy_consent RENAME CONSTRAINT consentimiento_privacidad_pkey TO privacy_consent_pkey;
ALTER TABLE privacy_consent RENAME CONSTRAINT consentimiento_privacidad_solicitud_id_fkey TO privacy_consent_request_id_fkey;
ALTER TABLE account RENAME CONSTRAINT cuenta_cliente_id_fkey TO account_customer_id_fkey;
ALTER TABLE account RENAME CONSTRAINT cuenta_numero_token_key TO account_number_token_key;
ALTER TABLE account RENAME CONSTRAINT cuenta_pkey TO account_pkey;
ALTER TABLE account RENAME CONSTRAINT cuenta_solicitud_id_fkey TO account_request_id_fkey;
ALTER TABLE account RENAME CONSTRAINT cuenta_solicitud_id_key TO account_request_id_key;
ALTER TABLE account RENAME CONSTRAINT cuenta_ultimos4_check TO account_last_four_check;
ALTER TABLE income_declaration RENAME CONSTRAINT declaracion_ingresos_origen_codigo_fkey TO income_declaration_source_code_fkey;
ALTER TABLE income_declaration RENAME CONSTRAINT declaracion_ingresos_pkey TO income_declaration_pkey;
ALTER TABLE income_declaration RENAME CONSTRAINT declaracion_ingresos_rango_codigo_fkey TO income_declaration_range_code_fkey;
ALTER TABLE income_declaration RENAME CONSTRAINT declaracion_ingresos_solicitud_id_fkey TO income_declaration_request_id_fkey;
ALTER TABLE device RENAME CONSTRAINT dispositivo_huella_key TO device_fingerprint_key;
ALTER TABLE device RENAME CONSTRAINT dispositivo_pkey TO device_pkey;
ALTER TABLE risk_assessment RENAME CONSTRAINT evaluacion_riesgo_evaluado_por_fkey TO risk_assessment_evaluated_by_fkey;
ALTER TABLE risk_assessment RENAME CONSTRAINT evaluacion_riesgo_pkey TO risk_assessment_pkey;
ALTER TABLE risk_assessment RENAME CONSTRAINT evaluacion_riesgo_regla_id_fkey TO risk_assessment_rule_id_fkey;
ALTER TABLE risk_assessment RENAME CONSTRAINT evaluacion_riesgo_solicitud_id_fkey TO risk_assessment_request_id_fkey;
ALTER TABLE request_event RENAME CONSTRAINT evento_solicitud_pkey TO request_event_pkey;
ALTER TABLE request_event RENAME CONSTRAINT evento_solicitud_solicitud_id_fkey TO request_event_request_id_fkey;
ALTER TABLE expected_activity RENAME CONSTRAINT movimiento_esperado_pkey TO expected_activity_pkey;
ALTER TABLE expected_activity RENAME CONSTRAINT movimiento_esperado_rango_monto_codigo_fkey TO expected_activity_monthly_amount_range_code_fkey;
ALTER TABLE expected_activity RENAME CONSTRAINT movimiento_esperado_solicitud_id_fkey TO expected_activity_request_id_fkey;
ALTER TABLE expected_activity RENAME CONSTRAINT movimiento_esperado_tipo_codigo_fkey TO expected_activity_transaction_type_code_fkey;
ALTER TABLE request_step RENAME CONSTRAINT ck_paso_tiempos TO ck_request_step_times;
ALTER TABLE request_step RENAME CONSTRAINT paso_solicitud_pkey TO request_step_pkey;
ALTER TABLE request_step RENAME CONSTRAINT paso_solicitud_solicitud_id_fkey TO request_step_request_id_fkey;
ALTER TABLE score_rule RENAME CONSTRAINT ck_regla_vigencia TO ck_score_rule_validity;
ALTER TABLE score_rule RENAME CONSTRAINT regla_score_actualizado_por_fkey TO score_rule_updated_by_fkey;
ALTER TABLE score_rule RENAME CONSTRAINT regla_score_operador_check TO score_rule_operator_check;
ALTER TABLE score_rule RENAME CONSTRAINT regla_score_pkey TO score_rule_pkey;
ALTER TABLE score_rule RENAME CONSTRAINT uq_regla_version TO uq_score_rule_version;
ALTER TABLE request_number_sequence RENAME CONSTRAINT secuencia_solicitud_pkey TO request_number_sequence_pkey;
ALTER TABLE onboarding_session RENAME CONSTRAINT sesion_onboarding_dispositivo_id_fkey TO onboarding_session_device_id_fkey;
ALTER TABLE onboarding_session RENAME CONSTRAINT sesion_onboarding_pkey TO onboarding_session_pkey;
ALTER TABLE onboarding_session RENAME CONSTRAINT sesion_onboarding_ritmo_cpm_check TO onboarding_session_typing_speed_cpm_check;
ALTER TABLE onboarding_session RENAME CONSTRAINT sesion_onboarding_solicitud_id_fkey TO onboarding_session_request_id_fkey;
ALTER TABLE onboarding_request RENAME CONSTRAINT ck_sol_celular TO ck_request_mobile_phone;
ALTER TABLE onboarding_request RENAME CONSTRAINT ck_sol_completa TO ck_request_completed;
ALTER TABLE onboarding_request RENAME CONSTRAINT ck_sol_datos_basicos TO ck_request_basic_data;
ALTER TABLE onboarding_request RENAME CONSTRAINT ck_sol_dui TO ck_request_dui;
ALTER TABLE onboarding_request RENAME CONSTRAINT ck_sol_etapas TO ck_request_completed_steps;
ALTER TABLE onboarding_request RENAME CONSTRAINT ck_sol_numero TO ck_request_number;
ALTER TABLE onboarding_request RENAME CONSTRAINT solicitud_cliente_id_fkey TO onboarding_request_customer_id_fkey;
ALTER TABLE onboarding_request RENAME CONSTRAINT solicitud_dispositivo_id_fkey TO onboarding_request_device_id_fkey;
ALTER TABLE onboarding_request RENAME CONSTRAINT solicitud_idempotency_key_key TO onboarding_request_idempotency_key_key;
ALTER TABLE onboarding_request RENAME CONSTRAINT solicitud_pkey TO onboarding_request_pkey;
ALTER TABLE onboarding_request RENAME CONSTRAINT uq_solicitud_numero TO uq_request_number;
ALTER TABLE alert_type RENAME CONSTRAINT tipo_alerta_pkey TO alert_type_pkey;
ALTER TABLE console_user RENAME CONSTRAINT usuario_consola_email_key TO console_user_email_key;
ALTER TABLE console_user RENAME CONSTRAINT usuario_consola_pkey TO console_user_pkey;
ALTER INDEX ix_alerta_bandeja RENAME TO ix_alert_inbox;
ALTER INDEX ix_alerta_cuenta RENAME TO ix_alert_account;
ALTER INDEX ix_alerta_responsable RENAME TO ix_alert_assignee;
ALTER INDEX ix_alerta_sin_asignar RENAME TO ix_alert_unassigned;
ALTER INDEX ix_alerta_hist RENAME TO ix_alert_history_alert;
ALTER INDEX ix_auditoria_usuario RENAME TO ix_access_audit_user;
ALTER INDEX ix_cliente_nombre_trgm RENAME TO ix_customer_name_trgm;
ALTER INDEX ix_cuenta_cliente RENAME TO ix_account_customer;
ALTER INDEX ix_cuenta_ultimos4 RENAME TO ix_account_last_four;
ALTER INDEX ix_eval_regla RENAME TO ix_risk_assessment_rule;
ALTER INDEX uq_eval_vigente RENAME TO uq_risk_assessment_current;
ALTER INDEX ix_evento_sol RENAME TO ix_request_event_request;
ALTER INDEX uq_regla_vigente RENAME TO uq_score_rule_in_force;
ALTER INDEX ix_sesion_ip RENAME TO ix_onboarding_session_ip;
ALTER INDEX ix_sesion_solicitud RENAME TO ix_onboarding_session_request;
ALTER INDEX ix_sol_cliente RENAME TO ix_request_customer;
ALTER INDEX ix_sol_dispositivo RENAME TO ix_request_device;
ALTER INDEX ix_sol_estado RENAME TO ix_request_status;
ALTER INDEX ix_sol_listado RENAME TO ix_request_listing;
ALTER INDEX ix_sol_nombre_trgm RENAME TO ix_request_name_trgm;
ALTER SEQUENCE alerta_historial_id_seq RENAME TO alert_history_id_seq;
ALTER SEQUENCE auditoria_acceso_id_seq RENAME TO access_audit_id_seq;
ALTER SEQUENCE aviso_privacidad_id_seq RENAME TO privacy_notice_id_seq;
ALTER SEQUENCE evaluacion_riesgo_id_seq RENAME TO risk_assessment_id_seq;
ALTER SEQUENCE evento_solicitud_id_seq RENAME TO request_event_id_seq;
ALTER SEQUENCE regla_score_id_seq RENAME TO score_rule_id_seq;
ALTER TRIGGER trg_declaracion_ingresos_inmutable ON income_declaration RENAME TO trg_income_declaration_immutable;
ALTER TRIGGER trg_movimiento_esperado_inmutable ON expected_activity RENAME TO trg_expected_activity_immutable;

-- 5. Functions other objects depend on are renamed and replaced in place (same OID), not dropped:
--    alert.severity is a generated column that calls alert_severity, and the triggers call
--    prevent_declaration_change.
ALTER FUNCTION severidad(alert_criticality) RENAME TO alert_severity;
CREATE OR REPLACE FUNCTION alert_severity(c alert_criticality) RETURNS smallint
LANGUAGE sql IMMUTABLE SET search_path = public AS $$
  SELECT CASE c WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END::smallint
$$;

-- Immutable file (VDI-23): a declaration can be neither changed nor deleted once its request leaves IN_PROGRESS.
-- If the request no longer exists (cascade delete) it is allowed.
ALTER FUNCTION bloquear_cambio_de_expediente() RENAME TO prevent_declaration_change;
CREATE OR REPLACE FUNCTION prevent_declaration_change() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
DECLARE v_status request_status;
BEGIN
  SELECT status INTO v_status FROM onboarding_request WHERE id = OLD.request_id;
  IF FOUND AND v_status <> 'IN_PROGRESS' THEN
    RAISE EXCEPTION 'The declaration of request % cannot be modified or deleted (status %)',
      OLD.request_id, v_status USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END $$;

-- 6. Values written by the system.
ALTER TABLE onboarding_request ALTER COLUMN channel SET DEFAULT 'MOBILE_APP';
UPDATE onboarding_request SET channel = 'MOBILE_APP' WHERE channel = 'APP_MOVIL';
ALTER TABLE request_event ALTER COLUMN actor SET DEFAULT 'CUSTOMER';
UPDATE request_event SET actor = 'CUSTOMER' WHERE actor = 'CLIENTE';
UPDATE request_event SET actor = 'SYSTEM' WHERE actor = 'SISTEMA';
UPDATE request_event SET data = (data - 'regla') || jsonb_build_object('rule', data -> 'regla') WHERE data ? 'regla';
UPDATE request_event SET data = (data - 'rango') || jsonb_build_object('range', data -> 'rango') WHERE data ? 'rango';
UPDATE request_event SET data = (data - 'monto') || jsonb_build_object('amount', data -> 'monto') WHERE data ? 'monto';
UPDATE request_event SET data = (data - 'numero') || jsonb_build_object('number', data -> 'numero') WHERE data ? 'numero';
UPDATE score_rule SET evaluated_field = 'expected_activity.monthly_amount_range.max_usd'
 WHERE evaluated_field = 'movimiento_esperado.rango_monto.max_usd';
UPDATE access_audit SET action = 'TAKE_ALERT' WHERE action = 'TOMAR_ALERTA';
UPDATE access_audit SET entity_name = 'alert' WHERE entity_name = 'alerta';

-- 7. Functions.

-- Request number SOL-YYYY-NNNNN: gap-free yearly counter, safe under concurrent submissions.
CREATE FUNCTION next_request_number(p_at timestamptz DEFAULT now())
RETURNS varchar LANGUAGE plpgsql SET search_path = public AS $$
DECLARE v_year smallint := EXTRACT(YEAR FROM p_at AT TIME ZONE 'America/El_Salvador');
        v_n integer;
BEGIN
  INSERT INTO request_number_sequence (year, last_number) VALUES (v_year, 1)
  ON CONFLICT (year) DO UPDATE SET last_number = request_number_sequence.last_number + 1
  RETURNING last_number INTO v_n;
  RETURN 'SOL-' || v_year || '-' || lpad(v_n::text, 5, '0');
END $$;

-- Score engine (same behavior as RiskAssessmentService): R-01 compares the ceiling of the declared monthly
-- amount range with the threshold. A range without ceiling ("More than…") is never low.
CREATE FUNCTION evaluate_risk(p_request uuid)
RETURNS risk_level LANGUAGE plpgsql SET search_path = public AS $$
DECLARE v_range monthly_amount_range; r score_rule; v_level risk_level; v_rule int; v_explanation text; v_value numeric;
BEGIN
  SELECT mr.* INTO v_range
    FROM expected_activity ea JOIN monthly_amount_range mr ON mr.code = ea.monthly_amount_range_code
   WHERE ea.request_id = p_request;
  SELECT * INTO r FROM score_rule
   WHERE code = 'R-01' AND valid_to IS NULL AND status IN ('PROVISIONAL', 'CONFIRMED');

  v_value := coalesce(v_range.max_usd, v_range.min_usd);
  IF v_range.code IS NULL THEN
    v_level := 'NOT_EVALUATED';
    v_explanation := 'La solicitud sigue en progreso: todavía no hay un monto declarado que evaluar.';
  ELSIF r.id IS NOT NULL AND v_range.max_usd IS NOT NULL AND v_range.max_usd <= r.threshold THEN
    v_level := r.result_if_matched; v_rule := r.id;
    v_explanation := format('Monto mensual declarado en el rango «%s», que no supera el umbral de USD %s, riesgo bajo.',
                            v_range.label, to_char(r.threshold, 'FM999,999,990'));
  ELSE
    v_level := coalesce(r.result_if_not_matched, 'PENDING_REVIEW');
    v_explanation := format('Monto mensual declarado en el rango «%s», que supera el umbral de USD %s. '
                            'La regla vigente solo asigna riesgo bajo, así que este caso queda pendiente de evaluación.',
                            v_range.label, to_char(coalesce(r.threshold, 500), 'FM999,999,990'));
  END IF;

  UPDATE risk_assessment SET is_current = false WHERE request_id = p_request AND is_current;
  INSERT INTO risk_assessment (request_id, rule_id, level, evaluated_value, explanation)
  VALUES (p_request, v_rule, v_level, v_value, v_explanation);
  UPDATE onboarding_request SET risk_level = v_level WHERE id = p_request;

  IF v_rule IS NOT NULL THEN
    INSERT INTO request_event (request_id, type, description, actor, data)
    VALUES (p_request, 'SCORE_ASSIGNED', 'Score de riesgo asignado: bajo (regla R-01)', 'SYSTEM',
            jsonb_build_object('rule', 'R-01', 'range', v_range.code));
  END IF;
  RETURN v_level;
END $$;

-- Atomic "take": two analysts cannot take the same alert.
CREATE FUNCTION take_alert(p_alert uuid, p_user uuid)
RETURNS boolean LANGUAGE plpgsql SET search_path = public AS $$
DECLARE v_ok int;
BEGIN
  UPDATE alert
     SET status = 'ASSIGNED', assignee_id = p_user, assigned_at = now(), version = version + 1
   WHERE id = p_alert AND status = 'UNASSIGNED';
  GET DIAGNOSTICS v_ok = ROW_COUNT;
  IF v_ok = 1 THEN
    INSERT INTO alert_history (alert_id, previous_status, new_status, assignee_id, actor_id)
    VALUES (p_alert, 'UNASSIGNED', 'ASSIGNED', p_user, p_user);
  END IF;
  RETURN v_ok = 1;
END $$;

-- 8. Console views.
CREATE VIEW v_request_list AS
SELECT r.id, r.number,
       trim(coalesce(r.first_names, '') || ' ' || coalesce(r.last_names, '')) AS name,
       coalesce(r.submitted_at, r.last_activity_at) AS activity_date,
       tt.label AS transaction_type_label, mr.label AS monthly_amount_range_label,
       r.risk_level, r.status, r.completed_steps
FROM onboarding_request r
LEFT JOIN expected_activity ea ON ea.request_id = r.id
LEFT JOIN transaction_type tt ON tt.code = ea.transaction_type_code
LEFT JOIN monthly_amount_range mr ON mr.code = ea.monthly_amount_range_code;

CREATE VIEW v_request_signals AS
SELECT r.id AS request_id, s.ip, s.approximate_location, d.fingerprint AS device_fingerprint,
       d.model || ' · ' || d.operating_system AS device,
       s.typing_speed_cpm, s.typing_pace,
       r.started_at, r.submitted_at,
       -- night time: 21:00–04:59 local time (same rule as the console front end)
       (r.submitted_at IS NOT NULL AND (
          EXTRACT(HOUR FROM r.submitted_at AT TIME ZONE 'America/El_Salvador') >= 21 OR
          EXTRACT(HOUR FROM r.started_at   AT TIME ZONE 'America/El_Salvador') < 5)) AS night_time,
       (SELECT sum(duration_seconds) FROM request_step st WHERE st.request_id = r.id) AS total_duration_seconds,
       (SELECT count(DISTINCT r2.id) FROM onboarding_request r2 WHERE r2.device_id = r.device_id) AS requests_from_same_device
FROM onboarding_request r
LEFT JOIN device d ON d.id = r.device_id
LEFT JOIN LATERAL (SELECT * FROM onboarding_session x WHERE x.request_id = r.id
                   ORDER BY x.started_at DESC LIMIT 1) s ON true;

CREATE VIEW v_alert_inbox AS
SELECT a.id, a.criticality, a.severity, '•••• ' || ac.last_four AS masked_account, ac.last_four,
       a.reason, a.status, u.full_name AS assignee_name, a.assignee_id, a.raised_at
FROM alert a
JOIN account ac ON ac.id = a.account_id
LEFT JOIN console_user u ON u.id = a.assignee_id
WHERE a.status <> 'CLOSED';
