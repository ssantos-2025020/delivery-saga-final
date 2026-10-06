import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Counter } from 'k6/metrics';

// Métricas personalizadas
const serverErrors5xx = new Rate('server_errors_5xx');
const successfulOrders = new Counter('orders_success_count');
const rejectedOrdersStock = new Counter('orders_rejected_stock_count');

// Variables de configuración
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        // Escenario de carga realista y escalonada
        mixed_workload: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 15 }, // Warm-up
                { duration: '40s', target: 35 }, // Carga media
                { duration: '30s', target: 50 }, // Pico de estrés concurrente
                { duration: '15s', target: 0 },  // Enfriamiento
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        // Umbrales exigidos por la especificación:
        'http_req_duration': ['p(95)<500'], // p95 debe ser menor a 500 ms
        'server_errors_5xx': ['rate==0'],   // 0% de errores 5xx bajo estrés
    },
};

// Setup: se ejecuta una sola vez para preparar credenciales de prueba
export function setup() {
    const email = `k6_qa_${Date.now()}@delivery.com`;
    const password = 'password123';

    // 1. Registro del usuario de prueba
    const registerRes = http.post(
        `${BASE_URL}/api/v1/auth/registro`,
        JSON.stringify({
            email: email,
            password: password,
            nombre: 'K6 QA Stress User',
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    let token = '';
    if (registerRes.status === 200 || registerRes.status === 201) {
        token = registerRes.json('token');
    } else {
        // Fallback a login con credenciales semilla si ya existía
        const loginRes = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email: 'cliente@delivery.com', password: 'password123' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        token = loginRes.json('token');
    }

    if (!token) {
        throw new Error('Fallo crítico en setup: No se pudo obtener token JWT');
    }

    // Obtener un ID de comercio y producto existente
    const authHeaders = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };

    let comercioId = 1;
    let productoId = 1;

    const comerciosRes = http.get(`${BASE_URL}/api/v1/comercios?page=0&size=10`, { headers: authHeaders });
    if (comerciosRes.status === 200 && comerciosRes.json('content') && comerciosRes.json('content').length > 0) {
        comercioId = comerciosRes.json('content')[0].id;
        const productosRes = http.get(
            `${BASE_URL}/api/v1/comercios/${comercioId}/productos?page=0&size=5`,
            { headers: authHeaders }
        );
        if (productosRes.status === 200 && productosRes.json('content') && productosRes.json('content').length > 0) {
            productoId = productosRes.json('content')[0].id;
        }
    }

    return { token, email, password, comercioId, productoId };
}

export default function (data) {
    const authHeaders = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.token}`,
    };

    // ESCENARIO 1: Login de Usuario
    group('01_Auth_Login', function () {
        const loginRes = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email: data.email, password: data.password }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        const is5xx = loginRes.status >= 500;
        serverErrors5xx.add(is5xx);

        check(loginRes, {
            'login status 200': (r) => r.status === 200,
            'login returns token': (r) => r.json('token') !== undefined && r.json('token') !== '',
        });
    });

    sleep(0.5);

    // ESCENARIO 2: Listado de Comercios Paginado
    group('02_Catalogo_Comercios_Paginado', function () {
        const page = Math.floor(Math.random() * 2);
        const comerciosRes = http.get(
            `${BASE_URL}/api/v1/comercios?page=${page}&size=5`,
            { headers: authHeaders }
        );

        const is5xx = comerciosRes.status >= 500;
        serverErrors5xx.add(is5xx);

        check(comerciosRes, {
            'comercios status 200': (r) => r.status === 200,
            'comercios has content': (r) => Array.isArray(r.json('content')),
        });
    });

    sleep(0.5);

    // ESCENARIO 3: Creación Concurrente de Pedidos
    group('03_Pedidos_Creacion_Concurrente', function () {
        const idempotencyKey = `k6-order-${__VU}-${__ITER}-${Date.now()}`;
        const orderPayload = JSON.stringify({
            items: [
                {
                    productoId: data.productoId,
                    cantidad: 1,
                },
            ],
        });

        const orderHeaders = {
            ...authHeaders,
            'Idempotency-Key': idempotencyKey,
        };

        const orderRes = http.post(
            `${BASE_URL}/api/v1/pedidos`,
            orderPayload,
            { headers: orderHeaders }
        );

        const is5xx = orderRes.status >= 500;
        serverErrors5xx.add(is5xx);

        // Registro de métricas de negocio
        if (orderRes.status === 200 || orderRes.status === 201) {
            successfulOrders.add(1);
        } else if (orderRes.status === 400 || orderRes.status === 409) {
            rejectedOrdersStock.add(1);
        }

        check(orderRes, {
            'pedido status valido (exito 200/201 o agotado 400/409)': (r) =>
                r.status === 200 || r.status === 201 || r.status === 400 || r.status === 409,
            'pedido no retorno 5xx': (r) => r.status < 500,
        });
    });

    sleep(1);
}

export function teardown(data) {
    // Reporte final en consola de K6
    console.log('>>> Prueba de carga finalizada exitosamente.');
}
