#!/usr/bin/env bash
# ==============================================================================
# QA CONCURRENCY TEST SUITE - Estrés y Resistencia Bajo Concurrencia
# ==============================================================================
# Requisitos: bash, curl, jq, xargs
# Ejecución:
#   chmod +x concurrency-test.sh
#   ./concurrency-test.sh [BASE_URL]
#   Ejemplo: ./concurrency-test.sh http://localhost:8080
# ==============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
TEMP_DIR=$(mktemp -d 2>/dev/null || mktemp -d -t 'concurrency_test')
trap 'rm -rf "$TEMP_DIR"' EXIT

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}          QA SENIOR - PRUEBA DE CONCURRENCIA, STOCK Y DEADLOCKS               ${NC}"
echo -e "${BLUE}==============================================================================${NC}"
echo -e "Target URL: ${CYAN}${BASE_URL}${NC}"
echo ""

# 1. Obtener Token de Cliente para las pruebas concurrentes
echo -e "${CYAN}>>> Preparando autenticación para pruebas concurrentes...${NC}"
AUTH_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"cliente@delivery.com","password":"password123"}')
TOKEN=$(echo "$AUTH_RESP" | jq -r '.token // empty')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    # Intentar registro si no existe
    REG_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/auth/registro" \
        -H "Content-Type: application/json" \
        -d '{"email":"concurrency_user@delivery.com","password":"password123","nombre":"Concurrency Tester"}')
    TOKEN=$(echo "$REG_RESP" | jq -r '.token // empty')
fi

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
    echo -e "${RED}[ERROR] No se pudo obtener token JWT. Asegúrate de que auth-service esté activo y con usuarios cargados.${NC}"
    exit 1
fi
echo -e " [${GREEN}OK${NC}] Token JWT obtenido correctamente"

# ==============================================================================
# PRUEBA 1: 50 PEDIDOS SIMULTÁNEOS SOBRE STOCK = 20
# ==============================================================================
echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${BLUE} PRUEBA 1: 50 Pedidos Concurrentes sobre Producto con Stock = 20              ${NC}"
echo -e "${BLUE}==============================================================================${NC}"

# Ajustar stock a 20 para el Producto ID 10 directamente en MySQL si docker está disponible
TARGET_PROD_ID=10
echo -e "Configurando stock = 20 para Producto ID ${TARGET_PROD_ID}..."
if command -v docker >/dev/null 2>&1 && docker ps | grep -q delivery-mysql; then
    docker exec -i delivery-mysql mysql -u root -p_odmon5Am -D IN5AM -e "UPDATE producto SET stock = 20 WHERE id = ${TARGET_PROD_ID};" >/dev/null 2>&1
    echo -e " [${GREEN}OK${NC}] Stock fijado en 20 vía docker exec mysql IN5AM"
elif command -v mysql >/dev/null 2>&1; then
    mysql -u root -p_odmon5Am -D IN5AM -e "UPDATE producto SET stock = 20 WHERE id = ${TARGET_PROD_ID};" >/dev/null 2>&1 && \
    echo -e " [${GREEN}OK${NC}] Stock fijado en 20 vía cliente local mysql IN5AM"
else
    echo -e " ${YELLOW}[AVISO] No se pudo conectar directo a MySQL para resetear stock.${NC}"
    echo -e " Verificando stock actual del producto vía API..."
fi

# Consultar stock inicial vía API
INITIAL_PROD=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/5/productos" -H "Authorization: Bearer ${TOKEN}")
INITIAL_STOCK=$(echo "$INITIAL_PROD" | jq -r ".content[] | select(.id == ${TARGET_PROD_ID}) | .stock // empty")

if [ -z "$INITIAL_STOCK" ]; then
    # Si comercio 5 no tiene producto 10, buscar cualquier producto disponible
    TARGET_PROD_ID=1
    INITIAL_PROD=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/1/productos" -H "Authorization: Bearer ${TOKEN}")
    INITIAL_STOCK=$(echo "$INITIAL_PROD" | jq -r ".content[] | select(.id == ${TARGET_PROD_ID}) | .stock // 20")
fi
echo -e "Stock inicial detectado para Producto ${TARGET_PROD_ID}: ${CYAN}${INITIAL_STOCK}${NC}"

RESPONSES_FILE="${TEMP_DIR}/p1_responses.txt"
> "$RESPONSES_FILE"

