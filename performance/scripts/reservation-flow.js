import http from 'k6/http';
import {check, fail, sleep} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';

http.setResponseCallback(
    http.expectedStatuses(
        {min: 200, max: 399},
        409
    )
);

const DAY_MS = 24 * 60 * 60 * 1000;
const EXPECTED_CONFLICT_CODES = new Set([
    'SLOT_ALREADY_RESERVED',
    'SLOT_UNAVAILABLE',
]);

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8081';
const from = __ENV.FROM || '2026-08-01T00:00:00';
const to = __ENV.TO || '2026-08-31T00:00:00';
const workloadMode = __ENV.WORKLOAD_MODE || 'hotspot';

const targetVus = Number(__ENV.TARGET_VUS || 20);
const testDuration = __ENV.TEST_DURATION || '60s';
const limit = Number(__ENV.LIMIT || 10);
const thinkTime = Number(__ENV.THINK_TIME || 0.1);
const requestTimeout = __ENV.REQUEST_TIMEOUT || '5s';

const browseRate = Number(__ENV.BROWSE_RATE || 80);
const reserveRate = Number(__ENV.RESERVE_RATE || 15);
const cancelRate = Number(__ENV.CANCEL_RATE || 5);

const userPoolSize = Number(
    __ENV.USER_POOL_SIZE || targetVus
);
const userStartIndex = Number(
    __ENV.USER_START_INDEX || 1
);
const usernamePrefix =
    __ENV.USERNAME_PREFIX || 'perf-user-';
const usernameWidth = Number(
    __ENV.USERNAME_WIDTH || 5
);
const userPassword =
    __ENV.USER_PASSWORD || 'TestPassword123';

const slotBrowseDuration = new Trend(
    'slot_browse_duration',
    true
);
const slotBrowseErrorRate = new Rate(
    'slot_browse_error_rate'
);
const slotBrowseOperations = new Counter(
    'slot_browse_operations'
);

const reservationDuration = new Trend(
    'reservation_duration',
    true
);
const reservationTechnicalErrorRate = new Rate(
    'reservation_technical_error_rate'
);
const reservationOperations = new Counter(
    'reservation_operations'
);
const reservationSuccesses = new Counter(
    'reservation_successes'
);
const reservationExpectedConflicts = new Counter(
    'reservation_expected_conflicts'
);

const cancellationDuration = new Trend(
    'cancellation_duration',
    true
);
const cancellationErrorRate = new Rate(
    'cancellation_error_rate'
);
const cancellationOperations = new Counter(
    'cancellation_operations'
);
const cancellationSuccesses = new Counter(
    'cancellation_successes'
);

const journeyDuration = new Trend(
    'journey_duration',
    true
);
const journeyOperations = new Counter(
    'journey_operations'
);

