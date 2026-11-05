-- =====================================================================================
-- Ahorra+ · V8 · M7 · Backoffice y cumplimiento (AML) + Notificaciones
-- Entidades: OPERADOR, CASO_SOPORTE, ALERTA_AML, REPORTE_REGULATORIO, NOTIFICACION
-- =====================================================================================

CREATE TABLE backoffice.operador (
    id_operador         BIGSERIAL    PRIMARY KEY,
    primer_nombre       VARCHAR(60)  NOT NULL,
    primer_apellido     VARCHAR(60)  NOT NULL,
    segundo_apellido    VARCHAR(60),
    correo_corporativo  VARCHAR(120) NOT NULL UNIQUE,     -- identidad SSO
    rol                 VARCHAR(14)  NOT NULL CHECK (rol IN ('soporte', 'conciliacion', 'oficial_cumpl', 'admin')),
    estatus             VARCHAR(10)  NOT NULL CHECK (estatus IN ('activo', 'inactivo')),
    clave_hash          TEXT                               -- técnico: SSO simulado en el MVP
);

CREATE TABLE backoffice.caso_soporte (
    id_caso         BIGSERIAL    PRIMARY KEY,
    id_usuario      BIGINT       REFERENCES identidad.usuario (id_usuario),
    id_pago         BIGINT       REFERENCES pagos.pago_tdc (id_pago),
    id_operador     BIGINT       REFERENCES backoffice.operador (id_operador),
    fecha_apertura  TIMESTAMPTZ  NOT NULL,
    fecha_cierre    TIMESTAMPTZ,
    tipo            VARCHAR(10)  NOT NULL CHECK (tipo IN ('diferencia', 'reverso', 'reclamo', 'fraude', 'consulta')),
    prioridad       VARCHAR(5)   NOT NULL CHECK (prioridad IN ('baja', 'media', 'alta')),
    estatus         VARCHAR(10)  NOT NULL CHECK (estatus IN ('abierto', 'en_proceso', 'resuelto', 'cerrado')),
    descripcion     TEXT         NOT NULL,
    resolucion      TEXT,
    clave_origen    VARCHAR(80)  UNIQUE,                  -- técnico: idempotencia de eventos
    CONSTRAINT caso_cierre_ck CHECK (estatus NOT IN ('resuelto', 'cerrado') OR fecha_cierre IS NOT NULL)
);

CREATE TABLE backoffice.alerta_aml (
    id_alerta        BIGSERIAL    PRIMARY KEY,
    id_usuario       BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_pago          BIGINT       REFERENCES pagos.pago_tdc (id_pago),
    id_operador      BIGINT       REFERENCES backoffice.operador (id_operador),
    fecha_hora       TIMESTAMPTZ  NOT NULL,
    regla_disparada  VARCHAR(30)  NOT NULL,
    nivel_riesgo     VARCHAR(5)   NOT NULL CHECK (nivel_riesgo IN ('bajo', 'medio', 'alto')),
    estatus          VARCHAR(12)  NOT NULL CHECK (estatus IN ('nueva', 'en_revision', 'descartada', 'escalada')),
    fecha_revision   TIMESTAMPTZ,
    justificacion    TEXT,
    clave_origen     VARCHAR(80)  UNIQUE
);

CREATE TABLE backoffice.reporte_regulatorio (
    id_reporte_reg    BIGSERIAL    PRIMARY KEY,
    id_alerta         BIGINT       REFERENCES backoffice.alerta_aml (id_alerta),
    id_operador       BIGINT       NOT NULL REFERENCES backoffice.operador (id_operador),
    tipo              VARCHAR(10)  NOT NULL CHECK (tipo IN ('ROS', 'periodico')),
    destinatario      VARCHAR(10)  NOT NULL CHECK (destinatario IN ('UNIF', 'SUDEBAN')),
    fecha_generacion  TIMESTAMPTZ  NOT NULL,
    fecha_envio       TIMESTAMPTZ,
    estatus           VARCHAR(10)  NOT NULL CHECK (estatus IN ('borrador', 'enviado')),
    numero_acuse      VARCHAR(40),
    contenido         JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE notificaciones.notificacion (
    id_notificacion   BIGSERIAL    PRIMARY KEY,
    id_usuario        BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    canal             VARCHAR(6)   NOT NULL CHECK (canal IN ('push', 'sms', 'correo')),
    tipo              VARCHAR(12)  NOT NULL CHECK (tipo IN ('movimiento', 'pago', 'otp', 'meta', 'racha', 'cuota', 'seguridad')),
    titulo            VARCHAR(120) NOT NULL,
    mensaje           VARCHAR(500) NOT NULL,
    fecha_hora_envio  TIMESTAMPTZ  NOT NULL,
    estatus           VARCHAR(10)  NOT NULL CHECK (estatus IN ('pendiente', 'enviada', 'fallida')),
    fecha_lectura     TIMESTAMPTZ
);
CREATE INDEX notificacion_usuario_idx ON notificaciones.notificacion (id_usuario, fecha_hora_envio DESC);
