-- =====================================================================================
-- Ahorra+ · V4 · M3 · Pago de TDC interbancario y conciliación
-- Entidades: TARIFA_COMISION, TARJETA_CREDITO, ESTADO_CUENTA_TDC, PAGO_TDC, HISTORIAL_ESTADO_PAGO,
--            MENSAJE_SUICHE, ARCHIVO_LIQUIDACION, REGISTRO_LIQUIDACION, RESULTADO_CONCILIACION
-- =====================================================================================

CREATE TABLE pagos.tarifa_comision (
    id_tarifa       BIGSERIAL  PRIMARY KEY,
    monto_fijo_cts  BIGINT     NOT NULL CHECK (monto_fijo_cts >= 0),
    porcentaje_pb   INTEGER    CHECK (porcentaje_pb BETWEEN 0 AND 10000),
    fecha_inicio    DATE       NOT NULL,
    fecha_fin       DATE,
    CONSTRAINT tarifa_fechas_ck CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio)
);
CREATE UNIQUE INDEX tarifa_vigente_uk ON pagos.tarifa_comision ((fecha_fin IS NULL)) WHERE fecha_fin IS NULL;

-- R-02: nunca PAN; R-04: emisor siempre subtipo BANCO
CREATE TABLE pagos.tarjeta_credito (
    id_tarjeta            BIGSERIAL    PRIMARY KEY,
    id_usuario            BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_institucion        BIGINT       NOT NULL,
    tipo_institucion      VARCHAR(20)  NOT NULL DEFAULT 'banco' CHECK (tipo_institucion = 'banco'),
    ref_tarjeta_emisor    VARCHAR(80)  NOT NULL,
    ultimos_4             CHAR(4)      NOT NULL CHECK (ultimos_4 ~ '^[0-9]{4}$'),
    titular_confirmado    VARCHAR(120) NOT NULL,
    es_propia             BOOLEAN      NOT NULL,
    alias_usuario         VARCHAR(40),
    fecha_registro        TIMESTAMPTZ  NOT NULL,
    estatus               VARCHAR(10)  NOT NULL CHECK (estatus IN ('activa', 'inactiva')),
    FOREIGN KEY (id_institucion, tipo_institucion) REFERENCES consolidacion.institucion_financiera (id_institucion, tipo),
    CONSTRAINT tarjeta_emisor_uk UNIQUE (id_usuario, id_institucion, ref_tarjeta_emisor)
);

CREATE TABLE pagos.estado_cuenta_tdc (
    id_estado_cuenta   BIGSERIAL  PRIMARY KEY,
    id_tarjeta         BIGINT     NOT NULL REFERENCES pagos.tarjeta_credito (id_tarjeta),
    fecha_corte        DATE       NOT NULL,
    fecha_limite_pago  DATE       NOT NULL,
    saldo_total_cts    BIGINT     NOT NULL,
    pago_minimo_cts    BIGINT     NOT NULL CHECK (pago_minimo_cts >= 0),
    CONSTRAINT estado_cuenta_corte_uk UNIQUE (id_tarjeta, fecha_corte),
    CONSTRAINT estado_cuenta_fechas_ck CHECK (fecha_limite_pago >= fecha_corte)
);

CREATE TABLE pagos.pago_tdc (
    id_pago                   BIGSERIAL    PRIMARY KEY,
    id_usuario                BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_tarjeta                BIGINT       NOT NULL REFERENCES pagos.tarjeta_credito (id_tarjeta),
    id_cuenta_origen          BIGINT       NOT NULL REFERENCES consolidacion.cuenta_vinculada (id_cuenta),
    id_estado_cuenta          BIGINT       REFERENCES pagos.estado_cuenta_tdc (id_estado_cuenta),
    id_tarifa                 BIGINT       NOT NULL REFERENCES pagos.tarifa_comision (id_tarifa),
    clave_idempotencia        VARCHAR(64)  NOT NULL UNIQUE,                         -- R-15
    monto_cts                 BIGINT       NOT NULL CHECK (monto_cts > 0),
    comision_cts              BIGINT       NOT NULL CHECK (comision_cts >= 0),       -- R-17: se calcula una vez
    fecha_hora_creacion       TIMESTAMPTZ  NOT NULL,
    estado_actual             VARCHAR(16)  NOT NULL CHECK (estado_actual IN
        ('PENDIENTE', 'AUTORIZADO', 'CAPTURADO', 'CONCILIADO', 'RECHAZADO', 'EN_VERIFICACION', 'REVERSADO', 'CON_DIFERENCIA')),
    beneficiario_confirmado   BOOLEAN      NOT NULL CHECK (beneficiario_confirmado),   -- R-16
    referencia_suiche         VARCHAR(40)  UNIQUE,
    codigo_autorizacion       VARCHAR(20),
    motivo_rechazo            TEXT,
    es_puntual                BOOLEAN,                                                 -- R-18 (al capturar)
    fecha_hora_captura        TIMESTAMPTZ,                                             -- técnico: día de conciliación
    fecha_hora_actualizacion  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pago_captura_ck CHECK (estado_actual NOT IN ('CAPTURADO', 'CONCILIADO') OR (referencia_suiche IS NOT NULL AND fecha_hora_captura IS NOT NULL AND es_puntual IS NOT NULL))
);
CREATE INDEX pago_usuario_idx ON pagos.pago_tdc (id_usuario, fecha_hora_creacion DESC);
CREATE INDEX pago_estado_idx ON pagos.pago_tdc (estado_actual);

