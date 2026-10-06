#!/bin/bash
set -e

echo "Creating databases..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${POSTGRES_DB:-postgres}" <<-EOSQL
    SELECT 'CREATE DATABASE auth_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
    SELECT 'CREATE DATABASE catalogo_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogo_db')\gexec
    SELECT 'CREATE DATABASE pedidos_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pedidos_db')\gexec
EOSQL

echo "Databases created successfully."