export const options = {
    scenarios: {
        mixed_workload: {
            executor: 'constant-vus',
            exec: 'mixedJourney',
            vus: targetVus,
            duration: testDuration,
        },
    },
    thresholds: {
        slot_browse_duration: [
            'p(95)<250',
            'p(99)<500',
        ],
        reservation_duration: [
            'p(95)<500',
            'p(99)<750',
        ],
        cancellation_duration: [
            'p(95)<500',
            'p(99)<750',
        ],
        journey_duration: [
            'p(95)<1000',
            'p(99)<1500',
        ],
        slot_browse_error_rate: [
            'rate<0.01',
        ],
        reservation_technical_error_rate: [
            'rate<0.01',
        ],
        cancellation_error_rate: [
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
    const configuration = validateConfiguration();
    const users = [];

    for (let offset = 0; offset < userPoolSize; offset += 1) {
        const userNumber = userStartIndex + offset;
        const username =
            usernamePrefix +
            String(userNumber).padStart(
                usernameWidth,
                '0'
            );

        const loginResponse = http.post(
            `${baseUrl}/api/auth/login`,
            JSON.stringify({
                username,
                password: userPassword,
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                },
                tags: {
                    request_type: 'setup_login',
                },
                timeout: requestTimeout,
            }
        );

        const loginBody = parseJson(loginResponse);
        const accessToken = loginBody?.accessToken;

        const loginSucceeded =
            loginResponse.status === 200 &&
            typeof accessToken === 'string' &&
            accessToken.length > 0;

        if (!loginSucceeded) {
            fail(
                `Authentication failed for ${username}. ` +
                `HTTP ${loginResponse.status}: ` +
                `${loginResponse.body}`
            );
        }

        users.push({
            username,
            authorization: `Bearer ${accessToken}`,
        });
    }

    return {
        users,
        distributed: configuration.distributed,
    };
}

export function mixedJourney(data) {
    const journeyStartedAt = Date.now();
    const user = data.users[__VU - 1];

    if (!user) {
        fail(
            `No authenticated user is available for VU ${__VU}. ` +
            'USER_POOL_SIZE must be at least TARGET_VUS.'
        );
    }

    const window = selectJourneyWindow(
        data.distributed
    );
    const tags = buildTags(window);

    try {
        const slots = browseSlots(
            user.authorization,
            window,
            tags
        );

        if (!slots || slots.length === 0) {
            return;
        }

        const action = selectJourneyAction();

        if (action === 'browse') {
            return;
        }

        const selectedSlot = slots[
            Math.floor(Math.random() * slots.length)
        ];

        const reservationResult = reserveSlot(
            user.authorization,
            selectedSlot.id,
            tags
        );

        if (
            action === 'cancel' &&
            reservationResult.success
        ) {
            cancelReservation(
                user.authorization,
                reservationResult.reservationId,
                tags
            );
        }
    } finally {
        journeyDuration.add(
            Date.now() - journeyStartedAt,
            tags
        );
        journeyOperations.add(1, tags);
        sleep(thinkTime);
    }
}

function browseSlots(authorization, window, tags) {
    const response = http.get(
        `${baseUrl}/api/slots?` +
        [
            `from=${encodeURIComponent(window.from)}`,
            `to=${encodeURIComponent(window.to)}`,
            `limit=${limit}`,
        ].join('&'),
        {
            headers: {
                Authorization: authorization,
            },
            tags: {
                ...tags,
                operation: 'browse',
            },
            timeout: requestTimeout,
        }
    );

    const body = parseJson(response);

    const valid = check(response, {
        'browse: HTTP 200': (result) =>
            result.status === 200,
        'browse: valid JSON object': () =>
            body !== null &&
            typeof body === 'object',
        'browse: items array present': () =>
            body !== null &&
            Array.isArray(body.items),
        'browse: item count does not exceed limit': () =>
            body !== null &&
            Array.isArray(body.items) &&
            body.items.length <= limit,
        'browse: hasNext is boolean': () =>
            body !== null &&
            typeof body.hasNext === 'boolean',
        'browse: nextCursor exists when hasNext': () =>
            body !== null &&
            (
                body.hasNext !== true ||
                (
                    typeof body.nextCursor === 'string' &&
                    body.nextCursor.length > 0
                )
            ),
    });

    slotBrowseDuration.add(
        response.timings.duration,
        tags
    );
    slotBrowseErrorRate.add(!valid, tags);
    slotBrowseOperations.add(1, tags);

    if (!valid) {
        return null;
    }

    return body.items.filter(
        (item) =>
            item !== null &&
            Number.isInteger(item.id) &&
            item.id > 0
    );
}

function reserveSlot(authorization, slotId, tags) {
    const response = http.post(
        `${baseUrl}/api/reservations`,
        JSON.stringify({slotId}),
        {
            headers: {
                Authorization: authorization,
                'Content-Type': 'application/json',
            },
            tags: {
                ...tags,
                operation: 'reserve',
            },
            timeout: requestTimeout,
        }
    );

    const body = parseJson(response);
    const conflictCode = extractErrorCode(body);

    const success =
        response.status === 201 &&
        body !== null &&
        Number.isInteger(body.id) &&
        body.id > 0;

    const expectedConflict =
        response.status === 409 &&
        EXPECTED_CONFLICT_CODES.has(conflictCode);

    const technicalError =
        !success &&
        !expectedConflict;

    check(response, {
        'reserve: success or expected conflict': () =>
            success || expectedConflict,
        'reserve: HTTP 201 contains reservation id': () =>
            response.status !== 201 || success,
        'reserve: HTTP 409 contains supported code': () =>
            response.status !== 409 ||
            expectedConflict,
    });

    reservationDuration.add(
        response.timings.duration,
        tags
    );
    reservationTechnicalErrorRate.add(
        technicalError,
        tags
    );
    reservationOperations.add(1, tags);

    if (success) {
        reservationSuccesses.add(1, tags);
    }

    if (expectedConflict) {
        reservationExpectedConflicts.add(1, tags);
    }

    return {
        success,
        reservationId: success ? body.id : null,
    };
}

function cancelReservation(
    authorization,
    reservationId,
    tags
) {
    const response = http.del(
        `${baseUrl}/api/reservations/${reservationId}`,
        null,
        {
            headers: {
                Authorization: authorization,
            },
            tags: {
                ...tags,
                operation: 'cancel',
            },
            timeout: requestTimeout,
        }
    );

    const success = response.status === 204;

    check(response, {
        'cancel: HTTP 204': () => success,
    });

    cancellationDuration.add(
        response.timings.duration,
        tags
    );
    cancellationErrorRate.add(!success, tags);
    cancellationOperations.add(1, tags);

    if (success) {
        cancellationSuccesses.add(1, tags);
    }
}

function selectJourneyAction() {
    const roll = Math.random() * 100;

    if (roll < browseRate) {
        return 'browse';
    }

    if (roll < browseRate + reserveRate) {
        return 'reserve';
    }

    return 'cancel';
}

function selectJourneyWindow(distributed) {
    if (workloadMode === 'hotspot') {
        return {
            from,
            to,
            dayIndex: null,
        };
    }

    const dayIndex =
        (__VU - 1 + __ITER) %
        distributed.totalDays;

    const journeyFromMs =
        distributed.fromMs +
        dayIndex * DAY_MS;

    return {
        from: formatLocalDateTime(journeyFromMs),
        to: formatLocalDateTime(
            journeyFromMs + DAY_MS
        ),
        dayIndex,
    };
}

function buildTags(window) {
    return {
        workload_mode: workloadMode,
        day_index:
            window.dayIndex === null
                ? 'hotspot'
                : String(window.dayIndex),
        journey_from: window.from,
        target_vus: String(targetVus),
        limit: String(limit),
    };
}

function validateConfiguration() {
    if (
        workloadMode !== 'hotspot' &&
        workloadMode !== 'distributed'
    ) {
        fail(
            'WORKLOAD_MODE must be hotspot or distributed'
        );
    }

    validatePositiveInteger(
        targetVus,
        'TARGET_VUS'
    );
    validatePositiveInteger(
        userPoolSize,
        'USER_POOL_SIZE'
    );
    validatePositiveInteger(
        userStartIndex,
        'USER_START_INDEX'
    );
    validatePositiveInteger(
        usernameWidth,
        'USERNAME_WIDTH'
    );
    validatePositiveInteger(limit, 'LIMIT', 100);

    if (userPoolSize < targetVus) {
        fail(
            'USER_POOL_SIZE must be at least TARGET_VUS'
        );
    }

    if (!Number.isFinite(thinkTime) || thinkTime < 0) {
        fail(
            'THINK_TIME must be a non-negative number'
        );
    }

    if (
        !Number.isFinite(browseRate) ||
        !Number.isFinite(reserveRate) ||
        !Number.isFinite(cancelRate) ||
        browseRate < 0 ||
        reserveRate < 0 ||
        cancelRate < 0 ||
        browseRate + reserveRate + cancelRate !== 100
    ) {
        fail(
            'BROWSE_RATE + RESERVE_RATE + CANCEL_RATE ' +
            'must equal 100'
        );
    }

    validateLocalDateTime(from, 'FROM');
    validateLocalDateTime(to, 'TO');

    const fromMs = localDateTimeToUtcMs(from);
    const toMs = localDateTimeToUtcMs(to);

    if (fromMs >= toMs) {
        fail('FROM must be before TO');
    }

    if (!userPassword) {
        fail('USER_PASSWORD must not be empty');
    }

    if (workloadMode === 'distributed') {
        if (
            !isMidnight(from) ||
            !isMidnight(to)
        ) {
            fail(
                'Distributed FROM and TO must be at 00:00:00'
            );
        }

        const rangeMs = toMs - fromMs;

        if (
            rangeMs < DAY_MS ||
            rangeMs % DAY_MS !== 0
        ) {
            fail(
                'Distributed range must contain complete days'
            );
        }

        return {
            distributed: {
                fromMs,
                toMs,
                totalDays: rangeMs / DAY_MS,
            },
        };
    }

    return {
        distributed: null,
    };
}

function validatePositiveInteger(
    value,
    name,
    maximum
) {
    if (!Number.isInteger(value) || value < 1) {
        fail(`${name} must be a positive integer`);
    }

    if (maximum !== undefined && value > maximum) {
        fail(
            `${name} must not be greater than ${maximum}`
        );
    }
}

function validateLocalDateTime(value, name) {
    if (
        !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/
            .test(value)
    ) {
        fail(
            `${name} must use yyyy-MM-ddTHH:mm:ss`
        );
    }

    if (!Number.isFinite(localDateTimeToUtcMs(value))) {
        fail(`${name} is not a valid local date-time`);
    }
}

function isMidnight(value) {
    return value.endsWith('T00:00:00');
}

function localDateTimeToUtcMs(value) {
    return Date.parse(`${value}Z`);
}

function formatLocalDateTime(timestamp) {
    const date = new Date(timestamp);

    const year = date.getUTCFullYear();
    const month = pad2(date.getUTCMonth() + 1);
    const day = pad2(date.getUTCDate());
    const hour = pad2(date.getUTCHours());
    const minute = pad2(date.getUTCMinutes());
    const second = pad2(date.getUTCSeconds());

    return (
        `${year}-${month}-${day}` +
        `T${hour}:${minute}:${second}`
    );
}

function pad2(value) {
    return String(value).padStart(2, '0');
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

function extractErrorCode(body) {
    if (!body || typeof body !== 'object') {
        return null;
    }

    return (
        body.code ||
        body.errorCode ||
        body.error?.code ||
        null
    );
}
