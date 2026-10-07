# Delivery Microservices (Saga)

Sistema de delivery estilo PedidosYa reconstruido como **microservicios** con
patrón **Saga** (coreografía vía HTTP) para la gestión de stock. Monorepo Maven.

## Arquitectura

```
                    ┌──────────────────────────┐
  clientes ───────► │  api-gateway    :8080    │
                    └───────┬───────┬──────────┘
               JWT (X-User-*)│       │
              ┌─────────────▼──┐  ┌─▼─────────────┐
              │ auth-service   │  │ catalogo-     │
              │     :8081      │  │ service :8082 │
              │  (usuarios,    │  │ (comercios,   │
              │   JWT)         │  │  stock +      │
              └────────────────┘  │  reservas)    │
                                  └─▲─────────────┘
                reservar/confirmar/ │ /internal/stock (X-Internal-API-Key)
                                  ┌─┴─────────────┐
                                  │ pedidos-      │
                                  │ service :8083 │
                                  └───────────────┘
```

- **API Gateway** (`spring-cloud-gateway`): valida el JWT, inyecta las cabeceras
  `X-User-Id`, `X-User-Rol`, `X-User-Name` y las propaga a los servicios.
  Endpoints públicos: `/api/v1/auth/**`, `/registro`, `GET /api/v1/comercios/**`
  y health.
- **auth-service**: usuarios, login, registro idempotente (`/register` y
  `/registro`), emisión de JWT (`app.jwt.secret`).
- **catalogo-service**: comercios y productos con stock. Expone un contrato
  interno (`/internal/stock/reservar|confirmar|liberar`) protegido con
  `X-Internal-API-Key`. Las reservas expiran a los 5 minutos (tarea
  programada) y se usa bloqueo pesimista.
- **pedidos-service**: crea pedidos ejecutando la saga (reservar → guardar →
  confirmar; sobre fallo de guardado o cancelación → liberar). Modelo escalar:
  sin FKs cruzadas entre bases de datos. Usa Resilience4j
  (`@CircuitBreaker` + `@Retry`) para el cliente HTTP hacia catálogo.
- **common**: enums, DTOs y excepciones compartidos.

Reglas de negocio (idénticas al monolito): total = Σ(precio catálogo × cantidad)
+ Q20.00; cancelación solo en `PENDIENTE` y restaura stock; transiciones
estrictas `PENDIENTE → EN_PREPARACION → EN_CAMINO → ENTREGADO`; un repartidor se
auto-asigna en su primera transición.

## Credenciales de prueba (seed en auth-service)

|| Rol        | Email                     | Password        ||
||------------|---------------------------|-----------------||
|| ADMIN      | admin@fastorder.com       | Admin123*       ||
|| REPARTIDOR | repartidor@fastorder.com  | Repartidor123*  ||
|| CLIENTE    | cliente@fastorder.com     | Cliente123*     ||

## Levantar

### Opción A: Docker Compose

```bash
docker compose up --build
```

- `db` -> PostgreSQL 15 (role `delivery`/`admin`, bases `auth_db`,
  `catalogo_db`, `pedidos_db`)
- `api-gateway` -> `http://localhost:8080` (único puerto expuesto)

### Opción B: PostgreSQL local + Maven (Windows)

```bash
./init-db.sh                  # crea rol delivery + 3 bases (o docker compose up -d db)
mvn -N install && mvn -pl common install -DskipTests
mvn -pl auth-service spring-boot:run      # :8081
mvn -pl catalogo-service spring-boot:run  # :8082
mvn -pl pedidos-service spring-boot:run   # :8083
mvn -pl api-gateway spring-boot:run       # :8080
```

O un solo paso con el script PowerShell: `powershell -ExecutionPolicy Bypass -File run-local.ps1`.

## Endpoints (vía gateway http://localhost:8080)

|| Método | Ruta                              | Acceso           ||
||--------|-----------------------------------|------------------||
|| POST   | /api/v1/auth/register             | Público          ||
|| POST   | /api/v1/auth/registro             | Público (alias)  ||
|| POST   | /api/v1/auth/login                | Público          ||
|| GET    | /api/v1/comercios                 | Público (`?categoria=`) ||
|| POST   | /api/v1/comercios                 | ADMIN            ||
|| GET    | /api/v1/comercios/{id}            | Autenticado      ||
|| GET    | /api/v1/comercios/{id}/productos  | Autenticado (`?soloDisponibles=`) ||
|| POST   | /api/v1/comercios/{id}/productos  | ADMIN            ||
|| POST   | /api/v1/pedidos                   | CLIENTE          ||
|| GET    | /api/v1/pedidos/mis-pedidos       | CLIENTE          ||
|| GET    | /api/v1/pedidos/disponibles       | REPARTIDOR/ADMIN ||
|| PATCH  | /api/v1/pedidos/{id}/estado       | REPARTIDOR/ADMIN ||
|| PATCH  | /api/v1/pedidos/{id}/cancelar     | CLIENTE/ADMIN    ||
|| POST   | /api/v1/pedidos/{id}/cancelar     | CLIENTE/ADMIN (alias) ||

Autenticación: `Authorization: Bearer <token>`. Al crear pedido el body acepta
`"productos"` o `"items"`.

## Tests

```bash
mvn -N install && mvn -pl common install -DskipTests
mvn -pl auth-service test && mvn -pl catalogo-service test && mvn -pl pedidos-service test
```

Los tres módulos tienen suites `MockMvc` sobre H2 (perfil `test`) que cubren
registro/login, autorización por rol, total de pedido, 409 por stock
insuficiente sin mutación, cancelación con liberación de stock y transiciones
inválidas. En pedidos el `CatalogoClient` se simula con `@MockBean`.

## Pruebas de aceptación y carga

```bash
./test-api3.sh                              # E2E contra el gateway
k6 run k6/load-test.js                      # ramp hasta 50 VUs
k6 run k6/concurrency-test.js               # 50 VUs concurrentes creando pedidos
```

## Configuración

Variables de entorno (todas con defaults en cada `application.yml`):
`JWT_SECRET`, `JWT_EXPIRATION_MS`, `INTERNAL_API_KEY`, `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `AUTH_SERVICE_URL`, `CATALOGO_SERVICE_URL`,
`PEDIDOS_SERVICE_URL`, `CATALOGO_URL`. El secret JWT debe coincidir entre
**auth-service** y **api-gateway**.
