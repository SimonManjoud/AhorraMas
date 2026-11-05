-- =====================================================================================
-- Ahorra+ · V3 · M2 · Consolidación multibanco y tasa BCV
-- Entidades: MONEDA, INSTITUCION_FINANCIERA (supertipo BANCO / PLATAFORMA_CRIPTO), ALIANZA_BANCARIA,
--            CONSENTIMIENTO, CUENTA_VINCULADA, SALDO_CUENTA, MOVIMIENTO, TASA_CAMBIO, INDICADOR_INFLACION
-- + boveda.secreto (simula Vault: tokens de consentimiento cifrados; la BD del núcleo guarda solo la referencia)
-- =====================================================================================

CREATE TABLE consolidacion.moneda (
    id_moneda  SMALLSERIAL  PRIMARY KEY,
    codigo     VARCHAR(5)   NOT NULL UNIQUE,
    nombre     VARCHAR(40)  NOT NULL,
    tipo       VARCHAR(10)  NOT NULL CHECK (tipo IN ('fiat', 'stablecoin', 'cripto')),
    decimales  SMALLINT     NOT NULL CHECK (decimales BETWEEN 0 AND 8)
);

CREATE TABLE consolidacion.institucion_financiera (
    id_institucion       BIGSERIAL    PRIMARY KEY,
    codigo_integracion   VARCHAR(8)   NOT NULL UNIQUE,   -- técnico: código en el Hub de integraciones
    nombre               VARCHAR(80)  NOT NULL UNIQUE,
    tipo                 VARCHAR(20)  NOT NULL CHECK (tipo IN ('banco', 'plataforma_cripto')),
    estatus_integracion  VARCHAR(10)  NOT NULL CHECK (estatus_integracion IN ('sandbox', 'activa', 'inactiva')),
    codigo_sudeban       CHAR(4)      UNIQUE,            -- subtipo BANCO
    tipo_plataforma      VARCHAR(10)  CHECK (tipo_plataforma IN ('exchange', 'billetera')),  -- subtipo PLATAFORMA_CRIPTO
    CONSTRAINT institucion_subtipo_ck CHECK (
        (tipo = 'banco' AND codigo_sudeban IS NOT NULL AND tipo_plataforma IS NULL) OR
        (tipo = 'plataforma_cripto' AND codigo_sudeban IS NULL AND tipo_plataforma IS NOT NULL)),
    CONSTRAINT institucion_tipo_uk UNIQUE (id_institucion, tipo)   -- permite FK que exijan subtipo BANCO
);

-- R-04: ALIANZA solo con el subtipo BANCO (FK compuesta contra (id, tipo='banco'))
CREATE TABLE consolidacion.alianza_bancaria (
    id_alianza                BIGSERIAL    PRIMARY KEY,
    id_institucion            BIGINT       NOT NULL,
    tipo_institucion          VARCHAR(20)  NOT NULL DEFAULT 'banco' CHECK (tipo_institucion = 'banco'),
    fecha_inicio              DATE         NOT NULL,
    fecha_fin                 DATE,
    fee_cliente_puntual_cts   BIGINT       NOT NULL CHECK (fee_cliente_puntual_cts >= 0),
    puntos_por_pago_puntual   INTEGER      NOT NULL CHECK (puntos_por_pago_puntual >= 0),
    correo_responsable        VARCHAR(120) NOT NULL,
    FOREIGN KEY (id_institucion, tipo_institucion) REFERENCES consolidacion.institucion_financiera (id_institucion, tipo),
    CONSTRAINT alianza_fechas_ck CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio)
);
CREATE UNIQUE INDEX alianza_vigente_uk ON consolidacion.alianza_bancaria (id_institucion) WHERE fecha_fin IS NULL;

CREATE TABLE consolidacion.consentimiento (
    id_consentimiento   BIGSERIAL    PRIMARY KEY,
    id_usuario          BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_institucion      BIGINT       NOT NULL REFERENCES consolidacion.institucion_financiera (id_institucion),
    fecha_otorgamiento  TIMESTAMPTZ  NOT NULL,
    fecha_vencimiento   TIMESTAMPTZ  NOT NULL,
    fecha_revocacion    TIMESTAMPTZ,
    estatus             VARCHAR(10)  NOT NULL CHECK (estatus IN ('pendiente', 'activo', 'revocado', 'vencido')),
    permite_lectura     BOOLEAN      NOT NULL,
    permite_debito_tdc  BOOLEAN      NOT NULL,
    ref_token_boveda    VARCHAR(64),                       -- R-02: solo la referencia; el token vive en la bóveda
    motivo_revocacion   TEXT,
    estado_oauth        VARCHAR(64)  UNIQUE,               -- técnico: parámetro "state" del flujo OAuth2
    CONSTRAINT consentimiento_fechas_ck CHECK (fecha_vencimiento > fecha_otorgamiento),
    CONSTRAINT consentimiento_token_ck CHECK (estatus <> 'activo' OR ref_token_boveda IS NOT NULL),
    CONSTRAINT consentimiento_revocacion_ck CHECK (estatus <> 'revocado' OR fecha_revocacion IS NOT NULL)
);
-- R-03: un solo consentimiento activo por usuario e institución
CREATE UNIQUE INDEX consentimiento_activo_uk ON consolidacion.consentimiento (id_usuario, id_institucion) WHERE estatus = 'activo';

