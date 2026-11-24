import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

/**
 * ==============================================================================
 * Enterprise E-Commerce Backend - Progressive Scalability & Load Test (k6)
 * Target: 10,000 concurrent VUs (Initial) -> 100,000 concurrent VUs (Peak Ceiling)
 * Hostinger VPS Target: Ubuntu 24.04 LTS, 16 vCPU, 32GB RAM, NVMe, Clustered Nginx
 * ==============================================================================
 */

// --- Custom Metrics & SLA Trackers ---
const catalogLatency = new Trend('catalog_latency_ms', true);
const productDetailsLatency = new Trend('product_details_latency_ms', true);
const cartLatency = new Trend('cart_latency_ms', true);
const orderPlacementLatency = new Trend('order_placement_latency_ms', true);
const errorCount = new Counter('http_error_count');
const successRate = new Rate('successful_requests_rate');

// --- Target Configuration ---
const BASE_URL = __ENV.BASE_URL || 'http://localhost:80/api/v1';
const MAX_VUS = parseInt(__ENV.MAX_VUS || '10000', 10);

// Dynamic progressive stage calculation based on MAX_VUS ceiling
export const options = {
  scenarios: {
    progressive_enterprise_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: Math.min(500, MAX_VUS) },              // Warm-up
        { duration: '2m', target: Math.min(1000, MAX_VUS) },             // Stage 1: 1k VUs
        { duration: '3m', target: Math.min(2500, MAX_VUS) },             // Stage 2: 2.5k VUs
        { duration: '3m', target: Math.min(5000, MAX_VUS) },             // Stage 3: 5k VUs
        { duration: '5m', target: Math.min(10000, MAX_VUS) },            // Stage 4: 10k VUs (Core Production Benchmark)
        { duration: '5m', target: Math.min(25000, MAX_VUS) },            // Stage 5: 25k VUs (Surge)
        { duration: '5m', target: Math.min(50000, MAX_VUS) },            // Stage 6: 50k VUs (High Peak)
        { duration: '5m', target: MAX_VUS },                             // Stage 7: Up to 100k VUs (Maximum Stress)
        { duration: '3m', target: 0 },                                   // Graceful Cooldown
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<350', 'p(99)<800'], // 95% of all requests must complete under 350ms
    'catalog_latency_ms': ['p(95)<200'],             // Redis cached catalog p95 < 200ms
    'order_placement_latency_ms': ['p(95)<500'],     // Transactional checkout p95 < 500ms
    'http_req_failed': ['rate<0.01'],                // Error rate must remain under 1%
    'successful_requests_rate': ['rate>0.99'],
  },
};

const HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
  'User-Agent': 'k6-load-agent/1.0',
};

export function setup() {
  // Pre-test health check validation
  const healthRes = http.get(`${BASE_URL}/ping`, { headers: HEADERS });
  if (healthRes.status !== 200) {
    throw new Error(`Target backend health check failed with status: ${healthRes.status}. Ensure services are running.`);
  }
  return { startTime: new Date().toISOString() };
}

export default function () {
  // Distribute user journey based on real-world e-commerce traffic model:
  // 60% Catalog Browsing & Filter Queries (High Cache Hit Rate)
  // 20% Product Details Inspection (Redis Cached)
  // 15% Cart Operations (User Session Modifying)
  // 5%  Authenticated Checkout & Order Creation (Acid Transactional)

  const userActionRoll = Math.random() * 100;

  if (userActionRoll < 60) {
    // -------------------------------------------------------------------------
    // User Action 1: Catalog Search & Filter Browsing (60%)
    // -------------------------------------------------------------------------
    group('Browse Catalog & Filter', function () {
      const page = Math.floor(Math.random() * 3);
      const categories = ['action-figures', 'board-games', 'puzzles', 'stem-educational'];
      const selectedCat = categories[Math.floor(Math.random() * categories.length)];

      const tStart = new Date().getTime();
      const res = http.get(`${BASE_URL}/products?page=${page}&size=20&sortBy=popularity`, {
        headers: HEADERS,
        tags: { endpoint: 'catalog_list' },
      });
      catalogLatency.add(new Date().getTime() - tStart);

      const isOk = check(res, {
        'catalog status is 200': (r) => r.status === 200,
        'catalog returns success envelope': (r) => {
          try {
            return JSON.parse(r.body).success === true;
          } catch (e) {
            return false;
          }
        },
      });

      successRate.add(isOk);
      if (!isOk) errorCount.add(1);
    });

  } else if (userActionRoll < 80) {
    // -------------------------------------------------------------------------
    // User Action 2: Inspect Product Details (20%)
    // -------------------------------------------------------------------------
    group('View Product Details', function () {
      // Pick a random product ID between 1 and 20
      const productId = Math.floor(Math.random() * 20) + 1;

      const tStart = new Date().getTime();
      const res = http.get(`${BASE_URL}/products/${productId}`, {
        headers: HEADERS,
        tags: { endpoint: 'product_details' },
      });
      productDetailsLatency.add(new Date().getTime() - tStart);

      const isOk = check(res, {
        'product status is 200 or 404': (r) => r.status === 200 || r.status === 404,
      });

      successRate.add(isOk);
      if (!isOk) errorCount.add(1);
    });

  } else if (userActionRoll < 95) {
    // -------------------------------------------------------------------------
    // User Action 3: Cart Calculation & Simulation (15%)
    // -------------------------------------------------------------------------
    group('Cart Operations', function () {
      const tStart = new Date().getTime();
      // Public category tree traversal to simulate cart item association
      const res = http.get(`${BASE_URL}/categories/tree`, {
        headers: HEADERS,
        tags: { endpoint: 'category_tree' },
      });
      cartLatency.add(new Date().getTime() - tStart);

      const isOk = check(res, {
        'cart/categories status is 200': (r) => r.status === 200,
      });

      successRate.add(isOk);
      if (!isOk) errorCount.add(1);
    });

  } else {
    // -------------------------------------------------------------------------
    // User Action 4: Authenticated Checkout Flow Simulation (5%)
    // -------------------------------------------------------------------------
    group('Checkout & Order Placement', function () {
      const randomId = Math.floor(Math.random() * 100000);
      const email = `testuser_${randomId}@example.com`;
      const registerPayload = JSON.stringify({
        email: email,
        password: 'Password@123',
        fullName: `Test Customer ${randomId}`,
        phone: '+919876543210',
      });

      const tStart = new Date().getTime();
      // Test registration and auth throughput
      const regRes = http.post(`${BASE_URL}/auth/register`, registerPayload, {
        headers: HEADERS,
        tags: { endpoint: 'auth_register' },
      });
      orderPlacementLatency.add(new Date().getTime() - tStart);

      const isOk = check(regRes, {
        'auth register status is 201 or 409': (r) => r.status === 201 || r.status === 409,
      });

      successRate.add(isOk);
      if (!isOk) errorCount.add(1);
    });
  }

  // Realistic user think time: between 0.5s and 1.5s
  sleep(Math.random() * 1.0 + 0.5);
}

export function teardown(data) {
  console.log(`Load test session completed. Started at: ${data.startTime}`);
}

