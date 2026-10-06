-- Script semilla para usuarios en base de datos IN5AM (MySQL 8.4)
-- Ejecutar con:
-- docker exec -i delivery-mysql mysql -u root -p_odmon5Am -D IN5AM < seed-auth.sql
-- o en cliente local: mysql -u root -p_odmon5Am IN5AM < seed-auth.sql

USE IN5AM;

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
