# Delivery API - Microservices Architecture

Sistema de pedidos de delivery implementado con microservicios y patrón Saga para control de stock bajo alta concurrencia.

## Arquitectura

```
┌─────────────┐
│ API Gateway │ (8080)
└──────┬──────┘
       │
   ┌───┴──────────────────────┐
   │                          │
┌──▼────┐  ┌─────────┐  ┌─────▼────┐
│ Auth  │  │Catalogo │  │ Pedidos  │
│(8081) │  │ (8082)  │  │  (8083)  │
└───────┘  └────┬────┘  └─────┬────┘
                │              │
                └──────┬───────┘
                       │
                ┌──────▼──────┐
                │ PostgreSQL  │
                │  (3 BDs)    │
                └─────────────┘
```

## Servicios

- **api-gateway**: Enrutamiento, validación JWT, Correlation ID
- **auth-service**: Registro, login, generación JWT
- **catalogo-service**: Comercios, productos, gestión de stock con bloqueo pesimista
- **pedidos-service**: Pedidos, orquestación Saga, compensación

## Bases de Datos

- `auth_db`: Usuarios
- `catalogo_db`: Comercios, productos, reservas de stock
- `pedidos_db`: Pedidos, detalles de pedido

## Patrón Saga

1. Pedidos-service genera `reservaId` único
2. Llama a catalogo-service para reservar stock (bloqueo pesimista)
3. Calcula total (+ Q20.00 delivery)
4. Guarda pedido y detalles
5. Confirma reserva
6. Si falla: compensación automática (liberar stock)
7. Reconciliación programada cada 5 minutos para reservas huérfanas

## Características de Resistencia a Estrés

- ✅ Bloqueo pesimista ordenado por ID (evita deadlocks)
- ✅ Idempotencia en liberación de stock
- ✅ Resilience4j: Circuit Breaker + Retry
- ✅ Reconciliación automática de reservas huérfanas
- ✅ Rate limiting en login (5 intentos/min)
- ✅ Correlation ID para trazabilidad
- ✅ Metrics con Micrometer

## Credenciales de Desarrollo

- **PostgreSQL**: postgres / admin
- **Internal API Key**: internal-api-key-secret
- **JWT Secret**: your-secret-key-minimum-32-characters

## Ejecutar con Docker

```bash
docker-compose up -d
```

## Health Checks

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## Endpoints Públicos

- `POST /api/v1/auth/registro` - Registrar usuario
- `POST /api/v1/auth/login` - Login (retorna JWT)
- `GET /api/v1/comercios` - Listar comercios
- `POST /api/v1/pedidos` - Crear pedido (requiere JWT)
- `GET /api/v1/pedidos` - Listar pedidos (requiere JWT)
- `POST /api/v1/pedidos/{id}/cancelar` - Cancelar pedido (requiere JWT)

## Endpoints Internos (no expuestos por gateway)

- `POST /internal/stock/reservar` - Reservar stock
- `POST /internal/stock/liberar` - Liberar stock
- `POST /internal/stock/confirmar` - Confirmar reserva

## Pruebas de Carga

```bash
cd k6
k6 run load-test-microservices.js
```

## Stack Tecnológico

- Java 17
- Spring Boot 3.2.0
- Spring Cloud Gateway
- PostgreSQL 15
- JWT (jjwt 0.12.3)
- Resilience4j 2.1.0
- Bucket4j 8.7.0 (rate limiting)
- Micrometer (metrics)
- Docker / Docker Compose
