#!/usr/bin/env bash
# ==============================================================================
# QA SMOKE TEST SUITE - API REST Sistema de Gestión de Pedidos y Delivery
# ==============================================================================
# Ejecución:
#   chmod +x smoke-test.sh
#   ./smoke-test.sh [BASE_URL]
#   Ejemplo local:  ./smoke-test.sh http://localhost:8080
#   Ejemplo docker: BASE_URL=http://localhost:8080 ./smoke-test.sh
# ==============================================================================

# Colores de terminal
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}        QA BACKEND SENIOR - SMOKE TEST DE VERIFICACIÓN Y ESPECIFICACIÓN       ${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo -e "Target URL: ${CYAN}${BASE_URL}${NC}"
echo -e "Timestamp : $(date)"
echo ""

# Verificar dependencias
command -v curl >/dev/null 2>&1 || { echo -e "${RED}ERROR: 'curl' es requerido pero no está instalado.${NC}" >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo -e "${RED}ERROR: 'jq' es requerido pero no está instalado.${NC}" >&2; exit 1; }

# Función de registro de resultados
record_result() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"
    local status="$4" # PASS or FAIL
    local details="$5"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if [ "$status" = "PASS" ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo -e " [${GREEN}OK${NC}] ${test_name}"
        if [ -n "$details" ]; then
            echo -e "      ${CYAN}Detalle:${NC} ${details}"
        fi
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo -e " [${RED}FAIL${NC}] ${test_name}"
        echo -e "      ${YELLOW}Esperado (Spec):${NC} ${expected}"
        echo -e "      ${RED}Recibido (API):${NC} ${actual}"
        if [ -n "$details" ]; then
            echo -e "      ${RED}Causa/Diagnóstico:${NC} ${details}"
        fi
    fi
}

# Tokens globales
ADMIN_TOKEN=""
REPARTIDOR_TOKEN=""
CLIENTE_TOKEN=""
CLIENTE_2_TOKEN=""

# ------------------------------------------------------------------------------
# 0. VERIFICAR HEALTH DEL GATEWAY Y MICROSERVICIOS
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> 0. Verificando disponibilidad del API Gateway...${NC}"
GATEWAY_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health" 2>/dev/null || echo "000")
if [ "$GATEWAY_HEALTH" != "200" ]; then
    echo -e "${RED}[FAIL] No se puede conectar al API Gateway en ${BASE_URL} (HTTP ${GATEWAY_HEALTH})${NC}"
    echo -e "${YELLOW}Asegúrate de que los contenedores o servicios estén activos antes de correr la prueba.${NC}"
    exit 1
fi
echo -e " [${GREEN}OK${NC}] API Gateway respondiendo (HTTP 200)"

# ------------------------------------------------------------------------------
# a. REGISTRO (201) Y EMAIL DUPLICADO (409); ROL CLIENTE INMUTABLE
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> a. Registro (201), Duplicado (409) y Forzado de Rol CLIENTE...${NC}"
RAND_ID=$((RANDOM % 90000 + 10000))
TEST_EMAIL="smoke_${RAND_ID}@delivery.com"
REG_BODY=$(cat <<EOF
{
  "email": "${TEST_EMAIL}",
  "password": "password123",
  "nombre": "Tester QA ${RAND_ID}",
  "rol": "ADMIN"
}
EOF
)

# 1) Registro inicial
REG_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/registro" \
    -H "Content-Type: application/json" -d "${REG_BODY}")
REG_CODE=$(echo "$REG_RESP" | tail -n1)
REG_BODY_RESP=$(echo "$REG_RESP" | sed '$d')

# Según la spec se espera 201 Created. La implementación actual devuelve 200 OK.
if [ "$REG_CODE" = "201" ]; then
    record_result "a.1 Registro exitoso de nuevo usuario" "HTTP 201 Created" "HTTP ${REG_CODE}" "PASS" ""
elif [ "$REG_CODE" = "200" ]; then
    record_result "a.1 Registro exitoso de nuevo usuario" "HTTP 201 Created" "HTTP 200 OK" "FAIL" "Bug en AuthController: retorna 200 OK en lugar de 201 Created"
else
    record_result "a.1 Registro exitoso de nuevo usuario" "HTTP 201 Created" "HTTP ${REG_CODE}" "FAIL" "Respuesta: ${REG_BODY_RESP}"
fi

# Verificar que el rol en el token/respuesta sea CLIENTE
ROL_OBTENIDO=$(echo "$REG_BODY_RESP" | jq -r '.rol // empty')
CLIENTE_TOKEN=$(echo "$REG_BODY_RESP" | jq -r '.token // empty')

if [ "$ROL_OBTENIDO" = "CLIENTE" ]; then
    record_result "a.2 Inmutabilidad de rol (intento de registrarse como ADMIN)" "rol == CLIENTE" "rol == ${ROL_OBTENIDO}" "PASS" "Se ignoró el rol del body y se asignó CLIENTE"
else
    record_result "a.2 Inmutabilidad de rol (intento de registrarse como ADMIN)" "rol == CLIENTE" "rol == ${ROL_OBTENIDO}" "FAIL" "El servicio permitió un rol no autorizado o no lo retornó"
fi

# 2) Email duplicado
DUP_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/registro" \
    -H "Content-Type: application/json" -d "${REG_BODY}")
DUP_CODE=$(echo "$DUP_RESP" | tail -n1)
DUP_BODY_RESP=$(echo "$DUP_RESP" | sed '$d')

# Según spec se espera 409 Conflict. Implementación actual lanza RuntimeException -> 400 Bad Request
if [ "$DUP_CODE" = "409" ]; then
    record_result "a.3 Registro con email duplicado" "HTTP 409 Conflict" "HTTP ${DUP_CODE}" "PASS" ""
elif [ "$DUP_CODE" = "400" ]; then
    record_result "a.3 Registro con email duplicado" "HTTP 409 Conflict" "HTTP 400 Bad Request" "FAIL" "Bug en GlobalExceptionHandler: mapea error de duplicado a 400 en vez de 409"
else
    record_result "a.3 Registro con email duplicado" "HTTP 409 Conflict" "HTTP ${DUP_CODE}" "FAIL" "Respuesta: ${DUP_BODY_RESP}"
fi

# Registrar un segundo cliente para pruebas de aislamiento
CLIENTE_2_EMAIL="cliente2_${RAND_ID}@delivery.com"
REG2_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/auth/registro" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${CLIENTE_2_EMAIL}\",\"password\":\"password123\",\"nombre\":\"Cliente Dos\"}")
CLIENTE_2_TOKEN=$(echo "$REG2_RESP" | jq -r '.token // empty')

