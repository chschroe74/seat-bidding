const SEAT_PUSH_VERSION = 1;
const SEAT_PUSH_TITLE = 'Seat bidding reminder';
const SEAT_PUSH_BODY = 'You have not placed your bids for next week yet.';
const SEAT_PUSH_ACTIONS = new Set(['PLACE_BIDS', 'SKIP_REMINDERS']);
const LEGACY_FLUTTER_CACHE = 'flutter-app-cache';
const STANDALONE_WORKER_MARKER = 'seat-bidding-standalone-worker-v1';

self.addEventListener('install', (event) => {
    event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const firstStandaloneActivation = !(await caches.has(STANDALONE_WORKER_MARKER));
        await caches.delete(LEGACY_FLUTTER_CACHE);
        await caches.open(STANDALONE_WORKER_MARKER);
        await self.clients.claim();
        if (firstStandaloneActivation) {
            const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
            await Promise.all(windows.map((client) => client.navigate(client.url).catch(() => null)));
        }
    })());
});

self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);
    const mutableApplicationShell = url.origin === self.location.origin
        && event.request.method === 'GET'
        && (event.request.mode === 'navigate'
            || event.request.destination === 'script'
            || event.request.destination === 'manifest');
    if (mutableApplicationShell) {
        event.respondWith(fetch(event.request, { cache: 'no-store' }));
    }
});

function seatSafeRoute(value, fallback) {
    try {
        const url = new URL(value || fallback, self.location.origin);
        if (url.origin !== self.location.origin) return fallback;
        if (url.pathname !== '/bids' && url.pathname !== '/settings/reminders/skip') return fallback;
        for (const [key, item] of url.searchParams) {
            if (key !== 'roundId' && key !== 'reminderRoundId' || !/^\d+$/.test(item)) return fallback;
        }
        return `${url.pathname}${url.search}`;
    }
    catch (_) {
        return fallback;
    }
}

function seatPayload(event) {
    try {
        const value = event.data ? event.data.json() : {};
        if (value.version !== SEAT_PUSH_VERSION || value.template !== 'BID_REMINDER_V1') throw new Error('version');
        const actions = Array.isArray(value.actions)
            ? value.actions.filter((action) => SEAT_PUSH_ACTIONS.has(action))
            : [];
        return {
            title: SEAT_PUSH_TITLE,
            body: SEAT_PUSH_BODY,
            route: seatSafeRoute(value.route, '/bids'),
            suppressionRoute: seatSafeRoute(value.suppressionRoute, '/settings/reminders/skip'),
            actions,
        };
    }
    catch (_) {
        return { title: SEAT_PUSH_TITLE, body: SEAT_PUSH_BODY, route: '/bids', suppressionRoute: '/settings/reminders/skip', actions: [] };
    }
}

self.addEventListener('push', (event) => {
    const payload = seatPayload(event);
    const actions = [];
    if (payload.actions.includes('PLACE_BIDS')) actions.push({ action: 'PLACE_BIDS', title: 'Place bids' });
    if (payload.actions.includes('SKIP_REMINDERS')) {
        actions.push({ action: 'SKIP_REMINDERS', title: 'Skip reminders this week' });
    }
    event.waitUntil(self.registration.showNotification(payload.title, {
        body: payload.body,
        icon: '/icons/seat.svg',
        badge: '/icons/seat.svg',
        tag: 'seat-bidding-reminder',
        renotify: false,
        actions,
        data: { route: payload.route, suppressionRoute: payload.suppressionRoute },
    }));
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const target = event.action === 'SKIP_REMINDERS'
        ? seatSafeRoute(event.notification.data?.suppressionRoute, '/settings/reminders/skip')
        : seatSafeRoute(event.notification.data?.route, '/bids');
    event.waitUntil((async () => {
        const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
        const existing = windows.find((client) => new URL(client.url).origin === self.location.origin);
        if (existing) {
            await existing.navigate(target);
            return existing.focus();
        }
        return self.clients.openWindow(target);
    })());
});