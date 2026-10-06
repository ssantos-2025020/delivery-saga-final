-- Script de inicialización para MySQL 8.4
-- Crea la base de datos IN5AM con motor InnoDB y charset utf8mb4

CREATE DATABASE IF NOT EXISTS IN5AM CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Bases de datos alternativas por servicio (si se desean separar en el futuro)
CREATE DATABASE IF NOT EXISTS auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS catalogo_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS pedidos_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
