# FastOrder API - Sistema de Gestión de Pedidos y Delivery

API REST construida con Spring Boot 3.x, Spring Security 6, Spring Data JPA, PostgreSQL y JWT (JJWT 0.12.x), diseñada para soportar alta concurrencia, control transaccional estricto de inventario y validación mediante el script de pruebas automatizado `test-api3.sh`.

---

## 1. Requisitos Previos

- **Java Development Kit (JDK)**: Versión 17 o superior.
- **Maven**: 3.8+ (o utilizar el wrapper `./mvnw` / `.\mvnw.cmd` incluido).
- **PostgreSQL**: Versión 15 o 16 (para ejecución local).
- **Docker y Docker Compose**: (opcional para despliegue en contenedores).
- **Herramientas de prueba de terminal**:
  - `curl` (para realizar peticiones HTTP)
  - `jq` (procesador JSON por línea de comandos)
  - `apache2-utils` (`ab` - Apache Benchmark, opcional para pruebas de estrés)
  - Git Bash o WSL (en entornos Windows)

---

## 2. Configuración de Base de Datos PostgreSQL Local

Si ejecutas el proyecto localmente sin Docker, inicializa PostgreSQL con los siguientes comandos desde tu cliente `psql`:

```sql
-- 1. Crear la base de datos
CREATE DATABASE fastorder_db;

-- 2. (Opcional) Si necesitas crear o verificar el usuario postgres
CREATE USER postgres WITH ENCRYPTED PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE fastorder_db TO postgres;
```

---

## 3. Variables de Entorno de Configuración

La aplicación expone todas las propiedades sensibles mediante variables de entorno con valores por defecto para agilizar el desarrollo local:

| Variable | Descripción | Valor por Defecto Local | Valor en Docker |
| :--- | :--- | :--- | :--- |
| `DB_HOST` | Host de PostgreSQL | `localhost` | `postgres` |
| `DB_PORT` | Puerto de PostgreSQL | `5432` | `5432` |
| `DB_NAME` | Nombre de la BD | `fastorder_db` | `fastorder_db` |
| `DB_USER` | Usuario de la BD | `postgres` | `postgres` |
| `DB_PASSWORD` | Contraseña de la BD | `postgres` | `postgres` |
| `JWT_SECRET` | Clave secreta HMAC-SHA (256+ bits) | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` | (Misma) |
| `JWT_EXPIRATION_MS` | Tiempo de expiración del token | `86400000` (24 horas) | `86400000` |

---

## 4. Ejecución del Proyecto

### Opción A: Ejecución Local con Maven / Maven Wrapper

```bash
# Con Maven instalado en el sistema:
mvn spring-boot:run

# En Windows con Maven Wrapper:
.\mvnw.cmd spring-boot:run

# En Linux / macOS con Maven Wrapper:
./mvnw spring-boot:run
```

El servidor iniciará en `http://localhost:8080/api/v1`.

### Opción B: Ejecución con Docker Compose

Docker Compose orquesta un contenedor para PostgreSQL 16 y otro contenedor multi-stage para el backend:

```bash
# Construir e iniciar los servicios en segundo plano
docker compose up --build -d

# Ver los logs del backend en tiempo real
docker compose logs -f backend

# Detener los contenedores y redes
docker compose down

# Detener eliminando volúmenes de datos
docker compose down -v
```

---

## 5. Usuarios y Credenciales de Prueba

El componente `DataInitializer` inicializa las credenciales de forma idempotente con hash BCrypt:

| Rol | Email | Contraseña | Descripción |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin@fastorder.com` | `Admin123*` | Gestión de comercios, productos y supervisión de pedidos |
| **REPARTIDOR** | `repartidor@fastorder.com` | `Repartidor123*` | Asignación y despacho de pedidos listos |
| **CLIENTE** | `cliente@fastorder.com` | `Cliente123*` | Creado dinámicamente en el paso [1] de `test-api3.sh` |

---

## 6. Catálogo de Endpoints y Matriz de Permisos

Prefijo global: `/api/v1`

| Método | Endpoint | Rol Requerido | Código Éxito | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/auth/register` | Público | `201 Created` | Registro de clientes (rol forzado a `CLIENTE`) |
| `POST` | `/auth/login` | Público | `200 OK` | Autenticación (devuelve `token`, `accessToken`, etc.) |
| `GET` | `/comercios` | Autenticado (Cualquiera) | `200 OK` | Lista de comercios abiertos (filtro `?categoria=`) |
| `POST` | `/comercios` | `ADMIN` | `201 Created` | Alta de comercio (devuelve `id` en la raíz) |
| `GET` | `/comercios/{id}/productos` | Autenticado (Cualquiera) | `200 OK` | Productos disponibles del comercio |
| `POST` | `/comercios/{id}/productos` | `ADMIN` | `201 Created` | Alta de producto en el comercio (`id` en la raíz) |
| `POST` | `/pedidos` | `CLIENTE` | `201 Created` | Creación de pedido transaccional con bloqueo de stock |
| `GET` | `/pedidos/mis-pedidos` | `CLIENTE` | `200 OK` | Historial de pedidos del cliente autenticado |
| `GET` | `/pedidos/disponibles` | `ADMIN` / `REPARTIDOR` | `200 OK` | Pedidos activos para despacho o entrega |
| `PATCH` | `/pedidos/{id}/estado` | `ADMIN` / `REPARTIDOR` | `200 OK` | Transición de estado (`EN_PREPARACION`, etc.) |
| `PATCH` | `/pedidos/{id}/cancelar`| `CLIENTE` / `ADMIN` | `200 OK` | Cancelación de pedido y rollback de stock |

---

## 7. Ejemplos de Peticiones con `curl`

### 1. Registro de Cliente
```bash
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Juan Perez",
    "direccion": "Avenida Reforma 10-00",
    "telefono": "55551234",
    "email": "juan@fastorder.com",
    "password": "Password123*"
  }'
```

### 2. Login y Obtención de JWT
```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@fastorder.com",
    "password": "Admin123*"
  }'
```

### 3. Listado de Comercios Autenticado
```bash
curl -i -X GET http://localhost:8080/api/v1/comercios \
  -H "Authorization: Bearer <TU_TOKEN_JWT>"
```

---

## 8. Ejecución del Script de Evaluación (`test-api3.sh`)

El script `test-api3.sh` valida la suite de autenticación, autorización 403, creación de recursos y pruebas de concurrencia.

### En Linux / macOS
```bash
# Dar permisos de ejecución
chmod +x test-api3.sh

# Ejecutar el script contra la API en ejecución
./test-api3.sh
```

### En Windows (Git Bash / WSL)
1. Abrir **Git Bash** o **WSL**.
2. Asegurar terminadores de línea Unix:
```bash
dos2unix test-api3.sh || sed -i -e 's/\r$//' test-api3.sh
chmod +x test-api3.sh
./test-api3.sh
```

---

## 9. Solución de Problemas Comunes

1. **Error: `Port 8080 is already in use`**:
   - Hay otro servicio ocupando el puerto 8080. Identifícalo y terminalo:
     - En Windows (PowerShell): `Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process -Force`
     - En Linux: `lsof -ti :8080 | xargs kill -9`
2. **Error de conexión a PostgreSQL (`Connection refused`)**:
   - Verifica que el servicio de base de datos esté corriendo en el puerto 5432 y que la base `fastorder_db` haya sido creada.
3. **Error `command not found: jq` en el script**:
   - En Debian/Ubuntu: `sudo apt-get install jq`
   - En macOS: `brew install jq`
   - En Windows: Descargar el binario `jq.exe` y colocarlo en el PATH o en `C:\Program Files\Git\usr\bin`.
4. **Error `\r: command not found` al ejecutar `test-api3.sh`**:
   - El script tiene terminaciones de línea estilo Windows (CRLF). Ejecuta: `sed -i -e 's/\r$//' test-api3.sh`.
