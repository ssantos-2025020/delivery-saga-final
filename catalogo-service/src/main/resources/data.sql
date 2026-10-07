-- ============================================================================
-- Datos iniciales (idempotentes: ejecutables en cada arranque sin duplicar).
-- Se ejecutan DESPUES de la DDL de Hibernate (spring.jpa.defer-datasource-initialization).
-- Sintaxis INSERT ... SELECT ... FROM comercio c WHERE ... AND NOT EXISTS (...)
-- valida para PostgreSQL y MySQL.
--
-- Los usuarios (1 ADMIN, 1 REPARTIDOR, 1 CLIENTE) se crean en runtime por el
-- auth-service (DataSeeder) usando BCryptPasswordEncoder.
-- ============================================================================

-- Comercios (categorias RESTAURANTE / SUPERMERCADO / FARMACIA)
INSERT INTO comercio (nombre, categoria, direccion, abierto)
SELECT 'Burger Palace', 'RESTAURANTE', 'Av. Reforma 1-23', true
WHERE NOT EXISTS (SELECT 1 FROM comercio WHERE nombre = 'Burger Palace');

INSERT INTO comercio (nombre, categoria, direccion, abierto)
SELECT 'Sushi Express', 'RESTAURANTE', 'Zona Viva 5-67', true
WHERE NOT EXISTS (SELECT 1 FROM comercio WHERE nombre = 'Sushi Express');

INSERT INTO comercio (nombre, categoria, direccion, abierto)
SELECT 'SuperMax', 'SUPERMERCADO', 'Calle 12 3-45', true
WHERE NOT EXISTS (SELECT 1 FROM comercio WHERE nombre = 'SuperMax');

INSERT INTO comercio (nombre, categoria, direccion, abierto)
SELECT 'Farmacia Vida', 'FARMACIA', '6a Avenida 8-90', true
WHERE NOT EXISTS (SELECT 1 FROM comercio WHERE nombre = 'Farmacia Vida');

INSERT INTO comercio (nombre, categoria, direccion, abierto)
SELECT 'Panaderia Central', 'SUPERMERCADO', 'Centro, 4a Calle 2-10', false
WHERE NOT EXISTS (SELECT 1 FROM comercio WHERE nombre = 'Panaderia Central');

-- Productos con stock variado (los ids de comercio se resuelven por nombre)
INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Hamburguesa Clasica', 15.00, 50, true, c.id
FROM comercio c
WHERE c.nombre = 'Burger Palace'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Hamburguesa Clasica' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Papas Fritas', 8.00, 100, true, c.id
FROM comercio c
WHERE c.nombre = 'Burger Palace'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Papas Fritas' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Refresco', 5.00, 3, true, c.id
FROM comercio c
WHERE c.nombre = 'Burger Palace'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Refresco' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Roll de Salmon', 18.00, 25, true, c.id
FROM comercio c
WHERE c.nombre = 'Sushi Express'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Roll de Salmon' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Nigiri Salmon', 12.00, 0, false, c.id
FROM comercio c
WHERE c.nombre = 'Sushi Express'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Nigiri Salmon' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Arroz 5kg', 45.00, 20, true, c.id
FROM comercio c
WHERE c.nombre = 'SuperMax'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Arroz 5kg' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Leche 1L', 8.50, 15, true, c.id
FROM comercio c
WHERE c.nombre = 'SuperMax'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Leche 1L' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Paracetamol 500mg', 10.00, 30, true, c.id
FROM comercio c
WHERE c.nombre = 'Farmacia Vida'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Paracetamol 500mg' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Alcohol 500ml', 15.00, 40, true, c.id
FROM comercio c
WHERE c.nombre = 'Farmacia Vida'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Alcohol 500ml' AND p.comercio_id = c.id);

INSERT INTO producto (nombre, precio, stock, disponible, comercio_id)
SELECT 'Baguette', 6.00, 0, false, c.id
FROM comercio c
WHERE c.nombre = 'Panaderia Central'
  AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.nombre = 'Baguette' AND p.comercio_id = c.id);

-- Los productos sembrados por SQL dejan la columna version (@Version de Hibernate) en NULL;
-- la primera modificacion de stock fallaria al incrementar la version. Normalizar a 0.
UPDATE producto SET version = 0 WHERE version IS NULL;