# ------------------------------------------------------------------------------
# b. LOGIN CORRECTO (200) Y LOGIN PASSWORD INCORRECTA (401)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> b. Login correcto (200) y password incorrecta (401)...${NC}"
LOGIN_OK_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"password123\"}")
LOGIN_OK_CODE=$(echo "$LOGIN_OK_RESP" | tail -n1)
LOGIN_OK_BODY=$(echo "$LOGIN_OK_RESP" | sed '$d')
NEW_TOKEN=$(echo "$LOGIN_OK_BODY" | jq -r '.token // empty')

if [ "$LOGIN_OK_CODE" = "200" ] && [ -n "$NEW_TOKEN" ] && [ "$NEW_TOKEN" != "null" ]; then
    record_result "b.1 Login exitoso con credenciales válidas" "HTTP 200 con JWT" "HTTP 200 con token presente" "PASS" ""
    CLIENTE_TOKEN="$NEW_TOKEN"
else
    record_result "b.1 Login exitoso con credenciales válidas" "HTTP 200 con JWT" "HTTP ${LOGIN_OK_CODE}" "FAIL" "Respuesta: ${LOGIN_OK_BODY}"
fi

# Password incorrecta
LOGIN_BAD_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"password_erronea_qa\"}")
LOGIN_BAD_CODE=$(echo "$LOGIN_BAD_RESP" | tail -n1)
LOGIN_BAD_BODY=$(echo "$LOGIN_BAD_RESP" | sed '$d')

# Según spec se espera 401 Unauthorized. Implementación actual lanza RuntimeException -> 400 Bad Request
if [ "$LOGIN_BAD_CODE" = "401" ]; then
    record_result "b.2 Login con password incorrecta" "HTTP 401 Unauthorized" "HTTP ${LOGIN_BAD_CODE}" "PASS" ""
elif [ "$LOGIN_BAD_CODE" = "400" ]; then
    record_result "b.2 Login con password incorrecta" "HTTP 401 Unauthorized" "HTTP 400 Bad Request" "FAIL" "Bug en AuthService/GlobalExceptionHandler: credenciales inválidas retornan 400 en vez de 401"
else
    record_result "b.2 Login con password incorrecta" "HTTP 401 Unauthorized" "HTTP ${LOGIN_BAD_CODE}" "FAIL" "Respuesta: ${LOGIN_BAD_BODY}"
fi

# Obtener Tokens de ADMIN y REPARTIDOR desde datos precargados (data.sql / seed)
echo -e "${YELLOW}  -> Obteniendo tokens para ADMIN y REPARTIDOR...${NC}"
ADMIN_LOGIN=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@delivery.com","password":"password123"}')
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN" | jq -r '.token // empty')

