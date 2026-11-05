-- =====================================================================================
-- Ahorra+ · V1 · Esquemas por módulo y registro de auditoría (WORM simulado)
-- Un esquema PostgreSQL por módulo del monolito: cada módulo es dueño de sus tablas.
-- Montos SIEMPRE en enteros (céntimos, BIGINT) con moneda explícita (R-01).
-- =====================================================================================

CREATE SCHEMA IF NOT EXISTS auditoria;      -- Registro de auditoría append-only (C4: almacenamiento WORM)
CREATE SCHEMA IF NOT EXISTS identidad;      -- M1 · Identidad, dispositivos y seguridad
CREATE SCHEMA IF NOT EXISTS consolidacion;  -- M2 · Consolidación multibanco y tasa BCV
CREATE SCHEMA IF NOT EXISTS boveda;         -- Bóveda de secretos simulada (C4: Vault + KMS)
CREATE SCHEMA IF NOT EXISTS pagos;          -- M3 · Pago de TDC interbancario y conciliación
CREATE SCHEMA IF NOT EXISTS perfilia;       -- M4 · Asistente de perfil de riesgo (IA)
CREATE SCHEMA IF NOT EXISTS cofre;          -- M5 · Cofre Protector
CREATE SCHEMA IF NOT EXISTS puntos;         -- M6 · Puntos Ahorro (ledger partida doble) y niveles
CREATE SCHEMA IF NOT EXISTS backoffice;     -- M7 · Backoffice y cumplimiento (AML)
CREATE SCHEMA IF NOT EXISTS notificaciones; -- Notificaciones al usuario

-- Función genérica: impide UPDATE / DELETE / TRUNCATE en tablas inmutables (ledger, auditoría).
CREATE OR REPLACE FUNCTION auditoria.impedir_modificacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla %.% es inmutable (append-only): % no permitido', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

-- -------------------------------------------------------------------------------------
-- Registro de auditoría: quién hizo qué y cuándo. Sin PII en claro.
-- Encadenado por hash (cada evento sella al anterior) => cualquier alteración se detecta.
-- -------------------------------------------------------------------------------------
CREATE SEQUENCE auditoria.evento_seq;

CREATE TABLE auditoria.evento (
    id_evento      BIGINT       PRIMARY KEY,
    fecha_hora     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_tipo     VARCHAR(10)  NOT NULL CHECK (actor_tipo IN ('usuario', 'sistema', 'suiche', 'operador')),
    actor_id       VARCHAR(64),
    accion         VARCHAR(80)  NOT NULL,
    entidad        VARCHAR(60)  NOT NULL,
    id_entidad     VARCHAR(64),
    detalle        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    hash_anterior  CHAR(64)     NOT NULL,
    hash_actual    CHAR(64)     NOT NULL UNIQUE
);
CREATE INDEX evento_entidad_idx ON auditoria.evento (entidad, id_entidad);

CREATE OR REPLACE FUNCTION auditoria.encadenar_hash() RETURNS trigger AS $$
DECLARE
    previo CHAR(64);
BEGIN
    -- Serializa las inserciones para que la cadena sea lineal.
    PERFORM pg_advisory_xact_lock(724001);
    NEW.id_evento := nextval('auditoria.evento_seq');
    SELECT hash_actual INTO previo FROM auditoria.evento ORDER BY id_evento DESC LIMIT 1;
    NEW.hash_anterior := COALESCE(previo, repeat('0', 64));
    NEW.hash_actual := encode(sha256(convert_to(
        NEW.hash_anterior || '|' || NEW.id_evento || '|' || NEW.fecha_hora::text || '|' || NEW.actor_tipo || '|' ||
        COALESCE(NEW.actor_id, '') || '|' || NEW.accion || '|' || NEW.entidad || '|' ||
        COALESCE(NEW.id_entidad, '') || '|' || NEW.detalle::text, 'UTF8')), 'hex');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER evento_encadenar BEFORE INSERT ON auditoria.evento
    FOR EACH ROW EXECUTE FUNCTION auditoria.encadenar_hash();
CREATE TRIGGER evento_inmutable BEFORE UPDATE OR DELETE ON auditoria.evento
    FOR EACH ROW EXECUTE FUNCTION auditoria.impedir_modificacion();
CREATE TRIGGER evento_sin_truncate BEFORE TRUNCATE ON auditoria.evento
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria.impedir_modificacion();
