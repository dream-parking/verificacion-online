-- =====================================================================
--  CEIBA · Onboarding digital + Consola KYC/AML · esquema relacional v0.1
--  Flyway ya envuelve la migración en una transacción (sin BEGIN/COMMIT).
--  gen_random_uuid() es nativo desde PG13, así que no se necesita pgcrypto.
--  pg_trgm debe estar permitido en Azure (parámetro azure.extensions).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- búsqueda por nombre (ILIKE '%...%')

CREATE SCHEMA IF NOT EXISTS ceiba;
-- LOCAL: no dejar el search_path cambiado en la conexión del pool.
SET LOCAL search_path TO ceiba, public;


-- ---------------------------------------------------------------------
-- 1. ENUMS (estados y clasificaciones estables)
-- ---------------------------------------------------------------------
CREATE TYPE estado_solicitud AS ENUM ('EN_PROGRESO', 'COMPLETADA', 'ABANDONADA');
CREATE TYPE nivel_riesgo     AS ENUM ('SIN_EVALUAR', 'BAJO', 'PENDIENTE_EVALUACION', 'MEDIO', 'ALTO');
CREATE TYPE paso_onboarding  AS ENUM ('AVISO_PRIVACIDAD', 'DATOS_BASICOS', 'INGRESOS',
                                      'MOVIMIENTO_ESPERADO', 'REVISION');
CREATE TYPE tipo_evento_sol  AS ENUM ('SOLICITUD_INICIADA', 'PRIVACIDAD_ACEPTADA', 'DATOS_BASICOS_COMPLETADOS',
                                      'INGRESOS_REGISTRADOS', 'MOVIMIENTO_REGISTRADO', 'SOLICITUD_ENVIADA',
                                      'ENVIO_FALLIDO', 'SCORE_ASIGNADO', 'SOLICITUD_ABANDONADA');
CREATE TYPE criticidad_alerta AS ENUM ('CRITICA', 'ALTA', 'MEDIA', 'BAJA');
CREATE TYPE estado_alerta     AS ENUM ('SIN_ASIGNAR', 'ASIGNADA', 'EN_REVISION', 'CERRADA');
CREATE TYPE rol_consola       AS ENUM ('LIDER_KYC', 'ANALISTA_FRAUDE', 'ADMIN');
CREATE TYPE estado_regla      AS ENUM ('BORRADOR', 'PROVISIONAL', 'CONFIRMADA', 'RETIRADA');
CREATE TYPE ritmo_escritura   AS ENUM ('PAUSADO', 'NORMAL', 'RAPIDO');
CREATE TYPE estado_cuenta     AS ENUM ('ACTIVA', 'BLOQUEADA', 'CERRADA');

-- Severidad numérica para ordenar la bandeja (Crítica=0 … Baja=3), igual que SEV en el front.
CREATE FUNCTION severidad(c criticidad_alerta) RETURNS smallint
LANGUAGE sql IMMUTABLE SET search_path = ceiba, public AS $$
  SELECT CASE c WHEN 'CRITICA' THEN 0 WHEN 'ALTA' THEN 1 WHEN 'MEDIA' THEN 2 ELSE 3 END::smallint
$$;

-- ---------------------------------------------------------------------
-- 2. CATÁLOGOS (listas de negocio que cambian sin desplegar código)
-- ---------------------------------------------------------------------
CREATE TABLE cat_origen_ingreso (
  codigo       varchar(30) PRIMARY KEY,
  etiqueta     varchar(80) NOT NULL,
  orden        smallint    NOT NULL DEFAULT 0,
  activo       boolean     NOT NULL DEFAULT true
);

CREATE TABLE cat_rango_ingreso (
  codigo       varchar(30) PRIMARY KEY,
  etiqueta     varchar(80) NOT NULL,
  min_usd      numeric(14,2),
  max_usd      numeric(14,2),
  orden        smallint    NOT NULL DEFAULT 0,
  activo       boolean     NOT NULL DEFAULT true,
  CONSTRAINT ck_rango CHECK (min_usd IS NULL OR max_usd IS NULL OR min_usd < max_usd)
);

