-- =====================================================================================
-- Ahorra+ · V7 · M6 · Puntos Ahorro (ledger de partida doble) y niveles
-- Entidades: NIVEL_FIDELIDAD, HISTORIAL_NIVEL, COMERCIO_ALIADO, CUENTA_PUNTOS, TRANSACCION_PUNTOS,
--            ASIENTO_PUNTOS, RECOMPENSA, CANJE
-- R-21: el saldo de puntos se CALCULA (Σ créditos − Σ débitos), nunca se guarda.
-- =====================================================================================

CREATE TABLE puntos.nivel_fidelidad (
    id_nivel        SMALLSERIAL  PRIMARY KEY,
    nombre          VARCHAR(20)  NOT NULL UNIQUE,
    puntos_minimos  BIGINT       NOT NULL CHECK (puntos_minimos >= 0),
    orden           SMALLINT     NOT NULL UNIQUE
);

CREATE TABLE puntos.historial_nivel (
    id_usuario    BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_nivel      SMALLINT     NOT NULL REFERENCES puntos.nivel_fidelidad (id_nivel),
    fecha_inicio  TIMESTAMPTZ  NOT NULL,
    fecha_fin     TIMESTAMPTZ,
    PRIMARY KEY (id_usuario, id_nivel, fecha_inicio),
    CONSTRAINT historial_nivel_fechas_ck CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio)
);
CREATE UNIQUE INDEX historial_nivel_vigente_uk ON puntos.historial_nivel (id_usuario) WHERE fecha_fin IS NULL;

CREATE TABLE puntos.comercio_aliado (
    id_comercio   BIGSERIAL    PRIMARY KEY,
    rif           VARCHAR(12)  NOT NULL UNIQUE,
    razon_social  VARCHAR(120) NOT NULL,
    categoria     VARCHAR(12)  NOT NULL CHECK (categoria IN ('alimentos', 'salud', 'tecnologia', 'ocio', 'otro')),
    estatus       VARCHAR(10)  NOT NULL CHECK (estatus IN ('activo', 'inactivo'))
);

-- R-20: arco exclusivo — la cuenta pertenece a un USUARIO, o a un BANCO aliado, o a un COMERCIO; 'sistema' sin dueño
CREATE TABLE puntos.cuenta_puntos (
    id_cuenta_puntos  BIGSERIAL    PRIMARY KEY,
    tipo              VARCHAR(12)  NOT NULL CHECK (tipo IN ('usuario', 'fondeo_banco', 'comercio', 'sistema')),
    fecha_apertura    TIMESTAMPTZ  NOT NULL,
    id_usuario        BIGINT       REFERENCES identidad.usuario (id_usuario),
    id_institucion    BIGINT       REFERENCES consolidacion.institucion_financiera (id_institucion),
    id_comercio       BIGINT       REFERENCES puntos.comercio_aliado (id_comercio),
    CONSTRAINT cuenta_puntos_arco_ck CHECK (
        (tipo = 'usuario'      AND id_usuario IS NOT NULL AND id_institucion IS NULL AND id_comercio IS NULL) OR
        (tipo = 'fondeo_banco' AND id_usuario IS NULL AND id_institucion IS NOT NULL AND id_comercio IS NULL) OR
        (tipo = 'comercio'     AND id_usuario IS NULL AND id_institucion IS NULL AND id_comercio IS NOT NULL) OR
        (tipo = 'sistema'      AND id_usuario IS NULL AND id_institucion IS NULL AND id_comercio IS NULL))
);
CREATE UNIQUE INDEX cuenta_puntos_usuario_uk ON puntos.cuenta_puntos (id_usuario) WHERE id_usuario IS NOT NULL;
CREATE UNIQUE INDEX cuenta_puntos_banco_uk ON puntos.cuenta_puntos (id_institucion) WHERE id_institucion IS NOT NULL;
CREATE UNIQUE INDEX cuenta_puntos_comercio_uk ON puntos.cuenta_puntos (id_comercio) WHERE id_comercio IS NOT NULL;
CREATE UNIQUE INDEX cuenta_puntos_sistema_uk ON puntos.cuenta_puntos ((tipo)) WHERE tipo = 'sistema';

