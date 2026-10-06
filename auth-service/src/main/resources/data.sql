-- Datos semilla para auth_db en MySQL 8.4
-- Password 'password123' hasheada con BCrypt ($2a$10$...)

INSERT INTO usuario (email, password, rol, nombre, activo)
VALUES 
    ('admin@delivery.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'ADMIN', 'Administrador General', true),
    ('repartidor@delivery.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'REPARTIDOR', 'Repartidor Expreso', true),
    ('cliente@delivery.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'CLIENTE', 'Cliente Frecuente', true),
    ('otrocliente@delivery.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'CLIENTE', 'Otro Cliente Distinto', true)
ON DUPLICATE KEY UPDATE 
    password = VALUES(password),
    rol = VALUES(rol),
    nombre = VALUES(nombre),
    activo = VALUES(activo);
