import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
    test(
        'Web Push worker owns its lifecycle and validates action routes',
        () {
            final worker = File('web/web_push_service_worker.js').readAsStringSync();
            expect(worker, isNot(contains('importScripts')));
            expect(worker, contains('self.skipWaiting()'));
            expect(worker, contains('self.clients.claim()'));
            expect(worker, contains("'flutter-app-cache'"));
            expect(worker, contains('caches.delete(LEGACY_FLUTTER_CACHE)'));
            expect(worker, contains("'seat-bidding-standalone-worker-v1'"));
            expect(worker, contains('firstStandaloneActivation'));
            expect(worker, contains('client.navigate(client.url)'));
            expect(worker, contains("addEventListener('fetch'"));
            expect(worker, contains("cache: 'no-store'"));
            expect(worker, contains("addEventListener('push'"));
            expect(worker, contains('showNotification'));
            expect(worker, contains("addEventListener('notificationclick'"));
            expect(worker, contains('PLACE_BIDS'));
            expect(worker, contains('SKIP_REMINDERS'));
            expect(worker, contains("'/bids'"));
            expect(worker, contains("'/settings/reminders/skip'"));
            expect(worker, contains('value.version !== SEAT_PUSH_VERSION'));
        },
    );

    test(
        'Flutter bootstrap updates the Web Push worker and reloads replaced clients',
        () {
            final bootstrap = File('web/flutter_bootstrap.js').readAsStringSync();
            expect(
                bootstrap,
                contains("'/web_push_service_worker.js'"),
            );
            expect(bootstrap, contains("updateViaCache: 'none'"));
            expect(bootstrap, contains('registration.update()'));
            expect(bootstrap, contains("addEventListener('controllerchange'"));
            expect(bootstrap, contains('window.location.reload()'));
            expect(bootstrap, contains('_flutter.loader.load'));
            final index = File('web/index.html').readAsStringSync();
            expect(index, contains('push_bridge.js'));
        },
    );
}