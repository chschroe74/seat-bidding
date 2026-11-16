const SEAT_PUSH_VERSION = 1;
const SEAT_PUSH_TITLE = 'Seat bidding reminder';
const SEAT_PUSH_BODY = 'You have not placed your bids for next week yet.';
const SEAT_OFFER_TITLE = 'Seat available';
const SEAT_OFFER_BODY = 'A time-sensitive seat opportunity is available. Open the app to view current status.';
const SEAT_PUSH_ACTIONS = new Set(['PLACE_BIDS', 'SKIP_REMINDERS', 'VIEW_OFFER']);
const LEGACY_FLUTTER_CACHE = 'flutter-app-cache';
const STANDALONE_WORKER_MARKER = 'seat-bidding-standalone-worker-v1';

self.addEventListener('install', (event) => {
    event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const firstStandaloneActivation = !(await caches.has(STANDALONE_WORKER_MARKER));
        // Only windows migrating from the legacy Flutter worker need the one-time reload. A first visit already
        // runs the current bootstrap; reloading it mid-load leaves WebKit/Safari with a blank page.
        const migratingFromLegacyWorker = await caches.has(LEGACY_FLUTTER_CACHE);
        await caches.delete(LEGACY_FLUTTER_CACHE);
        await caches.open(STANDALONE_WORKER_MARKER);
        await self.clients.claim();
        if (firstStandaloneActivation && migratingFromLegacyWorker) {
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
        if (!['/bids', '/settings/reminders/skip', '/assignments'].includes(url.pathname)) return fallback;
        for (const [key, item] of url.searchParams) {
            const numeric = (key === 'roundId' || key === 'reminderRoundId') && /^\d+$/.test(item);
            const date = key === 'date' && /^\d{4}-\d{2}-\d{2}$/.test(item);
            if (!numeric && !date) return fallback;
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
        if (value.version !== SEAT_PUSH_VERSION) throw new Error('version');
        const actions = Array.isArray(value.actions)
            ? value.actions.filter((action) => SEAT_PUSH_ACTIONS.has(action))
            : [];
        if (value.template === 'SEAT_OFFER_V1') {
            return {
                title: SEAT_OFFER_TITLE,
                body: typeof value.body === 'string' && value.body.length <= 240
                    ? value.body : SEAT_OFFER_BODY,
                route: seatSafeRoute(value.route, '/assignments'),
                suppressionRoute: '/settings/reminders/skip',
                actions: actions.filter((action) => action === 'VIEW_OFFER'),
                tag: `seat-offer-${Number.isInteger(value.candidateId) ? value.candidateId : 'current'}`,
            };
        }
        if (value.template !== 'BID_REMINDER_V1') throw new Error('template');
        return {
            title: SEAT_PUSH_TITLE,
            body: SEAT_PUSH_BODY,
            route: seatSafeRoute(value.route, '/bids'),
            suppressionRoute: seatSafeRoute(value.suppressionRoute, '/settings/reminders/skip'),
            actions,
            tag: 'seat-bidding-reminder',
        };
    }
    catch (_) {
        return { title: SEAT_PUSH_TITLE, body: SEAT_PUSH_BODY, route: '/bids', suppressionRoute: '/settings/reminders/skip', actions: [], tag: 'seat-bidding-reminder' };
    }
}

self.addEventListener('push', (event) => {
    const payload = seatPayload(event);
    const actions = [];
    if (payload.actions.includes('PLACE_BIDS')) actions.push({ action: 'PLACE_BIDS', title: 'Place bids' });
    if (payload.actions.includes('SKIP_REMINDERS')) {
        actions.push({ action: 'SKIP_REMINDERS', title: 'Skip reminders this week' });
    }
    if (payload.actions.includes('VIEW_OFFER')) actions.push({ action: 'VIEW_OFFER', title: 'View offer' });
    event.waitUntil(self.registration.showNotification(payload.title, {
        body: payload.body,
        icon: '/icons/seat.svg',
        badge: '/icons/seat.svg',
        tag: payload.tag,
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