CREATE TABLE cat_tipo_movimiento (
  codigo       varchar(30) PRIMARY KEY,
  etiqueta     varchar(80) NOT NULL,
  orden        smallint    NOT NULL DEFAULT 0,
  activo       boolean     NOT NULL DEFAULT true
);

CREATE TABLE aviso_privacidad (
  id           smallserial PRIMARY KEY,
  version      varchar(20)  NOT NULL UNIQUE,      -- ej. '2026.1'
  hash_texto   char(64)     NOT NULL,             -- SHA-256 del texto mostrado
  vigente_desde timestamptz NOT NULL,
  vigente_hasta timestamptz
);

-- ---------------------------------------------------------------------
-- 3. PERSONAS Y CONSOLA
-- ---------------------------------------------------------------------
CREATE TABLE cliente (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  dui            char(10)     NOT NULL,           -- 00000000-0  (ver nota PII en README)
  nombres        varchar(100) NOT NULL,
  apellidos      varchar(100) NOT NULL,
  celular        char(9)      NOT NULL,           -- 0000-0000
  creado_en      timestamptz  NOT NULL DEFAULT now(),
  actualizado_en timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uq_cliente_dui     UNIQUE (dui),
  CONSTRAINT ck_cliente_dui     CHECK (dui ~ '^[0-9]{8}-[0-9]$'),
  CONSTRAINT ck_cliente_celular CHECK (celular ~ '^[67][0-9]{3}-[0-9]{4}$')
);
CREATE INDEX ix_cliente_nombre_trgm
  ON cliente USING gin ((nombres || ' ' || apellidos) gin_trgm_ops);