REPARTIDOR_LOGIN=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"repartidor@delivery.com","password":"password123"}')
REPARTIDOR_TOKEN=$(echo "$REPARTIDOR_LOGIN" | jq -r '.token // empty')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
    echo -e "      ${YELLOW}[AVISO QA] admin@delivery.com no pudo autenticarse. Ejecuta: mysql -u root -p_odmon5Am IN5AM < seed-auth.sql${NC}"
fi

# ------------------------------------------------------------------------------
# c. VALIDACIÓN DE JWT: SIN TOKEN (401), TOKEN FALSO (401), TOKEN EXPIRADO (401)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> c. Verificación de JWT en Gateway (Sin token, Falso, Expirado)...${NC}"
# 1) Sin JWT
NO_JWT_RESP=$(curl -s -w "%{http_code}" -X GET "${BASE_URL}/api/v1/pedidos/mis-pedidos")
if [ "$NO_JWT_RESP" = "401" ]; then
    record_result "c.1 Petición protegida sin JWT" "HTTP 401 Unauthorized" "HTTP 401" "PASS" "Gateway bloqueó la petición sin Authorization header"
else
    record_result "c.1 Petición protegida sin JWT" "HTTP 401 Unauthorized" "HTTP ${NO_JWT_RESP}" "FAIL" ""
fi

# 2) Token falso / firma alterada
FAKE_JWT="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTYiLCJuYW1lIjoiRmFrZSBVc2VyIn0.invalid_signature_checksum"
FAKE_JWT_RESP=$(curl -s -w "%{http_code}" -X GET "${BASE_URL}/api/v1/pedidos/mis-pedidos" \
    -H "Authorization: Bearer ${FAKE_JWT}")
if [ "$FAKE_JWT_RESP" = "401" ]; then
    record_result "c.2 Petición con JWT falso/malformado" "HTTP 401 Unauthorized" "HTTP 401" "PASS" "Gateway rechazó firma inválida"
else
    record_result "c.2 Petición con JWT falso/malformado" "HTTP 401 Unauthorized" "HTTP ${FAKE_JWT_RESP}" "FAIL" ""
fi

# 3) Token expirado (JWT con exp en el pasado: 1514764800 = 2018-01-01)
EXPIRED_JWT="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJleHBpcmVkQHRlc3QuY29tIiwibm9tYnJlIjoiRXhwaXJlZCIsInJvbCI6IkNMSUVOVEUiLCJleHAiOjE1MTQ3NjQ4MDB9.x"
EXPIRED_JWT_RESP=$(curl -s -w "%{http_code}" -X GET "${BASE_URL}/api/v1/pedidos/mis-pedidos" \
    -H "Authorization: Bearer ${EXPIRED_JWT}")
if [ "$EXPIRED_JWT_RESP" = "401" ]; then
    record_result "c.3 Petición con JWT expirado" "HTTP 401 Unauthorized" "HTTP 401" "PASS" "Gateway rechazó token expirado"
else
    record_result "c.3 Petición con JWT expirado" "HTTP 401 Unauthorized" "HTTP ${EXPIRED_JWT_RESP}" "FAIL" ""
fi

# ------------------------------------------------------------------------------
# d. PERMISOS COMERCIOS: CLIENTE INTENTA POST (403), ADMIN LO CREA (201)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> d. Autorización en Comercios (POST /api/v1/comercios)...${NC}"
COMERCIO_PAYLOAD='{"nombre":"Nuevo Restaurante QA","categoria":"Comida Rápida","abierto":true}'

# CLIENTE intenta POST /comercios
CLI_COM_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${COMERCIO_PAYLOAD}")
CLI_COM_CODE=$(echo "$CLI_COM_RESP" | tail -n1)

if [ "$CLI_COM_CODE" = "403" ]; then
    record_result "d.1 CLIENTE intenta POST /comercios" "HTTP 403 Forbidden" "HTTP 403" "PASS" ""
elif [ "$CLI_COM_CODE" = "404" ] || [ "$CLI_COM_CODE" = "405" ]; then
    record_result "d.1 CLIENTE intenta POST /comercios" "HTTP 403 Forbidden" "HTTP ${CLI_COM_CODE}" "FAIL" "Endpoint POST /api/v1/comercios no existe en catalogo-service"
else
    record_result "d.1 CLIENTE intenta POST /comercios" "HTTP 403 Forbidden" "HTTP ${CLI_COM_CODE}" "FAIL" ""
fi