CREATE TABLE puntos.transaccion_puntos (
    id_transaccion  BIGSERIAL    PRIMARY KEY,
    fecha_hora      TIMESTAMPTZ  NOT NULL,
    concepto        VARCHAR(12)  NOT NULL CHECK (concepto IN ('meta', 'pago_puntual', 'racha', 'referido', 'canje', 'reverso')),
    descripcion     VARCHAR(200),
    clave_origen    VARCHAR(80)  UNIQUE     -- técnico: idempotencia (un evento nunca acredita dos veces)
);

CREATE TABLE puntos.asiento_puntos (
    id_transaccion    BIGINT      NOT NULL REFERENCES puntos.transaccion_puntos (id_transaccion),
    linea             SMALLINT    NOT NULL,
    id_cuenta_puntos  BIGINT      NOT NULL REFERENCES puntos.cuenta_puntos (id_cuenta_puntos),
    naturaleza        VARCHAR(8)  NOT NULL CHECK (naturaleza IN ('debito', 'credito')),
    puntos            BIGINT      NOT NULL CHECK (puntos > 0),
    PRIMARY KEY (id_transaccion, linea)
);
CREATE INDEX asiento_cuenta_idx ON puntos.asiento_puntos (id_cuenta_puntos);

-- R-10: ledger inmutable
CREATE TRIGGER transaccion_puntos_inmutable BEFORE UPDATE OR DELETE ON puntos.transaccion_puntos
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();
CREATE TRIGGER asiento_puntos_inmutable BEFORE UPDATE OR DELETE ON puntos.asiento_puntos
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();

-- R-21: ≥ 2 asientos y Σ débitos = Σ créditos (verificado al confirmar la transacción)
CREATE OR REPLACE FUNCTION puntos.verificar_partida_doble() RETURNS trigger AS $$
DECLARE
    deb  BIGINT;
    cred BIGINT;
    n    INTEGER;
BEGIN
    SELECT COALESCE(SUM(CASE WHEN naturaleza = 'debito' THEN puntos END), 0),
           COALESCE(SUM(CASE WHEN naturaleza = 'credito' THEN puntos END), 0),
           COUNT(*)
      INTO deb, cred, n
      FROM puntos.asiento_puntos WHERE id_transaccion = NEW.id_transaccion;
    IF n < 2 OR deb <> cred THEN
        RAISE EXCEPTION 'R-21: transacción de puntos % descuadrada (débitos %, créditos %, asientos %)', NEW.id_transaccion, deb, cred, n;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER asiento_partida_doble AFTER INSERT ON puntos.asiento_puntos
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION puntos.verificar_partida_doble();

CREATE TABLE puntos.recompensa (
    id_recompensa     BIGSERIAL    PRIMARY KEY,
    id_comercio       BIGINT       NOT NULL REFERENCES puntos.comercio_aliado (id_comercio),
    id_nivel_minimo   SMALLINT     NOT NULL REFERENCES puntos.nivel_fidelidad (id_nivel),
    descripcion       VARCHAR(160) NOT NULL,
    costo_puntos      BIGINT       NOT NULL CHECK (costo_puntos > 0),
    fecha_inicio      DATE         NOT NULL,
    fecha_fin         DATE,
    stock_disponible  INTEGER      CHECK (stock_disponible >= 0)
);

CREATE TABLE puntos.canje (
    id_canje          BIGSERIAL    PRIMARY KEY,
    id_recompensa     BIGINT       NOT NULL REFERENCES puntos.recompensa (id_recompensa),
    id_usuario        BIGINT       NOT NULL REFERENCES identidad.usuario (id_usuario),
    id_transaccion    BIGINT       NOT NULL UNIQUE REFERENCES puntos.transaccion_puntos (id_transaccion),
    fecha_hora        TIMESTAMPTZ  NOT NULL,
    puntos_canjeados  BIGINT       NOT NULL CHECK (puntos_canjeados > 0),
    codigo_canje      VARCHAR(20)  NOT NULL UNIQUE,
    estatus           VARCHAR(8)   NOT NULL CHECK (estatus IN ('emitido', 'usado', 'vencido', 'anulado'))
);