-- R-14: todo cambio de estado inserta historial (append-only)
CREATE TABLE pagos.historial_estado_pago (
    id_pago            BIGINT       NOT NULL REFERENCES pagos.pago_tdc (id_pago),
    secuencia          INTEGER      NOT NULL,
    estado_anterior    VARCHAR(16),
    estado_nuevo       VARCHAR(16)  NOT NULL,
    fecha_hora         TIMESTAMPTZ  NOT NULL,
    actor              VARCHAR(10)  NOT NULL CHECK (actor IN ('usuario', 'sistema', 'suiche', 'operador')),
    referencia_suiche  VARCHAR(40),
    PRIMARY KEY (id_pago, secuencia)
);
CREATE TRIGGER historial_estado_pago_inmutable BEFORE UPDATE OR DELETE ON pagos.historial_estado_pago
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();

CREATE TABLE pagos.mensaje_suiche (
    id_mensaje            BIGSERIAL    PRIMARY KEY,
    id_pago               BIGINT       NOT NULL REFERENCES pagos.pago_tdc (id_pago),
    tipo                  VARCHAR(10)  NOT NULL CHECK (tipo IN ('benef', 'autoriz', 'captura', 'reverso', 'consulta')),
    numero_intento        INTEGER      NOT NULL CHECK (numero_intento >= 1),
    fecha_hora_envio      TIMESTAMPTZ  NOT NULL,
    fecha_hora_respuesta  TIMESTAMPTZ,
    resultado             VARCHAR(10)  NOT NULL CHECK (resultado IN ('aprobado', 'rechazado', 'timeout', 'error')),
    codigo_respuesta      VARCHAR(10)
);
CREATE TRIGGER mensaje_suiche_inmutable BEFORE UPDATE OR DELETE ON pagos.mensaje_suiche
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();

CREATE TABLE pagos.archivo_liquidacion (
    id_archivo            BIGSERIAL    PRIMARY KEY,
    fecha_liquidacion     DATE         NOT NULL UNIQUE,
    fecha_hora_recepcion  TIMESTAMPTZ  NOT NULL,
    nombre_archivo        VARCHAR(120) NOT NULL,
    hash_archivo          CHAR(64)     NOT NULL,
    total_registros       INTEGER      NOT NULL CHECK (total_registros >= 0),
    monto_total_cts       BIGINT       NOT NULL,
    estatus               VARCHAR(16)  NOT NULL CHECK (estatus IN ('recibido', 'procesado', 'con_diferencias'))
);

CREATE TABLE pagos.registro_liquidacion (
    id_archivo            BIGINT       NOT NULL REFERENCES pagos.archivo_liquidacion (id_archivo),
    linea                 INTEGER      NOT NULL,
    referencia_suiche     VARCHAR(40)  NOT NULL,
    referencia            VARCHAR(64),                 -- técnico: clave de idempotencia enviada al suiche
    monto_cts             BIGINT       NOT NULL,
    estado_suiche         VARCHAR(10)  NOT NULL CHECK (estado_suiche IN ('capturado', 'reversado')),
    fecha_hora_operacion  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id_archivo, linea)
);

-- R-19: cada resultado tiene al menos un PAGO o un REGISTRO
CREATE TABLE pagos.resultado_conciliacion (
    id_resultado        BIGSERIAL    PRIMARY KEY,
    id_pago             BIGINT       REFERENCES pagos.pago_tdc (id_pago),
    id_archivo          BIGINT       NOT NULL REFERENCES pagos.archivo_liquidacion (id_archivo),
    linea               INTEGER,
    fecha_conciliacion  DATE         NOT NULL,
    resultado           VARCHAR(14)  NOT NULL CHECK (resultado IN ('conciliado', 'dif_monto', 'falta_suiche', 'falta_ahorra')),
    diferencia_cts      BIGINT,
    FOREIGN KEY (id_archivo, linea) REFERENCES pagos.registro_liquidacion (id_archivo, linea),
    CONSTRAINT resultado_origen_ck CHECK (id_pago IS NOT NULL OR linea IS NOT NULL),
    CONSTRAINT resultado_pago_uk UNIQUE (id_pago, id_archivo)
);