# ADMIN intenta POST /comercios
if [ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ]; then
    ADM_COM_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios" \
        -H "Authorization: Bearer ${ADMIN_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${COMERCIO_PAYLOAD}")
    ADM_COM_CODE=$(echo "$ADM_COM_RESP" | tail -n1)

    if [ "$ADM_COM_CODE" = "201" ]; then
        record_result "d.2 ADMIN crea comercio" "HTTP 201 Created" "HTTP 201" "PASS" ""
    elif [ "$ADM_COM_CODE" = "404" ] || [ "$ADM_COM_CODE" = "405" ]; then
        record_result "d.2 ADMIN crea comercio" "HTTP 201 Created" "HTTP ${ADM_COM_CODE}" "FAIL" "catalogo-service no implementa POST /api/v1/comercios"
    else
        record_result "d.2 ADMIN crea comercio" "HTTP 201 Created" "HTTP ${ADM_COM_CODE}" "FAIL" ""
    fi
else
    record_result "d.2 ADMIN crea comercio" "HTTP 201 Created" "SKIP (Sin token ADMIN)" "FAIL" "Falta seed de admin@delivery.com"
fi

# ------------------------------------------------------------------------------
# e. ADMIN AGREGA PRODUCTOS (201); GET PRODUCTOS (200 / 404)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> e. Gestión de Productos y Validación 404 en Comercio inexistente...${NC}"
PROD_PAYLOAD='{"nombre":"Hamburguesa Triple QA","precio":30.00,"stock":50}'

if [ -n "$ADMIN_TOKEN" ] && [ "$ADMIN_TOKEN" != "null" ]; then
    ADM_PRD_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/comercios/1/productos" \
        -H "Authorization: Bearer ${ADMIN_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${PROD_PAYLOAD}")
    ADM_PRD_CODE=$(echo "$ADM_PRD_RESP" | tail -n1)

    if [ "$ADM_PRD_CODE" = "201" ]; then
        record_result "e.1 ADMIN agrega producto a comercio" "HTTP 201 Created" "HTTP 201" "PASS" ""
    elif [ "$ADM_PRD_CODE" = "404" ] || [ "$ADM_PRD_CODE" = "405" ]; then
        record_result "e.1 ADMIN agrega producto a comercio" "HTTP 201 Created" "HTTP ${ADM_PRD_CODE}" "FAIL" "catalogo-service no implementa creación de productos vía API"
    else
        record_result "e.1 ADMIN agrega producto a comercio" "HTTP 201 Created" "HTTP ${ADM_PRD_CODE}" "FAIL" ""
    fi
else
    record_result "e.1 ADMIN agrega producto a comercio" "HTTP 201 Created" "SKIP (Sin token ADMIN)" "FAIL" ""
fi

# GET productos comercio 1 (debe dar 200)
GET_PRD_RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/comercios/1/productos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}")
GET_PRD_CODE=$(echo "$GET_PRD_RESP" | tail -n1)
if [ "$GET_PRD_CODE" = "200" ]; then
    record_result "e.2 GET productos de comercio existente (ID: 1)" "HTTP 200 OK" "HTTP 200" "PASS" ""
else
    record_result "e.2 GET productos de comercio existente (ID: 1)" "HTTP 200 OK" "HTTP ${GET_PRD_CODE}" "FAIL" ""
fi

# GET productos comercio inexistente (ID 999999 -> Debe dar 404)
GET_INV_RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/comercios/999999/productos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}")
GET_INV_CODE=$(echo "$GET_INV_RESP" | tail -n1)
if [ "$GET_INV_CODE" = "404" ]; then
    record_result "e.3 GET productos de comercio inexistente (ID: 999999)" "HTTP 404 Not Found" "HTTP 404" "PASS" ""
elif [ "$GET_INV_CODE" = "200" ]; then
    record_result "e.3 GET productos de comercio inexistente (ID: 999999)" "HTTP 404 Not Found" "HTTP 200 OK (vacío)" "FAIL" "PublicCatalogoController no valida existencia del comercio, retorna página vacía con 200"
else
    record_result "e.3 GET productos de comercio inexistente (ID: 999999)" "HTTP 404 Not Found" "HTTP ${GET_INV_CODE}" "FAIL" ""
fi

# ------------------------------------------------------------------------------
# f. PEDIDO CORRECTO Y PROTECCIÓN CONTRA CAMPOS INYECTADOS
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> f. Cálculo de Pedido y Protección contra Manipulación de Precios...${NC}"
# Usamos los productos de data.sql:
# Producto 1: Hamburguesa Clásica (15.00)
# Producto 2: Papas Fritas (8.00)
# Pedido: 2 x Prod 1 (30.00) + 1 x Prod 2 (8.00) + 20.00 delivery = 58.00
# Además inyectamos: precio: 0.01, total: 1.00, clienteId: 9999
INJECTED_ORDER_BODY=$(cat <<EOF
{
  "precio": 0.01,
  "total": 1.00,
  "montoTotal": 1.00,
  "clienteId": 9999,
  "items": [
    { "productoId": 1, "cantidad": 2, "precio": 0.01 },
    { "productoId": 2, "cantidad": 1, "precio": 0.01 }
  ]
}
EOF
)

