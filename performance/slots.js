import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import {
    Counter,
    Rate,
    Trend,
} from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8081';
const pageType = __ENV.PAGE_TYPE || 'first';
const from = '2026-06-01T00:00:00';
const to = '2026-07-01T00:00:00';
const limit = 100;
const deepCursor = __ENV.DEEP_CURSOR;

const pageDuration = new Trend(
    `${pageType}_page_duration`,
    true
);
const pageErrors = new Rate(`${pageType}_page_errors`);
const pageRequests = new Counter(`${pageType}_page_requests`);

const requestThresholds = {
    [`${pageType}_page_duration`]: [
        'p(95)<150',
        'p(99)<200',
    ],
    [`${pageType}_page_errors`]: ['rate<0.01'],
};

export const options = {
    scenarios: {
        warm_up: {
            executor: 'constant-vus',
            exec: 'warmUp',
            vus: 10,
            duration: '10s',
        },
        vus_20: {
            executor: 'constant-vus',
            exec: 'measure',
            vus: 20,
            duration: '20s',
            startTime: '12s',
        },
        vus_50: {
            executor: 'constant-vus',
            exec: 'measure',
            vus: 50,
            duration: '20s',
            startTime: '34s',
        },
        vus_100: {
            executor: 'constant-vus',
            exec: 'measure',
            vus: 100,
            duration: '20s',
            startTime: '56s',
        },
        vus_200: {
            executor: 'constant-vus',
            exec: 'measure',
            vus: 200,
            duration: '20s',
            startTime: '78s',
        },
    },
    thresholds: requestThresholds,
    summaryTrendStats: [
        'avg',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],
};

export function setup() {
    if (pageType !== 'first' && pageType !== 'deep') {
        fail('PAGE_TYPE must be either first or deep');
    }

    if (pageType === 'deep' && !deepCursor) {
        fail('DEEP_CURSOR is required for the deep-page test');
    }

    const loginResponse = http.post(
        `${baseUrl}/api/auth/login`,
        JSON.stringify({
            username: __ENV.AUTH_USERNAME,
            password: __ENV.AUTH_PASSWORD,
        }),
        {
            headers: {'Content-Type': 'application/json'},
            tags: {request_type: 'setup_login'},
        }
    );

    const loginSucceeded = check(loginResponse, {
        'login returned HTTP 200': (response) =>
            response.status === 200,
        'login returned an access token': (response) =>
            Boolean(response.json('accessToken')),
    });

    if (!loginSucceeded) {
        fail(
            `Authentication failed: HTTP ${loginResponse.status} ` +
            loginResponse.body
        );
    }

    return {
        authorization:
            `Bearer ${loginResponse.json('accessToken')}`,
    };
}

export function warmUp(data) {
    executeRequest(data, false);
}

export function measure(data) {
    executeRequest(data, true);
}

function executeRequest(data, collectResults) {
    const query = [
        `from=${encodeURIComponent(from)}`,
        `to=${encodeURIComponent(to)}`,
        `limit=${limit}`,
    ];

    if (pageType === 'deep') {
        query.push(`cursor=${encodeURIComponent(deepCursor)}`);
    }

    const response = http.get(
        `${baseUrl}/api/slots?${query.join('&')}`,
        {
            headers: {Authorization: data.authorization},
            tags: {
                page_type: pageType,
                phase: collectResults ? 'measurement' : 'warm_up',
            },
        }
    );

    const validResponse = check(response, {
        [`${pageType}: HTTP 200`]: (result) =>
            result.status === 200,
        [`${pageType}: items present`]: (result) =>
            Array.isArray(result.json('items')),
        [`${pageType}: hasNext present`]: (result) =>
            typeof result.json('hasNext') === 'boolean',
        [`${pageType}: nextCursor present when applicable`]: (result) =>
            result.json('hasNext') !== true ||
            typeof result.json('nextCursor') === 'string',
    });

    if (collectResults) {
        pageDuration.add(response.timings.duration);
        pageErrors.add(!validResponse);
        pageRequests.add(1);
    }

    sleep(0.1);
}
