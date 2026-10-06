-- =====================================================================
--  CEIBA · Alinea el modelo con los criterios de aceptación de Jira (Sprint 1).
--   VDI-23: rangos de ingreso del instructivo UIF; la declaración no se edita ni borra
--           una vez que la solicitud sale de EN_PROGRESO.
--   VDI-24: el monto mensual esperado se elige de un catálogo de rangos (sin texto libre).
--   VDI-25: la regla R-01 evalúa el rango declarado contra el umbral.
--  Los datos de V3/V4 se migran; en Prod (sin datos) solo cambian los catálogos.
-- =====================================================================

-- ---- 1. Rangos de ingreso mensual (VDI-23, VDI-46)
INSERT INTO cat_rango_ingreso (codigo, etiqueta, min_usd, max_usd, orden) VALUES
  ('HASTA_500', 'Hasta USD 500',          NULL,    500,     1),
  ('500_1000',  'USD 500.01 a 1,000',     500.01,  1000,    2),
  ('1000_2500', 'USD 1,000.01 a 2,500',   1000.01, 2500,    3),
  ('MAS_2500',  'Más de USD 2,500',       2500.01, NULL,    4);

UPDATE declaracion_ingresos SET rango_codigo = CASE rango_codigo
    WHEN 'MENOS_500' THEN 'HASTA_500'
    WHEN '500_1500'  THEN '500_1000'
    WHEN '1500_5000' THEN '1000_2500'
    WHEN 'MAS_5000'  THEN 'MAS_2500'
    ELSE rango_codigo END
WHERE rango_codigo IN ('MENOS_500', '500_1500', '1500_5000', 'MAS_5000');

DELETE FROM cat_rango_ingreso WHERE codigo IN ('MENOS_500', '500_1500', '1500_5000', 'MAS_5000');

-- ---- 2. Rangos de monto mensual esperado (VDI-24, VDI-50)
CREATE TABLE cat_rango_monto_mensual (
  codigo       varchar(30) PRIMARY KEY,
  etiqueta     varchar(80) NOT NULL,
  min_usd      numeric(14,2),                      -- primer monto del rango (NULL = sin piso)
  max_usd      numeric(14,2),                      -- último monto del rango (NULL = sin techo)
  orden        smallint    NOT NULL DEFAULT 0,
  activo       boolean     NOT NULL DEFAULT true,
  CONSTRAINT ck_rango_monto CHECK (min_usd IS NULL OR max_usd IS NULL OR min_usd < max_usd)
);

INSERT INTO cat_rango_monto_mensual (codigo, etiqueta, min_usd, max_usd, orden) VALUES
  ('HASTA_200', 'Hasta USD 200',        NULL,    200,   1),
  ('200_500',   'USD 200.01 a 500',     200.01,  500,   2),
  ('500_1000',  'USD 500.01 a 1,000',   500.01,  1000,  3),
  ('MAS_1000',  'Más de USD 1,000',     1000.01, NULL,  4);

-- ---- 3. movimiento_esperado guarda el rango en lugar del monto libre
ALTER TABLE movimiento_esperado ADD COLUMN rango_monto_codigo varchar(30) REFERENCES cat_rango_monto_mensual(codigo);

UPDATE movimiento_esperado SET rango_monto_codigo = CASE
    WHEN monto_mensual_usd <= 200  THEN 'HASTA_200'
    WHEN monto_mensual_usd <= 500  THEN '200_500'
    WHEN monto_mensual_usd <= 1000 THEN '500_1000'
    ELSE 'MAS_1000' END;

ALTER TABLE movimiento_esperado ALTER COLUMN rango_monto_codigo SET NOT NULL;

DROP VIEW v_solicitud_listado;
ALTER TABLE movimiento_esperado DROP COLUMN monto_mensual_usd;

CREATE VIEW v_solicitud_listado AS
SELECT s.id, s.numero,
       trim(coalesce(s.nombres,'') || ' ' || coalesce(s.apellidos,'')) AS nombre,
       coalesce(s.enviada_en, s.ultima_actividad_en) AS fecha,
       tm.etiqueta AS tipo_dinero, rm.etiqueta AS rango_monto_mensual,
       s.nivel_riesgo, s.estado, s.etapas_completadas
FROM solicitud s
LEFT JOIN movimiento_esperado me ON me.solicitud_id = s.id
LEFT JOIN cat_tipo_movimiento tm ON tm.codigo = me.tipo_codigo
LEFT JOIN cat_rango_monto_mensual rm ON rm.codigo = me.rango_monto_codigo;

