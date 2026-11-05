-- =====================================================================================
-- Ahorra+ · V5 · M4 · Asistente de perfil de riesgo (IA)
-- Entidades: VERSION_MODELO_IA, INSTRUMENTO_AHORRO, EVALUACION_PERFIL, RECOMENDACION_IA, DISTRIBUCION_SUGERIDA
-- =====================================================================================

CREATE TABLE perfilia.version_modelo_ia (
    id_version         BIGSERIAL    PRIMARY KEY,
    version_reglas     VARCHAR(20)  NOT NULL,
    version_modelo     VARCHAR(20)  NOT NULL,
    fecha_publicacion  DATE         NOT NULL,
    estatus            VARCHAR(10)  NOT NULL CHECK (estatus IN ('activa', 'retirada')),
    CONSTRAINT version_uk UNIQUE (version_reglas, version_modelo)
);
CREATE UNIQUE INDEX version_activa_uk ON perfilia.version_modelo_ia ((estatus)) WHERE estatus = 'activa';

CREATE TABLE perfilia.instrumento_ahorro (
    id_instrumento  SMALLSERIAL  PRIMARY KEY,
    nombre          VARCHAR(30)  NOT NULL UNIQUE,
    nivel_riesgo    VARCHAR(5)   NOT NULL CHECK (nivel_riesgo IN ('bajo', 'medio', 'alto'))
);

CREATE TABLE perfilia.evaluacion_perfil (
    id_evaluacion         BIGSERIAL    PRIMARY KEY,
    id_usuario            BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_version            BIGINT       NOT NULL REFERENCES perfilia.version_modelo_ia (id_version),
    fecha_inicio          TIMESTAMPTZ  NOT NULL,
    fecha_fin             TIMESTAMPTZ,
    objetivo_principal    VARCHAR(10)  NOT NULL CHECK (objetivo_principal IN ('proteger', 'meta_corta', 'crecer')),
    horizonte_meses       SMALLINT     NOT NULL CHECK (horizonte_meses BETWEEN 1 AND 120),
    tolerancia_riesgo     VARCHAR(5)   NOT NULL CHECK (tolerancia_riesgo IN ('baja', 'media', 'alta')),
    rango_ingreso         CHAR(2)      NOT NULL CHECK (rango_ingreso IN ('R1', 'R2', 'R3', 'R4', 'R5')),
    volatilidad_saldo_pb  INTEGER      NOT NULL CHECK (volatilidad_saldo_pb >= 0),
    perfil_resultante     VARCHAR(12)  NOT NULL CHECK (perfil_resultante IN ('conservador', 'moderado', 'dinamico')),
    CONSTRAINT evaluacion_fechas_ck CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio)
);
CREATE UNIQUE INDEX evaluacion_vigente_uk ON perfilia.evaluacion_perfil (id_usuario) WHERE fecha_fin IS NULL;

CREATE TABLE perfilia.recomendacion_ia (
    id_recomendacion        BIGSERIAL    PRIMARY KEY,
    id_evaluacion           BIGINT       NOT NULL REFERENCES perfilia.evaluacion_perfil (id_evaluacion),
    fecha_hora              TIMESTAMPTZ  NOT NULL,
    explicacion             TEXT         NOT NULL,
    estatus                 VARCHAR(10)  NOT NULL CHECK (estatus IN ('propuesta', 'aceptada', 'rechazada', 'expirada')),
    fecha_decision          TIMESTAMPTZ,
    entendio_explicacion    BOOLEAN,
    regla_sugerida_tipo     VARCHAR(16)  CHECK (regla_sugerida_tipo IN ('pct_ingreso', 'redondeo', 'fijo_quincenal')),
    regla_sugerida_valor    BIGINT,
    CONSTRAINT recomendacion_decision_ck CHECK (estatus IN ('propuesta', 'expirada') OR fecha_decision IS NOT NULL)
);

-- R-23: Σ porcentaje_pb = 10.000 por recomendación (verificado al final de la transacción)
CREATE TABLE perfilia.distribucion_sugerida (
    id_recomendacion  BIGINT    NOT NULL REFERENCES perfilia.recomendacion_ia (id_recomendacion),
    id_instrumento    SMALLINT  NOT NULL REFERENCES perfilia.instrumento_ahorro (id_instrumento),
    porcentaje_pb     INTEGER   NOT NULL CHECK (porcentaje_pb BETWEEN 0 AND 10000),
    PRIMARY KEY (id_recomendacion, id_instrumento)
);

CREATE OR REPLACE FUNCTION perfilia.verificar_distribucion() RETURNS trigger AS $$
DECLARE
    total BIGINT;
BEGIN
    SELECT COALESCE(SUM(porcentaje_pb), 0) INTO total FROM perfilia.distribucion_sugerida WHERE id_recomendacion = NEW.id_recomendacion;
    IF total <> 10000 THEN
        RAISE EXCEPTION 'R-23: la distribución de la recomendación % suma % pb (debe ser 10000)', NEW.id_recomendacion, total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER distribucion_suma_100 AFTER INSERT OR UPDATE ON perfilia.distribucion_sugerida
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION perfilia.verificar_distribucion();