CREATE TABLE usuario_consola (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email          varchar(150) NOT NULL UNIQUE,     -- correo institucional (login)
  nombre         varchar(150) NOT NULL,
  cargo          varchar(120) NOT NULL,
  iniciales      varchar(3)   NOT NULL,
  rol            rol_consola  NOT NULL,
  activo         boolean      NOT NULL DEFAULT true,
  creado_en      timestamptz  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 4. DISPOSITIVO (huella reutilizable entre solicitudes → detección de
--    "varias solicitudes desde el mismo dispositivo")
-- ---------------------------------------------------------------------
CREATE TABLE dispositivo (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  huella         varchar(64)  NOT NULL UNIQUE,     -- 'd4f1·9a3c·e7b2' (hash normalizado)
  modelo         varchar(80),                      -- 'iPhone 15'
  sistema_op     varchar(30),                      -- 'iOS' | 'Android'
  primera_vez_en timestamptz  NOT NULL DEFAULT now(),
  ultima_vez_en  timestamptz  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 5. SOLICITUD DE ONBOARDING (agregado raíz)
-- ---------------------------------------------------------------------
CREATE TABLE secuencia_solicitud (                 -- correlativo por año: SOL-2026-00418
  anio           smallint PRIMARY KEY,
  ultimo         integer  NOT NULL DEFAULT 0
);

CREATE TABLE solicitud (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  numero               varchar(14),                -- NULL hasta el envío ("Sin número aún")
  cliente_id           uuid REFERENCES cliente(id),-- NULL antes de Datos básicos
  dispositivo_id       uuid REFERENCES dispositivo(id),
  estado               estado_solicitud NOT NULL DEFAULT 'EN_PROGRESO',
  etapas_completadas   smallint     NOT NULL DEFAULT 0,   -- r.c (0..5)
  nivel_riesgo         nivel_riesgo NOT NULL DEFAULT 'SIN_EVALUAR', -- vista materializada de la última evaluación
  -- Snapshot de datos básicos tal como se declararon (expediente KYC)
  nombres              varchar(100),
  apellidos            varchar(100),
  dui                  char(10),
  celular              char(9),
  canal                varchar(20)  NOT NULL DEFAULT 'APP_MOVIL',
  iniciada_en          timestamptz  NOT NULL DEFAULT now(),
  enviada_en           timestamptz,
  ultima_actividad_en  timestamptz  NOT NULL DEFAULT now(),
  idempotency_key      uuid UNIQUE,                -- reintentos de envío sin duplicar
  version              integer      NOT NULL DEFAULT 0,   -- bloqueo optimista
  CONSTRAINT uq_solicitud_numero UNIQUE (numero),
  CONSTRAINT ck_sol_numero   CHECK (numero IS NULL OR numero ~ '^SOL-[0-9]{4}-[0-9]{5}$'),
  CONSTRAINT ck_sol_etapas   CHECK (etapas_completadas BETWEEN 0 AND 5),
  CONSTRAINT ck_sol_dui      CHECK (dui IS NULL OR dui ~ '^[0-9]{8}-[0-9]$'),
  CONSTRAINT ck_sol_celular  CHECK (celular IS NULL OR celular ~ '^[67][0-9]{3}-[0-9]{4}$'),
  -- Completada ⇔ 5 etapas, número y fecha de envío
  CONSTRAINT ck_sol_completa CHECK (
    (estado = 'COMPLETADA' AND etapas_completadas = 5 AND numero IS NOT NULL AND enviada_en IS NOT NULL)
    OR (estado <> 'COMPLETADA' AND numero IS NULL AND enviada_en IS NULL)
  ),
  CONSTRAINT ck_sol_datos_basicos CHECK (
    etapas_completadas < 2 OR (cliente_id IS NOT NULL AND dui IS NOT NULL AND nombres IS NOT NULL)
  )
);
CREATE INDEX ix_sol_listado     ON solicitud (iniciada_en DESC);
CREATE INDEX ix_sol_estado      ON solicitud (estado, nivel_riesgo);
CREATE INDEX ix_sol_cliente     ON solicitud (cliente_id);
CREATE INDEX ix_sol_dispositivo ON solicitud (dispositivo_id);
CREATE INDEX ix_sol_nombre_trgm ON solicitud USING gin ((coalesce(nombres,'') || ' ' || coalesce(apellidos,'')) gin_trgm_ops);

-- Consentimiento (paso "Aviso de privacidad"), 1:1 con solicitud
CREATE TABLE consentimiento_privacidad (
  solicitud_id   uuid PRIMARY KEY REFERENCES solicitud(id) ON DELETE CASCADE,
  aviso_id       smallint     NOT NULL REFERENCES aviso_privacidad(id),
  acepta_senales boolean      NOT NULL,            -- IP, dispositivo, tiempos, ritmo
  aceptado_en    timestamptz  NOT NULL DEFAULT now(),
  ip             inet         NOT NULL,
  CONSTRAINT ck_consent CHECK (acepta_senales)     -- aceptación obligatoria
);

-- Ingresos (Paso 2/4), 1:1
CREATE TABLE declaracion_ingresos (
  solicitud_id   uuid PRIMARY KEY REFERENCES solicitud(id) ON DELETE CASCADE,
  origen_codigo  varchar(30)  NOT NULL REFERENCES cat_origen_ingreso(codigo),
  origen_detalle varchar(150),                     -- obligatorio si origen = OTRO (validar en app)
  rango_codigo   varchar(30)  NOT NULL REFERENCES cat_rango_ingreso(codigo),
  registrado_en  timestamptz  NOT NULL DEFAULT now(),
  actualizado_en timestamptz  NOT NULL DEFAULT now()
);

-- Movimiento esperado (Paso 3/4), 1:1 — insumo de la regla R-01
CREATE TABLE movimiento_esperado (
  solicitud_id   uuid PRIMARY KEY REFERENCES solicitud(id) ON DELETE CASCADE,
  tipo_codigo    varchar(30)   NOT NULL REFERENCES cat_tipo_movimiento(codigo),
  monto_mensual_usd numeric(14,2) NOT NULL CHECK (monto_mensual_usd >= 0),
  registrado_en  timestamptz   NOT NULL DEFAULT now(),
  actualizado_en timestamptz   NOT NULL DEFAULT now()
);

-- Tiempo por paso (alimenta "Tiempo por paso" y "Duración total")
CREATE TABLE paso_solicitud (
  solicitud_id   uuid            NOT NULL REFERENCES solicitud(id) ON DELETE CASCADE,
  paso           paso_onboarding NOT NULL,
  iniciado_en    timestamptz     NOT NULL,
  completado_en  timestamptz,
  duracion_seg   integer GENERATED ALWAYS AS
                   (CASE WHEN completado_en IS NULL THEN NULL
                         ELSE EXTRACT(EPOCH FROM (completado_en - iniciado_en))::int END) STORED,
  intentos       smallint        NOT NULL DEFAULT 1,
  PRIMARY KEY (solicitud_id, paso),
  CONSTRAINT ck_paso_tiempos CHECK (completado_en IS NULL OR completado_en >= iniciado_en)
);

-- Señales de sesión / comportamiento (una o varias sesiones por solicitud)
CREATE TABLE sesion_onboarding (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  solicitud_id       uuid         NOT NULL REFERENCES solicitud(id) ON DELETE CASCADE,
  dispositivo_id     uuid         NOT NULL REFERENCES dispositivo(id),
  ip                 inet         NOT NULL,
  ubicacion_aprox    varchar(120),                 -- 'San Salvador, El Salvador' (geo-IP)
  pais_iso           char(2),
  user_agent         text,
  app_version        varchar(20),
  ritmo_cpm          smallint CHECK (ritmo_cpm IS NULL OR ritmo_cpm BETWEEN 0 AND 2000),
  ritmo_categoria    ritmo_escritura,
  iniciada_en        timestamptz  NOT NULL DEFAULT now(),
  finalizada_en      timestamptz
);
CREATE INDEX ix_sesion_solicitud ON sesion_onboarding (solicitud_id, iniciada_en);
CREATE INDEX ix_sesion_ip        ON sesion_onboarding (ip);

-- Línea de tiempo (append-only, auditable)
CREATE TABLE evento_solicitud (
  id             bigserial PRIMARY KEY,
  solicitud_id   uuid            NOT NULL REFERENCES solicitud(id) ON DELETE CASCADE,
  tipo           tipo_evento_sol NOT NULL,
  descripcion    varchar(250)    NOT NULL,          -- 'Aviso de privacidad aceptado'
  datos          jsonb           NOT NULL DEFAULT '{}'::jsonb,
  ocurrido_en    timestamptz     NOT NULL DEFAULT now(),
  actor          varchar(60)     NOT NULL DEFAULT 'CLIENTE'  -- CLIENTE | SISTEMA | usuario_consola.id
);
CREATE INDEX ix_evento_sol ON evento_solicitud (solicitud_id, ocurrido_en);

-- ---------------------------------------------------------------------
-- 6. SCORE DE RIESGO (reglas versionadas + evaluaciones)
-- ---------------------------------------------------------------------
CREATE TABLE regla_score (
  id               serial PRIMARY KEY,
  codigo           varchar(10)   NOT NULL,          -- 'R-01'
  version          smallint      NOT NULL DEFAULT 1,
  nombre           varchar(120)  NOT NULL,          -- 'Monto mensual bajo'
  descripcion      text          NOT NULL,
  campo_evaluado   varchar(60)   NOT NULL,          -- 'movimiento_esperado.monto_mensual_usd'
  operador         varchar(4)    NOT NULL CHECK (operador IN ('<','<=','>','>=','=')),
  umbral           numeric(14,2) NOT NULL,
  moneda           char(3)       NOT NULL DEFAULT 'USD',
  resultado_si     nivel_riesgo  NOT NULL,          -- BAJO
  resultado_no     nivel_riesgo  NOT NULL,          -- PENDIENTE_EVALUACION
  prioridad        smallint      NOT NULL DEFAULT 100,
  estado           estado_regla  NOT NULL DEFAULT 'BORRADOR',
  nota_estado      varchar(250),
  vigente_desde    timestamptz   NOT NULL,
  vigente_hasta    timestamptz,
  actualizado_en   timestamptz   NOT NULL DEFAULT now(),
  actualizado_por  uuid REFERENCES usuario_consola(id),
  CONSTRAINT uq_regla_version UNIQUE (codigo, version),
  CONSTRAINT ck_regla_vigencia CHECK (vigente_hasta IS NULL OR vigente_hasta > vigente_desde)
);
-- Solo una versión vigente por código
CREATE UNIQUE INDEX uq_regla_vigente ON regla_score (codigo)
  WHERE vigente_hasta IS NULL AND estado IN ('PROVISIONAL','CONFIRMADA');

CREATE TABLE evaluacion_riesgo (
  id               bigserial PRIMARY KEY,
  solicitud_id     uuid          NOT NULL REFERENCES solicitud(id) ON DELETE CASCADE,
  regla_id         integer       REFERENCES regla_score(id),  -- NULL = "Ninguna regla aplicada"
  nivel            nivel_riesgo  NOT NULL,
  valor_evaluado   numeric(14,2),
  explicacion      text          NOT NULL,
  evaluado_en      timestamptz   NOT NULL DEFAULT now(),
  evaluado_por     uuid REFERENCES usuario_consola(id),        -- NULL = motor automático
  es_vigente       boolean       NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX uq_eval_vigente ON evaluacion_riesgo (solicitud_id) WHERE es_vigente;
CREATE INDEX ix_eval_regla ON evaluacion_riesgo (regla_id);

-- ---------------------------------------------------------------------
-- 7. CUENTAS Y ALERTAS (monitoreo transaccional / fraude)
-- ---------------------------------------------------------------------
CREATE TABLE cuenta (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  cliente_id       uuid          NOT NULL REFERENCES cliente(id),
  solicitud_id     uuid          UNIQUE REFERENCES solicitud(id),  -- solicitud que la originó
  numero_token     varchar(64)   NOT NULL UNIQUE,   -- token del core bancario, nunca el número en claro
  ultimos4         char(4)       NOT NULL CHECK (ultimos4 ~ '^[0-9]{4}$'),  -- '•••• 4821'
  estado           estado_cuenta NOT NULL DEFAULT 'ACTIVA',
  abierta_en       timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_cuenta_ultimos4 ON cuenta (ultimos4);
CREATE INDEX ix_cuenta_cliente  ON cuenta (cliente_id);

CREATE TABLE tipo_alerta (
  codigo           varchar(40) PRIMARY KEY,         -- 'PERFIL_EXCEDIDO', 'MULTI_SOLICITUD_DISPOSITIVO'…
  descripcion      varchar(200) NOT NULL,
  criticidad_default criticidad_alerta NOT NULL
);

CREATE TABLE alerta (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  cuenta_id        uuid              NOT NULL REFERENCES cuenta(id),
  tipo_codigo      varchar(40)       NOT NULL REFERENCES tipo_alerta(codigo),
  criticidad       criticidad_alerta NOT NULL,
  severidad        smallint GENERATED ALWAYS AS (severidad(criticidad)) STORED,
  motivo           varchar(250)      NOT NULL,      -- 'Movimientos 4 veces por encima del perfil declarado'
  evidencia        jsonb             NOT NULL DEFAULT '{}'::jsonb,
  estado           estado_alerta     NOT NULL DEFAULT 'SIN_ASIGNAR',
  responsable_id   uuid REFERENCES usuario_consola(id),
  generada_en      timestamptz       NOT NULL DEFAULT now(),
  asignada_en      timestamptz,
  cerrada_en       timestamptz,
  resolucion       varchar(30),                     -- FALSO_POSITIVO | ESCALADA_ROS | …
  version          integer           NOT NULL DEFAULT 0,
  CONSTRAINT ck_alerta_responsable CHECK (
    (estado = 'SIN_ASIGNAR' AND responsable_id IS NULL)
    OR (estado <> 'SIN_ASIGNAR' AND responsable_id IS NOT NULL)
  )
);
-- Bandeja: orden por criticidad y más reciente primero
CREATE INDEX ix_alerta_bandeja     ON alerta (severidad, generada_en DESC) WHERE estado <> 'CERRADA';
CREATE INDEX ix_alerta_responsable ON alerta (responsable_id, estado);
CREATE INDEX ix_alerta_sin_asignar ON alerta (generada_en DESC) WHERE estado = 'SIN_ASIGNAR';
CREATE INDEX ix_alerta_cuenta      ON alerta (cuenta_id);

CREATE TABLE alerta_historial (
  id               bigserial PRIMARY KEY,
  alerta_id        uuid          NOT NULL REFERENCES alerta(id) ON DELETE CASCADE,
  estado_anterior  estado_alerta,
  estado_nuevo     estado_alerta NOT NULL,
  responsable_id   uuid REFERENCES usuario_consola(id),
  actor_id         uuid REFERENCES usuario_consola(id),   -- NULL = sistema
  comentario       text,
  ocurrido_en      timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_alerta_hist ON alerta_historial (alerta_id, ocurrido_en);

-- Auditoría de accesos a PII desde la consola (requisito KYC)
CREATE TABLE auditoria_acceso (
  id               bigserial PRIMARY KEY,
  usuario_id       uuid         NOT NULL REFERENCES usuario_consola(id),
  accion           varchar(40)  NOT NULL,           -- VER_SOLICITUD, TOMAR_ALERTA, LOGIN…
  entidad          varchar(40)  NOT NULL,
  entidad_id       varchar(64)  NOT NULL,
  ip               inet,
  ocurrido_en      timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_usuario ON auditoria_acceso (usuario_id, ocurrido_en DESC);

-- ---------------------------------------------------------------------
-- 8. LÓGICA DE DOMINIO EN BD (opcional; puede vivir en el servicio)
-- ---------------------------------------------------------------------

-- 8.1 Número de solicitud SOL-AAAA-NNNNN (correlativo anual, sin colisiones)
CREATE FUNCTION siguiente_numero_solicitud(p_fecha timestamptz DEFAULT now())
RETURNS varchar LANGUAGE plpgsql SET search_path = ceiba, public AS $$
DECLARE v_anio smallint := EXTRACT(YEAR FROM p_fecha AT TIME ZONE 'America/El_Salvador');
        v_n integer;
BEGIN
  INSERT INTO secuencia_solicitud (anio, ultimo) VALUES (v_anio, 1)
  ON CONFLICT (anio) DO UPDATE SET ultimo = secuencia_solicitud.ultimo + 1
  RETURNING ultimo INTO v_n;
  RETURN 'SOL-' || v_anio || '-' || lpad(v_n::text, 5, '0');
END $$;

-- 8.2 Motor de score: aplica la regla R-01 vigente (equivalente a riesgoDe())
CREATE FUNCTION evaluar_riesgo(p_solicitud uuid)
RETURNS nivel_riesgo LANGUAGE plpgsql SET search_path = ceiba, public AS $$
DECLARE v_monto numeric; r regla_score; v_nivel nivel_riesgo; v_regla int; v_expl text;
BEGIN
  SELECT monto_mensual_usd INTO v_monto FROM movimiento_esperado WHERE solicitud_id = p_solicitud;
  SELECT * INTO r FROM regla_score
   WHERE codigo = 'R-01' AND vigente_hasta IS NULL AND estado IN ('PROVISIONAL','CONFIRMADA');

  IF v_monto IS NULL THEN
    v_nivel := 'SIN_EVALUAR';
    v_expl  := 'La solicitud sigue en progreso: todavía no hay un monto declarado que evaluar.';
  ELSIF r.id IS NOT NULL AND v_monto < r.umbral THEN
    v_nivel := r.resultado_si; v_regla := r.id;
    v_expl  := format('Monto mensual declarado de USD %s, menor al umbral de USD %s, riesgo bajo.',
                      to_char(v_monto,'FM999,999,990'), to_char(r.umbral,'FM999,999,990'));
  ELSE
    v_nivel := coalesce(r.resultado_no, 'PENDIENTE_EVALUACION');
    v_expl  := format('Monto mensual declarado de USD %s, igual o mayor al umbral de USD %s. '
                      'La regla vigente solo asigna riesgo bajo, así que este caso queda pendiente de evaluación.',
                      to_char(v_monto,'FM999,999,990'), to_char(coalesce(r.umbral, 500),'FM999,999,990'));
  END IF;

  UPDATE evaluacion_riesgo SET es_vigente = false WHERE solicitud_id = p_solicitud AND es_vigente;
  INSERT INTO evaluacion_riesgo (solicitud_id, regla_id, nivel, valor_evaluado, explicacion)
  VALUES (p_solicitud, v_regla, v_nivel, v_monto, v_expl);
  UPDATE solicitud SET nivel_riesgo = v_nivel WHERE id = p_solicitud;

  IF v_regla IS NOT NULL THEN
    INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, actor, datos)
    VALUES (p_solicitud, 'SCORE_ASIGNADO', 'Score de riesgo asignado: bajo (regla R-01)', 'SISTEMA',
            jsonb_build_object('regla', 'R-01', 'monto', v_monto));
  END IF;
  RETURN v_nivel;
END $$;

-- 8.3 "Tomarla": asignación atómica (evita que dos analistas tomen la misma alerta)
CREATE FUNCTION tomar_alerta(p_alerta uuid, p_usuario uuid)
RETURNS boolean LANGUAGE plpgsql SET search_path = ceiba, public AS $$
DECLARE v_ok int;
BEGIN
  UPDATE alerta
     SET estado = 'ASIGNADA', responsable_id = p_usuario, asignada_en = now(), version = version + 1
   WHERE id = p_alerta AND estado = 'SIN_ASIGNAR';
  GET DIAGNOSTICS v_ok = ROW_COUNT;
  IF v_ok = 1 THEN
    INSERT INTO alerta_historial (alerta_id, estado_anterior, estado_nuevo, responsable_id, actor_id)
    VALUES (p_alerta, 'SIN_ASIGNAR', 'ASIGNADA', p_usuario, p_usuario);
  END IF;
  RETURN v_ok = 1;   -- false ⇒ la API responde 409 Conflict
END $$;

-- ---------------------------------------------------------------------
-- 9. VISTAS PARA LA CONSOLA
-- ---------------------------------------------------------------------
CREATE VIEW v_solicitud_listado AS
SELECT s.id, s.numero,
       trim(coalesce(s.nombres,'') || ' ' || coalesce(s.apellidos,'')) AS nombre,
       coalesce(s.enviada_en, s.ultima_actividad_en) AS fecha,
       tm.etiqueta AS tipo_dinero, me.monto_mensual_usd,
       s.nivel_riesgo, s.estado, s.etapas_completadas
FROM solicitud s
LEFT JOIN movimiento_esperado me ON me.solicitud_id = s.id
LEFT JOIN cat_tipo_movimiento tm ON tm.codigo = me.tipo_codigo;

CREATE VIEW v_solicitud_senales AS
SELECT s.id AS solicitud_id, so.ip, so.ubicacion_aprox, d.huella,
       d.modelo || ' · ' || d.sistema_op AS dispositivo,
       so.ritmo_cpm, so.ritmo_categoria,
       s.iniciada_en, s.enviada_en,
       -- horario nocturno: 21:00–04:59 hora local (misma regla que el front)
       (s.enviada_en IS NOT NULL AND (
          EXTRACT(HOUR FROM s.enviada_en  AT TIME ZONE 'America/El_Salvador') >= 21 OR
          EXTRACT(HOUR FROM s.iniciada_en AT TIME ZONE 'America/El_Salvador') < 5)) AS horario_nocturno,
       (SELECT sum(duracion_seg) FROM paso_solicitud p WHERE p.solicitud_id = s.id) AS duracion_total_seg,
       (SELECT count(DISTINCT s2.id) FROM solicitud s2 WHERE s2.dispositivo_id = s.dispositivo_id) AS solicitudes_mismo_dispositivo
FROM solicitud s
LEFT JOIN dispositivo d ON d.id = s.dispositivo_id
LEFT JOIN LATERAL (SELECT * FROM sesion_onboarding x WHERE x.solicitud_id = s.id
                   ORDER BY x.iniciada_en DESC LIMIT 1) so ON true;

CREATE VIEW v_bandeja_alertas AS
SELECT a.id, a.criticidad, a.severidad, '•••• ' || c.ultimos4 AS cuenta, c.ultimos4,
       a.motivo, a.estado, u.nombre AS responsable, a.responsable_id, a.generada_en
FROM alerta a
JOIN cuenta c ON c.id = a.cuenta_id
LEFT JOIN usuario_consola u ON u.id = a.responsable_id
WHERE a.estado <> 'CERRADA';

