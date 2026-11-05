-- =====================================================================================
-- Ahorra+ · V9 · Catálogos y datos de referencia del MVP
-- Instituciones FICTICIAS (coinciden con el simulador de mocks/simuladores).
-- =====================================================================================

INSERT INTO consolidacion.moneda (codigo, nombre, tipo, decimales) VALUES
    ('VES',  'Bolívar digital',      'fiat',       2),
    ('USD',  'Dólar estadounidense', 'fiat',       2),
    ('USDT', 'Tether (USDT)',        'stablecoin', 2);

INSERT INTO consolidacion.institucion_financiera (codigo_integracion, nombre, tipo, estatus_integracion, codigo_sudeban, tipo_plataforma) VALUES
    ('0901', 'Banco Ávila',          'banco',             'sandbox', '0901', NULL),
    ('0902', 'Banco Orinoco',        'banco',             'sandbox', '0902', NULL),
    ('0903', 'Banco Catatumbo',      'banco',             'sandbox', '0903', NULL),
    ('0904', 'Banco Roraima',        'banco',             'sandbox', '0904', NULL),
    ('C001', 'CriptoAndes Exchange', 'plataforma_cripto', 'sandbox', NULL,   'exchange'),
    ('C002', 'Billetera Tepuy',      'plataforma_cripto', 'sandbox', NULL,   'billetera');

-- Bancos aliados: pagan fee por cliente puntual y financian puntos
INSERT INTO consolidacion.alianza_bancaria (id_institucion, fecha_inicio, fee_cliente_puntual_cts, puntos_por_pago_puntual, correo_responsable)
SELECT id_institucion, DATE '2026-01-01', fee, pts, correo
  FROM (VALUES ('0901', 30000, 150, 'alianzas@bancoavila.test'),
               ('0902', 50000, 200, 'alianzas@bancoorinoco.test'),
               ('0904', 45000, 180, 'alianzas@bancororaima.test')) AS a(cod, fee, pts, correo)
  JOIN consolidacion.institucion_financiera i ON i.codigo_integracion = a.cod;

-- Comisión por pago de TDC: Bs 20,00 fijo + 0,20 %
INSERT INTO pagos.tarifa_comision (monto_fijo_cts, porcentaje_pb, fecha_inicio) VALUES (2000, 20, DATE '2026-01-01');

-- Tramos de racha: multiplicador de puntos y días colchón por mes calendario
INSERT INTO cofre.multiplicador_racha (dias_minimos, multiplicador_x100, dias_colchon_otorgados, fecha_inicio) VALUES
    (0,  100, 2, DATE '2026-01-01'),
    (7,  125, 3, DATE '2026-01-01'),
    (30, 150, 4, DATE '2026-01-01'),
    (90, 200, 5, DATE '2026-01-01');

INSERT INTO puntos.nivel_fidelidad (nombre, puntos_minimos, orden) VALUES
    ('Base', 0, 1), ('Gold', 5000, 2), ('Platinum', 20000, 3);

INSERT INTO puntos.cuenta_puntos (tipo, fecha_apertura) VALUES ('sistema', now());

INSERT INTO puntos.comercio_aliado (rif, razon_social, categoria, estatus) VALUES
    ('J-50000001-1', 'Panadería La Esquina Dorada',  'alimentos',  'activo'),
    ('J-50000002-2', 'Farmacia Salud Total',         'salud',      'activo'),
    ('J-50000003-3', 'TecnoMundo Accesorios',        'tecnologia', 'activo'),
    ('J-50000004-4', 'Cines del Valle',              'ocio',       'activo');

INSERT INTO puntos.recompensa (id_comercio, id_nivel_minimo, descripcion, costo_puntos, fecha_inicio, stock_disponible)
SELECT c.id_comercio, n.id_nivel, r.descripcion, r.costo, DATE '2026-01-01', r.stock
  FROM (VALUES ('J-50000001-1', 'Base',     'Café + cachito de jamón',             300,  500),
               ('J-50000002-2', 'Base',     '10 % de descuento en tu compra',      800,  300),
               ('J-50000004-4', 'Gold',     'Entrada 2x1 de lunes a jueves',       2500, 100),
               ('J-50000003-3', 'Platinum', 'Cargador rápido USB-C',               9000, 25)) AS r(rif, nivel, descripcion, costo, stock)
  JOIN puntos.comercio_aliado c ON c.rif = r.rif
  JOIN puntos.nivel_fidelidad n ON n.nombre = r.nivel;

INSERT INTO perfilia.instrumento_ahorro (nombre, nivel_riesgo) VALUES
    ('Bs', 'medio'), ('USD', 'bajo'), ('stablecoin', 'medio'), ('plazo fijo', 'bajo');

INSERT INTO perfilia.version_modelo_ia (version_reglas, version_modelo, fecha_publicacion, estatus)
VALUES ('reglas-1.2', 'modelo-0.3', DATE '2026-10-15', 'activa');