CREATE TABLE consolidacion.cuenta_vinculada (
    id_cuenta           BIGSERIAL    PRIMARY KEY,
    id_consentimiento   BIGINT       NOT NULL REFERENCES consolidacion.consentimiento (id_consentimiento),
    id_usuario          BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_institucion      BIGINT       NOT NULL REFERENCES consolidacion.institucion_financiera (id_institucion),
    id_moneda           SMALLINT     NOT NULL REFERENCES consolidacion.moneda (id_moneda),
    ref_cuenta_externa  VARCHAR(80)  NOT NULL,             -- alias opaco que entrega el banco
    ultimos_4           CHAR(4)      NOT NULL CHECK (ultimos_4 ~ '^[0-9]{4}$'),
    tipo                VARCHAR(10)  NOT NULL CHECK (tipo IN ('corriente', 'ahorro', 'divisas', 'billetera')),
    alias_usuario       VARCHAR(40),
    solo_lectura        BOOLEAN      NOT NULL,
    estatus             VARCHAR(10)  NOT NULL CHECK (estatus IN ('activa', 'inactiva')),
    fecha_vinculacion   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT cuenta_externa_uk UNIQUE (id_usuario, id_institucion, ref_cuenta_externa)
);

CREATE TABLE consolidacion.saldo_cuenta (
    id_saldo                   BIGSERIAL    PRIMARY KEY,
    id_cuenta                  BIGINT       NOT NULL REFERENCES consolidacion.cuenta_vinculada (id_cuenta),
    fecha_hora_sincronizacion  TIMESTAMPTZ  NOT NULL,
    saldo_cts                  BIGINT       NOT NULL,
    saldo_disponible_cts       BIGINT
);
CREATE INDEX saldo_cuenta_ultimo_idx ON consolidacion.saldo_cuenta (id_cuenta, fecha_hora_sincronizacion DESC);

CREATE TABLE consolidacion.movimiento (
    id_movimiento           BIGSERIAL    PRIMARY KEY,
    id_cuenta               BIGINT       NOT NULL REFERENCES consolidacion.cuenta_vinculada (id_cuenta),
    ref_movimiento_externo  VARCHAR(80)  NOT NULL,
    fecha_hora              TIMESTAMPTZ  NOT NULL,
    naturaleza              VARCHAR(8)   NOT NULL CHECK (naturaleza IN ('credito', 'debito')),
    clasificacion           VARCHAR(14)  NOT NULL CHECK (clasificacion IN ('ingreso', 'gasto', 'transf_propia', 'pago_tdc')),
    monto_cts               BIGINT       NOT NULL CHECK (monto_cts > 0),
    descripcion             VARCHAR(200),
    CONSTRAINT movimiento_externo_uk UNIQUE (id_cuenta, ref_movimiento_externo)
);
CREATE INDEX movimiento_fecha_idx ON consolidacion.movimiento (id_cuenta, fecha_hora DESC);

-- R-07: una tasa por moneda y fecha
CREATE TABLE consolidacion.tasa_cambio (
    id_tasa               BIGSERIAL    PRIMARY KEY,
    id_moneda             SMALLINT     NOT NULL REFERENCES consolidacion.moneda (id_moneda),
    fecha_vigencia        DATE         NOT NULL,
    valor_bs_x10000       BIGINT       NOT NULL CHECK (valor_bs_x10000 > 0),
    fecha_hora_obtencion  TIMESTAMPTZ  NOT NULL,
    fuente                VARCHAR(5)   NOT NULL DEFAULT 'BCV' CHECK (fuente = 'BCV'),
    CONSTRAINT tasa_moneda_fecha_uk UNIQUE (id_moneda, fecha_vigencia)
);

CREATE TABLE consolidacion.indicador_inflacion (
    id_indicador          BIGSERIAL    PRIMARY KEY,
    anio                  SMALLINT     NOT NULL,
    mes                   SMALLINT     NOT NULL CHECK (mes BETWEEN 1 AND 12),
    variacion_mensual_pb  INTEGER      NOT NULL,
    fecha_publicacion     DATE         NOT NULL,
    fuente                VARCHAR(5)   NOT NULL DEFAULT 'BCV' CHECK (fuente = 'BCV'),
    CONSTRAINT inflacion_periodo_uk UNIQUE (anio, mes)
);

-- Bóveda de secretos simulada (en producción: HashiCorp Vault + KMS/HSM)
CREATE TABLE boveda.secreto (
    ref_secreto        VARCHAR(64)  PRIMARY KEY,
    contenido_cifrado  TEXT         NOT NULL,
    fecha_creacion     TIMESTAMPTZ  NOT NULL,
    destruido          BOOLEAN      NOT NULL DEFAULT FALSE
);
