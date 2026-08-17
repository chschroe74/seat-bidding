import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/app_version.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/core/web_push_client.dart';
import 'package:seat_bidding/features/settings/settings_screen.dart';
import 'package:seat_bidding/features/settings/skip_reminders_screen.dart';

void main() {
    test('display version matches the semantic pubspec version', () {
        final pubspec = File('pubspec.yaml').readAsStringSync();
        final declaredVersion = RegExp(
            r'^version:\s*([^+\s]+)',
            multiLine: true,
        ).firstMatch(pubspec)?.group(1);

        expect(applicationVersion, declaredVersion);
    });

    testWidgets('disabled reminders hide schedule and device controls', (
        tester,
    ) async {
        final api = _FakeApi(_settings(enabled: false));
        final push = _FakeWebPushClient();
        await tester.pumpWidget(
            MaterialApp(
                home: SettingsScreen(api: api, webPush: push),
            ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Bid reminders enabled'), findsOneWidget);
        expect(find.text('Start reminders on'), findsNothing);
        expect(find.text('Enable notifications on this device'), findsNothing);

        await tester.tap(find.byType(Switch));
        await tester.pumpAndSettle();
        expect(push.enrollCalls, 0);
        expect(api.settings.bidRemindersEnabled, isTrue);
        expect(find.text('Start reminders on'), findsOneWidget);
        expect(find.text('Reminder time: 10:00 Europe/Berlin'), findsOneWidget);
    });

    testWidgets('shows the version without the platform build number', (
        tester,
    ) async {
        final api = _FakeApi(_settings(enabled: false));
        await tester.pumpWidget(
            MaterialApp(
                home: SettingsScreen(api: api, webPush: _FakeWebPushClient()),
            ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Version 1.3.1'), findsOneWidget);
        expect(find.textContaining('+2'), findsNothing);
    });

    testWidgets('enrollment requires its own gesture and registers the browser', (
        tester,
    ) async {
        final api = _FakeApi(_settings(enabled: true));
        final push = _FakeWebPushClient();
        await tester.pumpWidget(
            MaterialApp(
                home: SettingsScreen(api: api, webPush: push),
            ),
        );
        await tester.pumpAndSettle();

        final enable = find.text('Enable notifications on this device');
        await tester.scrollUntilVisible(enable, 200);
        expect(push.enrollCalls, 0);
        await tester.tap(enable);
        await tester.pumpAndSettle();

        expect(push.enrollCalls, 1);
        expect(api.registerCalls, greaterThanOrEqualTo(1));
        expect(
            find.text('Notifications are enabled on this device'),
            findsOneWidget,
        );
        expect(find.text('Test browser'), findsOneWidget);
    });

    testWidgets(
        'iOS browser context shows Home Screen guidance without permission prompt',
        (tester) async {
            final api = _FakeApi(_settings(enabled: true));
            final push = _FakeWebPushClient(ios: true, standalone: false);
            await tester.pumpWidget(
                MaterialApp(
                    home: SettingsScreen(api: api, webPush: push),
                ),
            );
            await tester.pumpAndSettle();

            await tester.scrollUntilVisible(
                find.textContaining('add this site to the Home Screen'),
                200,
            );
            expect(
                find.textContaining('add this site to the Home Screen'),
                findsOneWidget,
            );
            expect(find.text('Enable notifications on this device'), findsNothing);
            expect(push.enrollCalls, 0);
        },
    );

    testWidgets('round suppression requires confirmation and offers no undo', (
        tester,
    ) async {
        final api = _FakeApi(_settings(enabled: true));
        await tester.pumpWidget(
            MaterialApp(home: SkipRemindersScreen(api: api, requestedRoundId: 1)),
        );
        await tester.pumpAndSettle();

        expect(find.textContaining('choice cannot be undone'), findsOneWidget);
        expect(
            find.textContaining('resume automatically for the next round'),
            findsOneWidget,
        );
        expect(find.textContaining('Undo'), findsNothing);
        await tester.tap(find.text('Confirm skip'));
        await tester.pumpAndSettle();
        expect(api.suppressCalls, 1);
        expect(
            find.text('Reminders are already skipped for the current round.'),
            findsOneWidget,
        );
    });
}

NotificationSettings _settings({
    required bool enabled,
    List<RegisteredPushDevice> devices = const [],
}) => NotificationSettings(
    bidRemindersEnabled: enabled,
    bidReminderStartWeekday: ReminderWeekday.monday,
    schedule: const NotificationSchedule(
        systemEnabled: true,
        localTime: '10:00',
        timeZone: 'Europe/Berlin',
        weekdays: ReminderWeekday.values,
    ),
    webPushApplicationServerKey: 'public-key',
    currentRound: ReminderRound(
        roundId: 1,
        cutoffAt: DateTime.utc(2026, 8, 14, 20),
        suppressed: false,
        suppressionAvailable: true,
    ),
    devices: devices,
);

class _FakeApi extends ApiClient {
    _FakeApi(this.settings)
        : super(AuthService.testing(Dio(), _NoopCookieStore()));

    NotificationSettings settings;
    int registerCalls = 0;
    int suppressCalls = 0;

    @override
    Future<NotificationSettings> notificationSettings() async => settings;

    @override
    Future<NotificationSettings> updateNotificationSettings(
        bool enabled,
        ReminderWeekday weekday,
    ) async {
        settings = NotificationSettings(
            bidRemindersEnabled: enabled,
            bidReminderStartWeekday: weekday,
            schedule: settings.schedule,
            webPushApplicationServerKey: settings.webPushApplicationServerKey,
            currentRound: settings.currentRound,
            devices: settings.devices,
        );
        return settings;
    }

    @override
    Future<RegisteredPushDevice> registerPushDevice(
        LocalPushSubscription subscription,
        String label,
    ) async {
        registerCalls++;
        final device = RegisteredPushDevice(
            id: 7,
            label: label,
            registeredAt: DateTime.utc(2026, 8, 15, 8),
            lastSeenAt: DateTime.utc(2026, 8, 15, 8),
        );
        settings = NotificationSettings(
            bidRemindersEnabled: settings.bidRemindersEnabled,
            bidReminderStartWeekday: settings.bidReminderStartWeekday,
            schedule: settings.schedule,
            webPushApplicationServerKey: settings.webPushApplicationServerKey,
            currentRound: settings.currentRound,
            devices: [device],
        );
        return device;
    }

    @override
    Future<void> suppressBidReminders(int roundId) async {
        suppressCalls++;
        settings = NotificationSettings(
            bidRemindersEnabled: settings.bidRemindersEnabled,
            bidReminderStartWeekday: settings.bidReminderStartWeekday,
            schedule: settings.schedule,
            webPushApplicationServerKey: settings.webPushApplicationServerKey,
            currentRound: ReminderRound(
                roundId: roundId,
                cutoffAt: settings.currentRound!.cutoffAt,
                suppressed: true,
                suppressionAvailable: false,
            ),
            devices: settings.devices,
        );
    }
}

class _FakeWebPushClient implements WebPushClient {
    _FakeWebPushClient({this.ios = false, this.standalone = true});

    final bool ios;
    final bool standalone;
    int enrollCalls = 0;
    LocalPushSubscription? subscription;

    @override
    Future<WebPushCapability> capability() async => WebPushCapability(
        supported: true,
        canEnroll: !ios || standalone,
        permission: WebPushPermission.prompt,
        ios: ios,
        standalone: standalone,
        deviceLabel: 'Test browser',
    );

    @override
    Future<LocalPushSubscription?> currentSubscription() async => subscription;

    @override
    Future<WebPushEnrollment> enroll(String applicationServerKey) async {
        enrollCalls++;
        subscription = const LocalPushSubscription(
            endpoint: 'https://push.example.test/device',
            p256dh: 'p256dh',
            auth: 'auth',
        );
        return WebPushEnrollment(
            permission: WebPushPermission.granted,
            subscription: subscription,
        );
    }

    @override
    Future<void> unsubscribe() async => subscription = null;
}

class _NoopCookieStore implements ManagedCookieStore {
    @override
    Future<void> attach(Dio dio) async {}

    @override
    Future<void> clear() async {}
}