import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
    test(
        'Web Push worker preserves Flutter caching and validates action routes',
        () {
            final worker = File('web/web_push_service_worker.js').readAsStringSync();
            expect(worker, contains("importScripts('/flutter_service_worker.js')"));
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
        'Flutter bootstrap installs the Web Push wrapper before loading the app',
        () {
            final bootstrap = File('web/flutter_bootstrap.js').readAsStringSync();
            expect(
                bootstrap,
                contains("register('/web_push_service_worker.js', { scope: '/' })"),
            );
            expect(bootstrap, contains('_flutter.loader.load'));
            final index = File('web/index.html').readAsStringSync();
            expect(index, contains('push_bridge.js'));
        },
    );
}