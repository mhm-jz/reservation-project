import http from 'k6/http';
import {check, fail, sleep} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

const BASE_URL =
    __ENV.BASE_URL || 'http://127.0.0.1:8080';
const pageType = __ENV.PAGE_TYPE || 'first';

const from = __ENV.FROM || '2026-06-01T00:00:00';
const to = __ENV.TO || '2026-07-01T00:00:00';
const limit = Number(__ENV.LIMIT || 100);
const deepCursor = __ENV.DEEP_CURSOR;

const targetVus = Number(__ENV.TARGET_VUS || 20);
const warmUpVus = Number(__ENV.WARM_UP_VUS || 10);
const warmUpDuration = __ENV.WARM_UP_DURATION || '10s';
const warmUpGap = __ENV.WARM_UP_GAP || '2s';
const testDuration = __ENV.TEST_DURATION || '60s';
const thinkTime = Number(__ENV.THINK_TIME || 0.1);
const requestTimeout = __ENV.REQUEST_TIMEOUT || '5s';

const measurementStart = `${
    parseDurationMs(warmUpDuration, 'WARM_UP_DURATION') +
    parseDurationMs(warmUpGap, 'WARM_UP_GAP')
}ms`;

const pageDuration = new Trend(`${pageType}_page_duration`, true);
const pageErrors = new Rate(`${pageType}_page_errors`);
const pageRequests = new Counter(`${pageType}_page_requests`);

export const options = {
    scenarios: {
        warm_up: {
            executor: 'constant-vus',
            exec: 'warmUp',
            vus: warmUpVus,
            duration: warmUpDuration,
        },
        measurement: {
            executor: 'constant-vus',
            exec: 'measure',
            vus: targetVus,
            duration: testDuration,
            startTime: measurementStart,
        },
    },
    thresholds: {
        [`${pageType}_page_duration`]: [
            'p(95)<150',
            'p(99)<200',
        ],
        [`${pageType}_page_errors`]: [
            'rate<0.01',
        ],
        http_req_failed: [
            'rate<0.01',
        ],
    },
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
    validateConfiguration();
}

export function warmUp() {
    executeRequest(false);
}

export function measure() {
    executeRequest(true);
}

function executeRequest(collectResults) {
    const queryParameters = [
        `from=${encodeURIComponent(from)}`,
        `to=${encodeURIComponent(to)}`,
        `limit=${limit}`,
    ];

    if (pageType === 'deep') {
        queryParameters.push(
            `cursor=${encodeURIComponent(deepCursor)}`
        );
    }

    const metricTags = {
        page_type: pageType,
        target_vus: String(targetVus),
        limit: String(limit),
    };

    const response = http.get(
        `${BASE_URL}/api/slots?${queryParameters.join('&')}`,
        {
            tags: {
                ...metricTags,
                phase: collectResults
                    ? 'measurement'
                    : 'warm_up',
            },
            timeout: requestTimeout,
        }
    );

    const responseBody = parseJson(response);

    const validResponse = check(response, {
        [`${pageType}: HTTP 200`]: (result) =>
            result.status === 200,
        [`${pageType}: items present`]: () =>
            responseBody !== null &&
            Array.isArray(responseBody.items),
        [`${pageType}: maximum ${limit} items returned`]: () =>
            responseBody !== null &&
            Array.isArray(responseBody.items) &&
            responseBody.items.length <= limit,
        [`${pageType}: hasNext present`]: () =>
            responseBody !== null &&
            typeof responseBody.hasNext === 'boolean',
        [`${pageType}: nextCursor present when applicable`]: () =>
            responseBody !== null &&
            (
                responseBody.hasNext !== true ||
                (
                    typeof responseBody.nextCursor === 'string' &&
                    responseBody.nextCursor.length > 0
                )
            ),
    });

    if (collectResults) {
        pageDuration.add(response.timings.duration, metricTags);
        pageErrors.add(!validResponse, metricTags);
        pageRequests.add(1, metricTags);
    }

    sleep(thinkTime);
}

function validateConfiguration() {
    if (pageType !== 'first' && pageType !== 'deep') {
        fail('PAGE_TYPE must be either first or deep');
    }

    validatePositiveInteger(limit, 'LIMIT', 100);
    validatePositiveInteger(targetVus, 'TARGET_VUS');
    validatePositiveInteger(warmUpVus, 'WARM_UP_VUS');

    if (!Number.isFinite(thinkTime) || thinkTime < 0) {
        fail('THINK_TIME must be a non-negative number');
    }

    validateLocalDateTime(from, 'FROM');
    validateLocalDateTime(to, 'TO');

    if (localDateTimeToUtcMs(from) >= localDateTimeToUtcMs(to)) {
        fail('FROM must be before TO');
    }

    if (pageType === 'deep' && !deepCursor) {
        fail('DEEP_CURSOR is required for deep-page tests');
    }
}

function validatePositiveInteger(value, name, maximum) {
    if (!Number.isInteger(value) || value < 1) {
        fail(`${name} must be a positive integer`);
    }

    if (maximum !== undefined && value > maximum) {
        fail(`${name} must not be greater than ${maximum}`);
    }
}

function validateLocalDateTime(value, name) {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value)) {
        fail(`${name} must use yyyy-MM-ddTHH:mm:ss`);
    }

    if (!Number.isFinite(localDateTimeToUtcMs(value))) {
        fail(`${name} is not a valid local date-time`);
    }
}

function localDateTimeToUtcMs(value) {
    return Date.parse(`${value}Z`);
}

function parseJson(response) {
    if (!response.body) {
        return null;
    }

    try {
        return response.json();
    } catch (error) {
        return null;
    }
}

function parseDurationMs(value, name) {
    const match = /^(\d+(?:\.\d+)?)(ms|s|m|h)$/.exec(value);

    if (!match) {
        throw new Error(
            `${name} must use one duration unit, for example ` +
            '500ms, 10s, 2m, or 1h'
        );
    }

    const amount = Number(match[1]);
    const unit = match[2];

    const multipliers = {
        ms: 1,
        s: 1000,
        m: 60_000,
        h: 3_600_000,
    };

    return amount * multipliers[unit];
}