ORDER_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${INJECTED_ORDER_BODY}")
ORDER_CODE=$(echo "$ORDER_RESP" | tail -n1)
ORDER_BODY_RESP=$(echo "$ORDER_RESP" | sed '$d')

LAST_ORDER_ID=$(echo "$ORDER_BODY_RESP" | jq -r '.id // empty')
LAST_ORDER_TOTAL=$(echo "$ORDER_BODY_RESP" | jq -r '.total // empty')
LAST_ORDER_CLIENT=$(echo "$ORDER_BODY_RESP" | jq -r '.clienteId // empty')

# Esperado total: 15*2 + 8*1 + 20 = 58.00
if [ "$ORDER_CODE" = "200" ] || [ "$ORDER_CODE" = "201" ]; then
    if [ "$LAST_ORDER_TOTAL" = "58.00" ] || [ "$LAST_ORDER_TOTAL" = "58" ]; then
        record_result "f.1 Cálculo de total servidor (costoEnvio=20.00 sumado)" "total == 58.00" "total == ${LAST_ORDER_TOTAL}" "PASS" "El servidor calculó el precio ignorando campos inyectados"
    else
        record_result "f.1 Cálculo de total servidor (costoEnvio=20.00 sumado)" "total == 58.00" "total == ${LAST_ORDER_TOTAL}" "FAIL" "Total no concuerda con cálculo esperado"
    fi

    if [ "$LAST_ORDER_CLIENT" != "9999" ] && [ -n "$LAST_ORDER_CLIENT" ]; then
        record_result "f.2 clienteId tomado del token JWT y no del body" "clienteId != 9999" "clienteId == ${LAST_ORDER_CLIENT}" "PASS" "Se ignoró el clienteId inyectado en el payload"
    else
        record_result "f.2 clienteId tomado del token JWT y no del body" "clienteId != 9999" "clienteId == ${LAST_ORDER_CLIENT}" "FAIL" "Vulnerabilidad: se aceptó el clienteId inyectado"
    fi
else
    record_result "f.1 Creación de pedido válido" "HTTP 200/201" "HTTP ${ORDER_CODE}" "FAIL" "Respuesta: ${ORDER_BODY_RESP}"
fi

# ------------------------------------------------------------------------------
# g. STOCK INSUFICIENTE (409) Y ROLLBACK/INTACTO
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> g. Stock Insuficiente (409) y Verificación de Stock Intacto...${NC}"
# Consultar stock actual del producto 1
PROD1_BEFORE_RESP=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/1/productos" -H "Authorization: Bearer ${CLIENTE_TOKEN}")
PROD1_STOCK_BEFORE=$(echo "$PROD1_BEFORE_RESP" | jq -r '.content[] | select(.id == 1) | .stock // 0')

# Pedido con cantidad excesiva para producto 2 (Papas Fritas, stock normal 100, pedimos 99999)
OVERSTOCK_BODY='{"items":[{"productoId":1,"cantidad":2},{"productoId":2,"cantidad":999999}]}'
OVERSTOCK_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${OVERSTOCK_BODY}")
OVERSTOCK_CODE=$(echo "$OVERSTOCK_RESP" | tail -n1)

if [ "$OVERSTOCK_CODE" = "409" ]; then
    record_result "g.1 Rechazo por stock insuficiente" "HTTP 409 Conflict" "HTTP 409" "PASS" ""
elif [ "$OVERSTOCK_CODE" = "400" ]; then
    record_result "g.1 Rechazo por stock insuficiente" "HTTP 409 Conflict" "HTTP 400 Bad Request" "FAIL" "Bug: PedidosService lanza RuntimeException -> GlobalExceptionHandler retorna 400 en vez de 409"
else
    record_result "g.1 Rechazo por stock insuficiente" "HTTP 409 Conflict" "HTTP ${OVERSTOCK_CODE}" "FAIL" ""
fi

# Verificar que el stock del producto 1 no haya disminuido
PROD1_AFTER_RESP=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/1/productos" -H "Authorization: Bearer ${CLIENTE_TOKEN}")
PROD1_STOCK_AFTER=$(echo "$PROD1_AFTER_RESP" | jq -r '.content[] | select(.id == 1) | .stock // 0')

if [ "$PROD1_STOCK_BEFORE" = "$PROD1_STOCK_AFTER" ]; then
    record_result "g.2 Invariante atómica: Stock de producto A permanece intacto" "Stock ${PROD1_STOCK_BEFORE}" "Stock ${PROD1_STOCK_AFTER}" "PASS" "Transacción atómica garantizó integridad"
else
    record_result "g.2 Invariante atómica: Stock de producto A permanece intacto" "Stock ${PROD1_STOCK_BEFORE}" "Stock ${PROD1_STOCK_AFTER}" "FAIL" "Inconsistencia: Stock cambió a pesar del fallo"