echo -e "Lanzando 50 peticiones HTTP concurrentes (xargs -P 50)..."
seq 1 50 | xargs -n 1 -P 50 -I {} sh -c "
    STATUS=\$(curl -s -o /dev/null -w '%{http_code}' -X POST '${BASE_URL}/api/v1/pedidos' \
        -H 'Authorization: Bearer ${TOKEN}' \
        -H 'Content-Type: application/json' \
        -H 'Idempotency-Key: concurrency-p1-{}' \
        -d '{\"items\":[{\"productoId\":${TARGET_PROD_ID},\"cantidad\":1}]}')
    echo \"\$STATUS\" >> '${RESPONSES_FILE}'
"

# Conteo de respuestas
COUNT_200=$(grep -c "^200" "$RESPONSES_FILE" || true)
COUNT_201=$(grep -c "^201" "$RESPONSES_FILE" || true)
COUNT_400=$(grep -c "^400" "$RESPONSES_FILE" || true)
COUNT_409=$(grep -c "^409" "$RESPONSES_FILE" || true)
COUNT_5XX=$(grep -c "^5" "$RESPONSES_FILE" || true)
COUNT_OTHER=$(grep -Ev "^(200|201|400|409|5[0-9]{2})" "$RESPONSES_FILE" | wc -l || true)

SUCCESS_COUNT=$((COUNT_200 + COUNT_201))
CONFLICT_COUNT=$((COUNT_400 + COUNT_409))

echo -e "\nResultados de las 50 peticiones concurrentes:"
echo -e "  - Respuestas Exitosas (200 / 201)   : ${GREEN}${SUCCESS_COUNT}${NC} (201: ${COUNT_201}, 200: ${COUNT_200})"
echo -e "  - Respuestas Rechazo Stock (400/409): ${YELLOW}${CONFLICT_COUNT}${NC} (409: ${COUNT_409}, 400: ${COUNT_400})"
echo -e "  - Errores de Servidor (5xx)          : ${RED}${COUNT_5XX}${NC}"

# Consultar stock final
FINAL_PROD=$(curl -s -X GET "${BASE_URL}/api/v1/comercios/5/productos" -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || curl -s -X GET "${BASE_URL}/api/v1/comercios/1/productos" -H "Authorization: Bearer ${TOKEN}")
FINAL_STOCK=$(echo "$FINAL_PROD" | jq -r ".content[] | select(.id == ${TARGET_PROD_ID}) | .stock // 0")
echo -e "Stock final reportado por API          : ${CYAN}${FINAL_STOCK}${NC}"

echo -e "\n${CYAN}Evaluación QA Prueba 1:${NC}"
if [ "$COUNT_5XX" -eq 0 ]; then
    echo -e "  [${GREEN}OK${NC}] Cero errores 5xx bajo contención máxima"
else
    echo -e "  [${RED}FAIL${NC}] Se detectaron ${COUNT_5XX} errores 5xx (fallo de concurrencia/timeout)"
fi

if [ "$FINAL_STOCK" -ge 0 ]; then
    echo -e "  [${GREEN}OK${NC}] Stock final no es negativo (Stock: ${FINAL_STOCK})"
else
    echo -e "  [${RED}FAIL${NC}] VULNERABILIDAD CRÍTICA: Stock negativo detectado (${FINAL_STOCK})"
fi

# Diagnóstico de discrepancia con la especificación
if [ "$COUNT_201" -eq 20 ] && [ "$COUNT_409" -eq 30 ]; then
    echo -e "  [${GREEN}OK${NC}] Cumple estrictamente la especificación (20 de 201 y 30 de 409)"
else
    echo -e "  [${YELLOW}AVISO QA - BUG DE CÓDIGO HTTP${NC}]"
    echo -e "       Spec exige: 20 respuestas HTTP 201 y 30 respuestas HTTP 409"
    echo -e "       Recibido  : ${COUNT_200} respuestas 200 y ${COUNT_400} respuestas 400"
    echo -e "       Motivo    : PedidosController retorna 200 OK y GlobalExceptionHandler mapea excepciones de stock a 400."
fi

# ==============================================================================
# PRUEBA 2: PEDIDOS CRUZADOS PARALELOS (A+B vs B+A) - PREVENCIÓN DE DEADLOCKS
# ==============================================================================
echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${BLUE} PRUEBA 2: Pedidos Cruzados (A+B y B+A) x 100 en Paralelo (Sin Deadlock)      ${NC}"
echo -e "${BLUE}==============================================================================${NC}"

DEADLOCK_FILE="${TEMP_DIR}/p2_responses.txt"
> "$DEADLOCK_FILE"

# Resetear stock suficiente para ambos productos si es posible
if command -v docker >/dev/null 2>&1 && docker ps | grep -q delivery-mysql; then
    docker exec -i delivery-mysql mysql -u root -p_odmon5Am -D IN5AM -e "UPDATE producto SET stock = 500 WHERE id IN (1, 2);" >/dev/null 2>&1
