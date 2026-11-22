// k6 load test for the Trade Store NFRs (throughput, p99 latency, error rate).
// Usage: see local-run.md, section "Demonstrate the NFRs".
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ASYNC = __ENV.MODE === 'async';
const RUN = Date.now().toString(36).toUpperCase();

export const options = {
  scenarios: {
    trades: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 200),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 50,
      maxVUs: 400,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(99)<200'],
    checks: ['rate>0.99'],
    dropped_iterations: ['count<1'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const trade = {
    tradeId: `NFR-${RUN}-${__VU}-${__ITER}`,
    version: 1,
    counterPartyId: 'CP-1',
    bookId: 'B1',
    maturityDate: '31/12/2099',
  };
  const res = http.post(`${BASE_URL}/api/v1/trades${ASYNC ? '/async' : ''}`, JSON.stringify(trade), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { [`status is ${ASYNC ? 202 : 201}`]: (r) => r.status === (ASYNC ? 202 : 201) });
}