fi

# ------------------------------------------------------------------------------
# h. AISLAMIENTO DE PEDIDOS (mis-pedidos SOLO DEL CLIENTE AUTENTICADO)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> h. Aislamiento Multi-inquilino en /mis-pedidos...${NC}"
MY_ORDERS_CLIENT2=$(curl -s -X GET "${BASE_URL}/api/v1/pedidos/mis-pedidos" \
    -H "Authorization: Bearer ${CLIENTE_2_TOKEN}")
CONTAINS_LAST_ORDER=$(echo "$MY_ORDERS_CLIENT2" | jq --arg id "$LAST_ORDER_ID" '[.[] | select(.id == ($id | tonumber))] | length')

if [ "$CONTAINS_LAST_ORDER" = "0" ] || [ -z "$CONTAINS_LAST_ORDER" ]; then
    record_result "h.1 /mis-pedidos aísla pedidos entre clientes" "0 pedidos ajenos" "0 encontrados en cliente 2" "PASS" ""
else
    record_result "h.1 /mis-pedidos aísla pedidos entre clientes" "0 pedidos ajenos" "${CONTAINS_LAST_ORDER} encontrados" "FAIL" "Fuga de privacidad: Cliente 2 ve pedidos de Cliente 1"
fi

# ------------------------------------------------------------------------------
# i. DISPONIBLES: REPARTIDOR/ADMIN (200), CLIENTE (403)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> i. Autorización en /api/v1/pedidos/disponibles...${NC}"
# CLIENTE intenta ver disponibles
CLI_DISP_RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/pedidos/disponibles" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}")
CLI_DISP_CODE=$(echo "$CLI_DISP_RESP" | tail -n1)

if [ "$CLI_DISP_CODE" = "403" ]; then
    record_result "i.1 CLIENTE consulta pedidos disponibles" "HTTP 403 Forbidden" "HTTP 403" "PASS" ""
elif [ "$CLI_DISP_CODE" = "404" ]; then
    record_result "i.1 CLIENTE consulta pedidos disponibles" "HTTP 403 Forbidden" "HTTP 404 Not Found" "FAIL" "Endpoint /api/v1/pedidos/disponibles no implementado en pedidos-service"
else
    record_result "i.1 CLIENTE consulta pedidos disponibles" "HTTP 403 Forbidden" "HTTP ${CLI_DISP_CODE}" "FAIL" ""
fi

# REPARTIDOR intenta ver disponibles
if [ -n "$REPARTIDOR_TOKEN" ] && [ "$REPARTIDOR_TOKEN" != "null" ]; then
    REP_DISP_RESP=$(curl -s -w "\n%{http_code}" -X GET "${BASE_URL}/api/v1/pedidos/disponibles" \
        -H "Authorization: Bearer ${REPARTIDOR_TOKEN}")
    REP_DISP_CODE=$(echo "$REP_DISP_RESP" | tail -n1)

    if [ "$REP_DISP_CODE" = "200" ]; then
        record_result "i.2 REPARTIDOR consulta pedidos disponibles" "HTTP 200 OK" "HTTP 200" "PASS" ""
    elif [ "$REP_DISP_CODE" = "404" ]; then
        record_result "i.2 REPARTIDOR consulta pedidos disponibles" "HTTP 200 OK" "HTTP 404 Not Found" "FAIL" "Endpoint no implementado"
    else
        record_result "i.2 REPARTIDOR consulta pedidos disponibles" "HTTP 200 OK" "HTTP ${REP_DISP_CODE}" "FAIL" ""
    fi
else
    record_result "i.2 REPARTIDOR consulta pedidos disponibles" "HTTP 200 OK" "SKIP (Sin token REPARTIDOR)" "FAIL" ""
fi

# ------------------------------------------------------------------------------
# j. MÁQUINA DE ESTADOS / TRANSICIONES DE PEDIDO
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> j. Máquina de Estados y Transiciones de Pedido...${NC}"
# La spec indica:
# PENDIENTE -> EN_CAMINO (400)
# PENDIENTE -> EN_PREPARACION (200) -> EN_CAMINO (200) -> ENTREGADO (200)
# ENTREGADO -> PENDIENTE (400)
if [ -n "$LAST_ORDER_ID" ]; then
    TRANS_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "${BASE_URL}/api/v1/pedidos/${LAST_ORDER_ID}/estado" \
        -H "Authorization: Bearer ${ADMIN_TOKEN}" \
        -H "Content-Type: application/json" \
        -d '{"nuevoEstado":"EN_CAMINO"}')
    TRANS_CODE=$(echo "$TRANS_RESP" | tail -n1)

    if [ "$TRANS_CODE" = "400" ]; then
        record_result "j.1 Transición inválida PENDIENTE -> EN_CAMINO" "HTTP 400 Bad Request" "HTTP 400" "PASS" ""
    elif [ "$TRANS_CODE" = "404" ] || [ "$TRANS_CODE" = "405" ]; then
        record_result "j.1 Transición inválida PENDIENTE -> EN_CAMINO" "HTTP 400 Bad Request" "HTTP ${TRANS_CODE}" "FAIL" "Endpoint PATCH /api/v1/pedidos/{id}/estado no existe en el servicio"
    else
        record_result "j.1 Transición inválida PENDIENTE -> EN_CAMINO" "HTTP 400 Bad Request" "HTTP ${TRANS_CODE}" "FAIL" ""
    fi