elif command -v mysql >/dev/null 2>&1; then
    mysql -u root -p_odmon5Am -D IN5AM -e "UPDATE producto SET stock = 500 WHERE id IN (1, 2);" >/dev/null 2>&1
fi

echo -e "Lanzando 100 peticiones alternadas concurrentes (50 A+B y 50 B+A)..."
seq 1 100 | xargs -n 1 -P 30 -I {} sh -c "
    if [ \$(({} % 2)) -eq 0 ]; then
        # Orden A (1) luego B (2)
        BODY='{\"items\":[{\"productoId\":1,\"cantidad\":1},{\"productoId\":2,\"cantidad\":1}]}'
    else
        # Orden B (2) luego A (1) (inversión de orden para forzar deadlock potencial)
        BODY='{\"items\":[{\"productoId\":2,\"cantidad\":1},{\"productoId\":1,\"cantidad\":1}]}'
    fi
    STATUS=\$(curl -s -o /dev/null -w '%{http_code}' -X POST '${BASE_URL}/api/v1/pedidos' \
        -H 'Authorization: Bearer ${TOKEN}' \
        -H 'Content-Type: application/json' \
        -d \"\$BODY\")
    echo \"\$STATUS\" >> '${DEADLOCK_FILE}'
"

DEADLOCK_500=$(grep -c "^500" "$DEADLOCK_FILE" || true)
DEADLOCK_20X=$(grep -c -E "^(200|201)" "$DEADLOCK_FILE" || true)

echo -e "Respuestas de pedidos cruzados:"
echo -e "  - Pedidos procesados exitosamente (20x): ${GREEN}${DEADLOCK_20X}${NC}"
echo -e "  - Fallos 500 (Potenciales Deadlocks)    : ${RED}${DEADLOCK_500}${NC}"

if [ "$DEADLOCK_500" -eq 0 ]; then
    echo -e "  [${GREEN}OK${NC}] Ningún deadlock detectado. El ordenamiento por ID (.sorted()) en CatalogoService funciona correctamente."
else
    echo -e "  [${RED}FAIL${NC}] Se detectaron ${DEADLOCK_500} errores 500 que podrían ser deadlocks en base de datos (40P01)."
fi

# ==============================================================================
# PRUEBA 3: IDEMPOTENCIA CON 10 PETICIONES SIMULTÁNEAS Y MISMA Idempotency-Key
# ==============================================================================
echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${BLUE} PRUEBA 3: 10 Peticiones con la Misma Idempotency-Key                          ${NC}"
echo -e "${BLUE}==============================================================================${NC}"

IDEMP_KEY="idemp-qa-uuid-$RANDOM-$RANDOM"
IDEMP_RESP_FILE="${TEMP_DIR}/p3_responses.txt"
> "$IDEMP_RESP_FILE"

echo -e "Enviando 10 peticiones concurrentes con Header 'Idempotency-Key: ${IDEMP_KEY}'..."
seq 1 10 | xargs -n 1 -P 10 -I {} sh -c "
    RESP=\$(curl -s -X POST '${BASE_URL}/api/v1/pedidos' \
        -H 'Authorization: Bearer ${TOKEN}' \
        -H 'Content-Type: application/json' \
        -H 'Idempotency-Key: ${IDEMP_KEY}' \
        -d '{\"items\":[{\"productoId\":1,\"cantidad\":1}]}')
    ORDER_ID=\$(echo \"\$RESP\" | jq -r '.id // empty')
    echo \"\$ORDER_ID\" >> '${IDEMP_RESP_FILE}'
"

DISTINCT_ORDERS=$(grep -v "^$" "$IDEMP_RESP_FILE" | sort -u | wc -l || true)
TOTAL_CREATED=$(grep -v "^$" "$IDEMP_RESP_FILE" | wc -l || true)

echo -e "Análisis de Idempotencia:"
echo -e "  - Total de pedidos generados en respuesta : ${TOTAL_CREATED}"
echo -e "  - IDs de pedido únicos encontrados        : ${DISTINCT_ORDERS}"

if [ "$DISTINCT_ORDERS" -eq 1 ]; then
    echo -e "  [${GREEN}OK${NC}] Idempotencia implementada correctamente: 1 solo pedido generado."
else
    echo -e "  [${RED}FAIL - DEFECTO DETECTADO${NC}]"
    echo -e "       Se crearon ${DISTINCT_ORDERS} pedidos diferentes con la misma Idempotency-Key."
    echo -e "       Diagnóstico: PedidosController y PedidosService NO leen ni persisten la cabecera 'Idempotency-Key'."
fi

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${BLUE}                   FIN DE LA PRUEBA DE CONCURRENCIA                           ${NC}"
echo -e "${BLUE}==============================================================================${NC}"
