-- =====================================================================================
-- Ahorra+ · V6 · M5 · Cofre Protector: metas, reglas, racha, sello y Espejo de Impacto
-- Entidades: META_AHORRO, REGLA_AHORRO (supertipo), CUOTA_DIARIA, APORTE (ledger), MULTIPLICADOR_RACHA,
--            RACHA, RACHA_DIA, SELLO_COMPROMISO, REPORTE_MENSUAL
-- El cofre es LÓGICO: el dinero sigue en el banco del usuario (regla de oro: sin custodia).
-- =====================================================================================

CREATE TABLE cofre.meta_ahorro (
    id_meta                 BIGSERIAL    PRIMARY KEY,
    id_usuario              BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    nombre                  VARCHAR(60)  NOT NULL,
    categoria               VARCHAR(12)  NOT NULL CHECK (categoria IN ('viaje', 'emergencia', 'estudio', 'salud', 'hogar', 'otro')),
    monto_objetivo_usd_cts  BIGINT       NOT NULL CHECK (monto_objetivo_usd_cts > 0),     -- R-08: meta en USD
    fecha_objetivo          DATE         NOT NULL,
    frecuencia_cuota        VARCHAR(8)   NOT NULL CHECK (frecuencia_cuota IN ('diaria', 'semanal')),
    fecha_creacion          TIMESTAMPTZ  NOT NULL,
    estatus                 VARCHAR(10)  NOT NULL CHECK (estatus IN ('activa', 'pausada', 'cumplida', 'cancelada', 'vencida')),
    fecha_cierre            TIMESTAMPTZ,
    -- R-08: fecha_objetivo > fecha_creacion
    CONSTRAINT meta_fecha_objetivo_ck CHECK (fecha_objetivo > (fecha_creacion AT TIME ZONE 'America/Caracas')::date)
);
CREATE INDEX meta_usuario_idx ON cofre.meta_ahorro (id_usuario);

-- Supertipo REGLA_AHORRO con subtipos PORCENTAJE_INGRESO / REDONDEO / APORTE_FIJO_QUINCENAL (R-13)
CREATE TABLE cofre.regla_ahorro (
    id_regla               BIGSERIAL    PRIMARY KEY,
    id_meta                BIGINT       NOT NULL REFERENCES cofre.meta_ahorro (id_meta),
    id_recomendacion       BIGINT       REFERENCES perfilia.recomendacion_ia (id_recomendacion),
    tipo                   VARCHAR(16)  NOT NULL CHECK (tipo IN ('pct_ingreso', 'redondeo', 'fijo_quincenal')),
    fecha_aprobacion       TIMESTAMPTZ  NOT NULL,          -- huella/PIN
    fecha_inicio_vigencia  TIMESTAMPTZ  NOT NULL,
    fecha_fin_vigencia     TIMESTAMPTZ,
    estatus                VARCHAR(10)  NOT NULL CHECK (estatus IN ('activa', 'pausada', 'finalizada')),
    porcentaje_pb          INTEGER,
    multiplo_redondeo_cts  BIGINT,
    monto_fijo_usd_cts     BIGINT,
    CONSTRAINT regla_subtipo_ck CHECK (
        (tipo = 'pct_ingreso'    AND porcentaje_pb BETWEEN 1 AND 10000 AND multiplo_redondeo_cts IS NULL AND monto_fijo_usd_cts IS NULL) OR
        (tipo = 'redondeo'       AND multiplo_redondeo_cts > 0 AND porcentaje_pb IS NULL AND monto_fijo_usd_cts IS NULL) OR
        (tipo = 'fijo_quincenal' AND monto_fijo_usd_cts > 0 AND porcentaje_pb IS NULL AND multiplo_redondeo_cts IS NULL)),
    CONSTRAINT regla_fechas_ck CHECK (fecha_fin_vigencia IS NULL OR fecha_fin_vigencia > fecha_inicio_vigencia)
);
-- R-05: una regla vigente por meta y tipo
CREATE UNIQUE INDEX regla_vigente_uk ON cofre.regla_ahorro (id_meta, tipo) WHERE estatus <> 'finalizada';

-- R-09: una cuota por meta y fecha
CREATE TABLE cofre.cuota_diaria (
    id_cuota                BIGSERIAL    PRIMARY KEY,
    id_meta                 BIGINT       NOT NULL REFERENCES cofre.meta_ahorro (id_meta),
    fecha                   DATE         NOT NULL,
    monto_sugerido_usd_cts  BIGINT       NOT NULL CHECK (monto_sugerido_usd_cts >= 0),
    monto_sugerido_bs_cts   BIGINT       NOT NULL CHECK (monto_sugerido_bs_cts >= 0),
    tasa_valor_bs_x10000    BIGINT       NOT NULL CHECK (tasa_valor_bs_x10000 > 0),   -- técnico: tasa BCV usada
    periodos_restantes      INTEGER      NOT NULL CHECK (periodos_restantes >= 1),    -- técnico
    estatus                 VARCHAR(10)  NOT NULL CHECK (estatus IN ('pendiente', 'cumplida', 'parcial', 'colchon', 'omitida')),
    CONSTRAINT cuota_meta_fecha_uk UNIQUE (id_meta, fecha)
);