-- ---- 4. Regla R-01: el rango declarado no supera el umbral → riesgo bajo
UPDATE regla_score
   SET campo_evaluado = 'movimiento_esperado.rango_monto.max_usd',
       operador       = '<=',
       descripcion    = 'Si el rango de monto mensual declarado no supera USD 500, el riesgo es bajo.',
       actualizado_en = now()
 WHERE codigo = 'R-01' AND version = 1;

-- Motor de score (mismo comportamiento que RiskAssessmentService): evalúa el techo del rango declarado.
-- Un rango sin techo ("Más de…") nunca es bajo.
CREATE OR REPLACE FUNCTION evaluar_riesgo(p_solicitud uuid)
RETURNS nivel_riesgo LANGUAGE plpgsql SET search_path = public AS $$
DECLARE v_rango cat_rango_monto_mensual; r regla_score; v_nivel nivel_riesgo; v_regla int; v_expl text; v_valor numeric;
BEGIN
  SELECT rm.* INTO v_rango
    FROM movimiento_esperado me JOIN cat_rango_monto_mensual rm ON rm.codigo = me.rango_monto_codigo
   WHERE me.solicitud_id = p_solicitud;
  SELECT * INTO r FROM regla_score
   WHERE codigo = 'R-01' AND vigente_hasta IS NULL AND estado IN ('PROVISIONAL','CONFIRMADA');

  v_valor := coalesce(v_rango.max_usd, v_rango.min_usd);
  IF v_rango.codigo IS NULL THEN
    v_nivel := 'SIN_EVALUAR';
    v_expl  := 'La solicitud sigue en progreso: todavía no hay un monto declarado que evaluar.';
  ELSIF r.id IS NOT NULL AND v_rango.max_usd IS NOT NULL AND v_rango.max_usd <= r.umbral THEN
    v_nivel := r.resultado_si; v_regla := r.id;
    v_expl  := format('Monto mensual declarado en el rango «%s», que no supera el umbral de USD %s, riesgo bajo.',
                      v_rango.etiqueta, to_char(r.umbral,'FM999,999,990'));
  ELSE
    v_nivel := coalesce(r.resultado_no, 'PENDIENTE_EVALUACION');
    v_expl  := format('Monto mensual declarado en el rango «%s», que supera el umbral de USD %s. '
                      'La regla vigente solo asigna riesgo bajo, así que este caso queda pendiente de evaluación.',
                      v_rango.etiqueta, to_char(coalesce(r.umbral, 500),'FM999,999,990'));
  END IF;

  UPDATE evaluacion_riesgo SET es_vigente = false WHERE solicitud_id = p_solicitud AND es_vigente;
  INSERT INTO evaluacion_riesgo (solicitud_id, regla_id, nivel, valor_evaluado, explicacion)
  VALUES (p_solicitud, v_regla, v_nivel, v_valor, v_expl);
  UPDATE solicitud SET nivel_riesgo = v_nivel WHERE id = p_solicitud;

  IF v_regla IS NOT NULL THEN
    INSERT INTO evento_solicitud (solicitud_id, tipo, descripcion, actor, datos)
    VALUES (p_solicitud, 'SCORE_ASIGNADO', 'Score de riesgo asignado: bajo (regla R-01)', 'SISTEMA',
            jsonb_build_object('regla', 'R-01', 'rango', v_rango.codigo));
  END IF;
  RETURN v_nivel;
END $$;

-- ---- 5. Expediente inmutable (VDI-23): ni se edita ni se borra una declaración cuando la solicitud
--         ya no está EN_PROGRESO. Si la solicitud ya no existe (borrado en cascada) se permite.
CREATE FUNCTION bloquear_cambio_de_expediente() RETURNS trigger
LANGUAGE plpgsql SET search_path = public AS $$
DECLARE v_estado estado_solicitud;
BEGIN
  SELECT estado INTO v_estado FROM solicitud WHERE id = OLD.solicitud_id;
  IF FOUND AND v_estado <> 'EN_PROGRESO' THEN
    RAISE EXCEPTION 'La declaración de la solicitud % no se puede modificar ni borrar (estado %)',
      OLD.solicitud_id, v_estado USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END $$;

CREATE TRIGGER trg_declaracion_ingresos_inmutable BEFORE UPDATE OR DELETE ON declaracion_ingresos
  FOR EACH ROW EXECUTE FUNCTION bloquear_cambio_de_expediente();
CREATE TRIGGER trg_movimiento_esperado_inmutable BEFORE UPDATE OR DELETE ON movimiento_esperado
  FOR EACH ROW EXECUTE FUNCTION bloquear_cambio_de_expediente();