else
    record_result "j.1 Transiciones de estado" "HTTP 400/200" "SKIP (Sin pedido previo)" "FAIL" ""
fi

# ------------------------------------------------------------------------------
# k. CANCELACIÓN DE PEDIDOS Y CONTROL DE ACCESO
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> k. Cancelación de Pedidos y Restauración de Stock...${NC}"
if [ -n "$LAST_ORDER_ID" ]; then
    # 1) Cliente 2 intenta cancelar pedido de Cliente 1 (Debe dar 403)
    OTHER_CANCEL_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos/${LAST_ORDER_ID}/cancelar" \
        -H "Authorization: Bearer ${CLIENTE_2_TOKEN}")
    OTHER_CANCEL_CODE=$(echo "$OTHER_CANCEL_RESP" | tail -n1)

    if [ "$OTHER_CANCEL_CODE" = "403" ] || [ "$OTHER_CANCEL_CODE" = "400" ]; then
        record_result "k.1 Cancelar pedido de otro cliente rechazado" "HTTP 403 Forbidden" "HTTP ${OTHER_CANCEL_CODE}" "PASS" ""
    else
        record_result "k.1 Cancelar pedido de otro cliente rechazado" "HTTP 403 Forbidden" "HTTP ${OTHER_CANCEL_CODE}" "FAIL" ""
    fi

    # Consultar stock antes de cancelar
    PROD1_PRE_CANCEL=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/1/productos" -H "Authorization: Bearer ${CLIENTE_TOKEN}" | jq -r '.content[] | select(.id == 1) | .stock // 0')

    # 2) Cliente 1 cancela su propio pedido
    OWN_CANCEL_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos/${LAST_ORDER_ID}/cancelar" \
        -H "Authorization: Bearer ${CLIENTE_TOKEN}")
    OWN_CANCEL_CODE=$(echo "$OWN_CANCEL_RESP" | tail -n1)

    if [ "$OWN_CANCEL_CODE" = "200" ]; then
        record_result "k.2 Cancelar propio pedido" "HTTP 200 OK" "HTTP 200" "PASS" ""
    else
        record_result "k.2 Cancelar propio pedido" "HTTP 200 OK" "HTTP ${OWN_CANCEL_CODE}" "FAIL" ""
    fi

    # Consultar stock después de cancelar
    PROD1_POST_CANCEL=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/1/productos" -H "Authorization: Bearer ${CLIENTE_TOKEN}" | jq -r '.content[] | select(.id == 1) | .stock // 0')

    # En el pedido f consumimos 2 unidades de Prod 1. Al cancelar debería aumentar en +2.
    # En el código actual existe el BUG CRÍTICO de que la reserva en catalogo-service queda CONFIRMADA y CatalogoService.liberarStock solo restaura si está RESERVADA.
    EXPECTED_STOCK=$((PROD1_PRE_CANCEL + 2))
    if [ "$PROD1_POST_CANCEL" -eq "$EXPECTED_STOCK" ]; then
        record_result "k.3 Restauración de stock tras cancelación" "Stock ${EXPECTED_STOCK}" "Stock ${PROD1_POST_CANCEL}" "PASS" ""
    else
        record_result "k.3 Restauración de stock tras cancelación" "Stock ${EXPECTED_STOCK}" "Stock ${PROD1_POST_CANCEL}" "FAIL" "Bug Crítico en CatalogoService.liberarStock: No restaura reservas con estado CONFIRMADA"
    fi

    # --------------------------------------------------------------------------
    # l. DOBLE CANCELACIÓN DEL MISMO PEDIDO
    # --------------------------------------------------------------------------
    echo -e "\n${CYAN}>>> l. Doble Cancelación Idempotente...${NC}"
    DOUBLE_CANCEL_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos/${LAST_ORDER_ID}/cancelar" \
        -H "Authorization: Bearer ${CLIENTE_TOKEN}")
    DOUBLE_CANCEL_CODE=$(echo "$DOUBLE_CANCEL_RESP" | tail -n1)

    if [ "$DOUBLE_CANCEL_CODE" = "400" ]; then
        record_result "l.1 Doble cancelación rechazada con 400" "HTTP 400 Bad Request" "HTTP 400" "PASS" "No permite re-cancelar pedido ya CANCELADO"
    else
        record_result "l.1 Doble cancelación rechazada con 400" "HTTP 400 Bad Request" "HTTP ${DOUBLE_CANCEL_CODE}" "FAIL" ""
    fi

    # Verificar que el stock no aumentó una segunda vez
    PROD1_DOUBLE_CHECK=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/1/productos" -H "Authorization: Bearer ${CLIENTE_TOKEN}" | jq -r '.content[] | select(.id == 1) | .stock // 0')
    if [ "$PROD1_DOUBLE_CHECK" -eq "$PROD1_POST_CANCEL" ]; then
        record_result "l.2 Stock no se duplica en segunda cancelación" "Stock sin cambios" "Stock conservado" "PASS" ""
    else
        record_result "l.2 Stock no se duplica en segunda cancelación" "Stock sin cambios" "Stock incrementó indebidamente" "FAIL" ""
    fi