-- R-10: APORTE es ledger inmutable (sin UPDATE ni DELETE)
CREATE TABLE cofre.aporte (
    id_aporte             BIGSERIAL    PRIMARY KEY,
    id_meta               BIGINT       NOT NULL REFERENCES cofre.meta_ahorro (id_meta),
    id_cuenta             BIGINT       REFERENCES consolidacion.cuenta_vinculada (id_cuenta),
    id_regla              BIGINT       REFERENCES cofre.regla_ahorro (id_regla),
    id_cuota              BIGINT       REFERENCES cofre.cuota_diaria (id_cuota),
    fecha_hora            TIMESTAMPTZ  NOT NULL,
    tipo                  VARCHAR(8)   NOT NULL CHECK (tipo IN ('aporte', 'retiro')),
    origen                VARCHAR(14)  NOT NULL CHECK (origen IN ('regla', 'manual', 'cuota_del_dia')),
    monto_usd_cts         BIGINT       NOT NULL CHECK (monto_usd_cts > 0),
    monto_bs_cts          BIGINT       NOT NULL CHECK (monto_bs_cts >= 0),
    tasa_valor_bs_x10000  BIGINT       NOT NULL CHECK (tasa_valor_bs_x10000 > 0),   -- técnico: R-07
    clave_origen          VARCHAR(80)  UNIQUE,                                       -- técnico: idempotencia de reglas
    CONSTRAINT aporte_origen_ck CHECK ((origen = 'regla') = (id_regla IS NOT NULL))
);
CREATE INDEX aporte_meta_idx ON cofre.aporte (id_meta, fecha_hora);
CREATE TRIGGER aporte_inmutable BEFORE UPDATE OR DELETE ON cofre.aporte
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();
CREATE TRIGGER aporte_sin_truncate BEFORE TRUNCATE ON cofre.aporte
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria.impedir_modificacion();

CREATE TABLE cofre.multiplicador_racha (
    id_tramo                BIGSERIAL  PRIMARY KEY,
    dias_minimos            INTEGER    NOT NULL CHECK (dias_minimos >= 0),
    multiplicador_x100      INTEGER    NOT NULL CHECK (multiplicador_x100 >= 100),
    dias_colchon_otorgados  INTEGER    NOT NULL CHECK (dias_colchon_otorgados >= 0),
    fecha_inicio            DATE       NOT NULL,
    fecha_fin               DATE,
    CONSTRAINT tramo_fechas_ck CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio)
);
CREATE UNIQUE INDEX tramo_vigente_uk ON cofre.multiplicador_racha (dias_minimos) WHERE fecha_fin IS NULL;

-- R-11: una sola racha activa por usuario
CREATE TABLE cofre.racha (
    id_racha       BIGSERIAL    PRIMARY KEY,
    id_usuario     BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    fecha_inicio   DATE         NOT NULL,
    fecha_fin      DATE,
    motivo_cierre  VARCHAR(14)  CHECK (motivo_cierre IN ('sin_aporte', 'sello_roto', 'cierre_cuenta')),
    CONSTRAINT racha_cierre_ck CHECK ((fecha_fin IS NULL) = (motivo_cierre IS NULL)),
    CONSTRAINT racha_fechas_ck CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);
CREATE UNIQUE INDEX racha_activa_uk ON cofre.racha (id_usuario) WHERE fecha_fin IS NULL;

CREATE TABLE cofre.racha_dia (
    id_racha  BIGINT      NOT NULL REFERENCES cofre.racha (id_racha),
    fecha     DATE        NOT NULL,
    tipo_dia  VARCHAR(8)  NOT NULL CHECK (tipo_dia IN ('aporte', 'colchon')),
    PRIMARY KEY (id_racha, fecha)
);

-- R-12: sello lógico, el dinero nunca se bloquea
CREATE TABLE cofre.sello_compromiso (
    id_sello               BIGSERIAL    PRIMARY KEY,
    id_meta                BIGINT       NOT NULL REFERENCES cofre.meta_ahorro (id_meta),
    monto_sellado_usd_cts  BIGINT       NOT NULL CHECK (monto_sellado_usd_cts > 0),
    fecha_sellado          TIMESTAMPTZ  NOT NULL,
    fecha_liberacion       DATE         NOT NULL,
    estatus                VARCHAR(10)  NOT NULL CHECK (estatus IN ('activo', 'liberado', 'roto')),
    fecha_ruptura          TIMESTAMPTZ,
    CONSTRAINT sello_ruptura_ck CHECK ((estatus = 'roto') = (fecha_ruptura IS NOT NULL))
);
CREATE UNIQUE INDEX sello_activo_uk ON cofre.sello_compromiso (id_meta) WHERE estatus = 'activo';

-- Espejo de Impacto: se calcula una sola vez al cierre de mes (inmutable)
CREATE TABLE cofre.reporte_mensual (
    id_reporte                BIGSERIAL    PRIMARY KEY,
    id_usuario                BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    anio                      SMALLINT     NOT NULL,
    mes                       SMALLINT     NOT NULL CHECK (mes BETWEEN 1 AND 12),
    total_bs_cts              BIGINT       NOT NULL,
    total_usd_cts             BIGINT       NOT NULL,
    saldo_cofres_usd_cts      BIGINT       NOT NULL,
    perdida_evitada_bs_cts    BIGINT       NOT NULL,
    tasa_inicio_bs_x10000     BIGINT       NOT NULL,
    tasa_fin_bs_x10000        BIGINT       NOT NULL,
    inflacion_mensual_pb      INTEGER,
    fecha_generacion          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT reporte_periodo_uk UNIQUE (id_usuario, anio, mes)
);
CREATE TRIGGER reporte_mensual_inmutable BEFORE UPDATE OR DELETE ON cofre.reporte_mensual
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();
