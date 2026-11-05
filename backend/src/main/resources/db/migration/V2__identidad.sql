-- =====================================================================================
-- Ahorra+ · V2 · M1 · Identidad, dispositivos y seguridad
-- Entidades del E-R: USUARIO, VERIFICACION_KYC, DOCUMENTO_KYC, LIMITE_TRANSACCIONAL, DISPOSITIVO
-- Tablas técnicas (fuera del E-R, sustituyen a Keycloak en el MVP): credencial, desafio_otp
-- LV S/N del E-R se implementan como BOOLEAN.
-- =====================================================================================

CREATE TABLE identidad.usuario (
    id_usuario        BIGSERIAL    PRIMARY KEY,
    nacionalidad_doc  CHAR(1)      NOT NULL CHECK (nacionalidad_doc IN ('V', 'E')),
    cedula            INTEGER      NOT NULL CHECK (cedula > 0),
    primer_nombre     VARCHAR(60)  NOT NULL,
    segundo_nombre    VARCHAR(60),
    primer_apellido   VARCHAR(60)  NOT NULL,
    segundo_apellido  VARCHAR(60),
    fecha_nacimiento  DATE         NOT NULL,
    -- R-06: PII cifrada en reposo (AES-GCM) + índice ciego HMAC para garantizar unicidad sin descifrar
    correo_cifrado    TEXT         NOT NULL,
    correo_indice     CHAR(64)     NOT NULL UNIQUE,
    telefono_cifrado  TEXT         NOT NULL,
    telefono_indice   CHAR(64)     NOT NULL UNIQUE,
    id_seudonimo      BIGINT       NOT NULL UNIQUE,     -- lo único que ve la IA (R-23)
    fecha_registro    TIMESTAMPTZ  NOT NULL,
    estatus           VARCHAR(10)  NOT NULL CHECK (estatus IN ('activo', 'bloqueado', 'cerrado')),
    CONSTRAINT usuario_cedula_uk UNIQUE (nacionalidad_doc, cedula),
    -- R-06: mayor de edad
    CONSTRAINT usuario_mayor_edad_ck CHECK (fecha_nacimiento <= ((fecha_registro AT TIME ZONE 'America/Caracas')::date - INTERVAL '18 years'))
);

CREATE TABLE identidad.credencial (
    id_usuario           BIGINT       PRIMARY KEY REFERENCES identidad.usuario (id_usuario),
    pin_hash             TEXT         NOT NULL,
    intentos_fallidos    INTEGER      NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    fecha_actualizacion  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE identidad.dispositivo (
    id_dispositivo            BIGSERIAL    PRIMARY KEY,
    id_usuario                BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    huella_dispositivo        CHAR(64)     NOT NULL,                 -- hash, nunca el identificador crudo
    plataforma                VARCHAR(10)  NOT NULL CHECK (plataforma IN ('iOS', 'Android', 'Otro')),
    modelo                    VARCHAR(80)  NOT NULL,
    fecha_registro            TIMESTAMPTZ  NOT NULL,
    biometria_vinculada       BOOLEAN      NOT NULL DEFAULT FALSE,
    saldo_rapido_activo       BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_activ_saldo_rapido  TIMESTAMPTZ,
    estatus                   VARCHAR(10)  NOT NULL CHECK (estatus IN ('activo', 'revocado')),
    fecha_revocacion          TIMESTAMPTZ,
    motivo_revocacion         VARCHAR(12)  CHECK (motivo_revocacion IN ('cambio_sim', 'root', 'robo', '3_fallas', 'usuario')),
    CONSTRAINT dispositivo_huella_uk UNIQUE (id_usuario, huella_dispositivo),
    CONSTRAINT dispositivo_revocacion_ck CHECK (estatus = 'activo' OR (fecha_revocacion IS NOT NULL AND motivo_revocacion IS NOT NULL)),
    CONSTRAINT dispositivo_saldo_rapido_ck CHECK (NOT saldo_rapido_activo OR fecha_activ_saldo_rapido IS NOT NULL)
);

CREATE TABLE identidad.verificacion_kyc (
    id_verificacion           BIGSERIAL    PRIMARY KEY,
    id_usuario                BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    fecha_solicitud           TIMESTAMPTZ  NOT NULL,
    fecha_resultado           TIMESTAMPTZ,
    resultado                 VARCHAR(10)  NOT NULL CHECK (resultado IN ('pendiente', 'aprobado', 'rechazado')),
    prueba_vida_superada      BOOLEAN,
    coincide_lista_sanciones  BOOLEAN,
    ref_proveedor_kyc         VARCHAR(80)  NOT NULL,
    motivo_rechazo            TEXT
);
CREATE INDEX verificacion_kyc_usuario_idx ON identidad.verificacion_kyc (id_usuario);

CREATE TABLE identidad.documento_kyc (
    id_verificacion     BIGINT       NOT NULL REFERENCES identidad.verificacion_kyc (id_verificacion),
    secuencia           INTEGER      NOT NULL,
    tipo                VARCHAR(10)  NOT NULL CHECK (tipo IN ('cedula', 'selfie')),
    ref_almacen_objetos VARCHAR(200) NOT NULL,   -- objeto cifrado en el almacén de objetos (nunca en la BD)
    hash_sha256         CHAR(64)     NOT NULL,
    fecha_carga         TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id_verificacion, secuencia)
);

CREATE TABLE identidad.limite_transaccional (
    id_limite                BIGSERIAL    PRIMARY KEY,
    id_usuario               BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    monto_max_diario_cts     BIGINT       NOT NULL CHECK (monto_max_diario_cts > 0),
    max_operaciones_diarias  INTEGER      NOT NULL CHECK (max_operaciones_diarias > 0),
    fecha_inicio             TIMESTAMPTZ  NOT NULL,
    fecha_fin                TIMESTAMPTZ,
    motivo_cambio            TEXT,
    CONSTRAINT limite_fechas_ck CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio)  -- R-05
);
-- R-05: un solo límite vigente por usuario
CREATE UNIQUE INDEX limite_vigente_uk ON identidad.limite_transaccional (id_usuario) WHERE fecha_fin IS NULL;

CREATE TABLE identidad.desafio_otp (
    id_desafio    UUID         PRIMARY KEY,
    id_usuario    BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    proposito     VARCHAR(20)  NOT NULL,
    codigo_hash   CHAR(64)     NOT NULL,     -- el OTP nunca se guarda en claro
    expira_en     TIMESTAMPTZ  NOT NULL,
    usado         BOOLEAN      NOT NULL DEFAULT FALSE,
    intentos      INTEGER      NOT NULL DEFAULT 0
);