fi

# ------------------------------------------------------------------------------
# m. VALIDACIONES Y MANEJO DE ERRORES (400 NUNCA 500)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}>>> m. Resistencia a Payloads Inválidos (400 vs 500)...${NC}"

# 1) JSON malformado sintácticamente
BAD_JSON_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"items": [{"productoId": 1, "cantidad": }]')
BAD_JSON_CODE=$(echo "$BAD_JSON_RESP" | tail -n1)
if [ "$BAD_JSON_CODE" = "400" ]; then
    record_result "m.1 JSON malformado sintácticamente" "HTTP 400 Bad Request" "HTTP 400" "PASS" ""
else
    record_result "m.1 JSON malformado sintácticamente" "HTTP 400 Bad Request" "HTTP ${BAD_JSON_CODE}" "FAIL" "Debe retornar 400, no 500"
fi

# 2) Array de items vacío
EMPTY_ITEMS_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"items": []}')
EMPTY_ITEMS_CODE=$(echo "$EMPTY_ITEMS_RESP" | tail -n1)
if [ "$EMPTY_ITEMS_CODE" = "400" ]; then
    record_result "m.2 Payload con lista de items vacía" "HTTP 400 Bad Request" "HTTP 400" "PASS" ""
else
    record_result "m.2 Payload con lista de items vacía" "HTTP 400 Bad Request" "HTTP ${EMPTY_ITEMS_CODE}" "FAIL" ""
fi

# 3) Cantidad 0
ZERO_QTY_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"items": [{"productoId": 1, "cantidad": 0}]}')
ZERO_QTY_CODE=$(echo "$ZERO_QTY_RESP" | tail -n1)
if [ "$ZERO_QTY_CODE" = "400" ]; then
    record_result "m.3 Item con cantidad 0" "HTTP 400 Bad Request" "HTTP 400" "PASS" ""
else
    record_result "m.3 Item con cantidad 0" "HTTP 400 Bad Request" "HTTP ${ZERO_QTY_CODE}" "FAIL" ""
fi

# 4) Cantidad negativa
NEG_QTY_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/pedidos" \
    -H "Authorization: Bearer ${CLIENTE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"items": [{"productoId": 1, "cantidad": -5}]}')
NEG_QTY_CODE=$(echo "$NEG_QTY_RESP" | tail -n1)
if [ "$NEG_QTY_CODE" = "400" ]; then
    record_result "m.4 Item con cantidad negativa (-5)" "HTTP 400 Bad Request" "HTTP 400" "PASS" ""
else
    record_result "m.4 Item con cantidad negativa (-5)" "HTTP 400 Bad Request" "HTTP ${NEG_QTY_CODE}" "FAIL" ""
fi

# ------------------------------------------------------------------------------
# RESUMEN FINAL
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${BLUE}                           RESUMEN DE AUDITORÍA QA                            ${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo -e "Total Casos Ejecutados : ${TOTAL_TESTS}"
echo -e "Casos Exitosos (OK)    : ${GREEN}${PASSED_TESTS}${NC}"
echo -e "Casos Fallidos (FAIL)  : ${RED}${FAILED_TESTS}${NC}"

if [ "$FAILED_TESTS" -eq 0 ]; then
    echo -e "\n${GREEN}>>> RESULTADO: TODOS LOS TESTS PASARON EXITOSAMENTE (EXIT 0)${NC}\n"
    exit 0
else
    echo -e "\n${RED}>>> RESULTADO: SE DETECTARON DISCREPANCIAS O BUGS RESPECTO A LA ESPECIFICACIÓN (EXIT 1)${NC}\n"
    exit 1
fi
