import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/bidding/bids_screen.dart';

void main() {
    testWidgets('attendance controls cycle independently from full day', (
        tester,
    ) async {
        final monday = DateTime(2026, 8, 10);
        final tuesday = DateTime(2026, 8, 11);
        final api = _FakeApi(
            _context(
                days: [
                    BidDay(date: monday, weekday: 'MONDAY', tokens: 0),
                    BidDay(date: tuesday, weekday: 'TUESDAY', tokens: 0),
                ],
            ),
        );
        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: BidsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Full day'), findsNWidgets(2));
        await tester.tap(
            find.byKey(Key('attendance-period-${monday.toIso8601String()}')),
        );
        await tester.pump();
        expect(find.text('Morning'), findsOneWidget);
        expect(find.text('Full day'), findsOneWidget);
        await tester.tap(
            find.byKey(Key('attendance-period-${monday.toIso8601String()}')),
        );
        await tester.pump();
        expect(find.text('Afternoon'), findsOneWidget);
    });

    testWidgets('Clear empties the bid fields without saving', (tester) async {
        final monday = DateTime(2026, 8, 10);
        final tuesday = DateTime(2026, 8, 11);
        final api = _FakeApi(
            BiddingContext(
                roundId: 1,
                status: 'OPEN',
                cutoffAt: DateTime.now().toUtc().add(const Duration(days: 1)),
                cutoffTimeZone: 'Europe/Berlin',
                serverTime: DateTime.now().toUtc(),
                seatCapacity: 6,
                startingBalance: 10,
                bidTotal: 7,
                availableToBid: 3,
                days: [
                    BidDay(date: monday, weekday: 'MONDAY', tokens: 5),
                    BidDay(date: tuesday, weekday: 'TUESDAY', tokens: 2),
                ],
            ),
        );

        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: BidsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();

        expect(
            tester.widget<TextField>(find.byType(TextField).first).controller!.text,
            '5',
        );
        expect(
            tester.widget<TextField>(find.byType(TextField).last).controller!.text,
            '2',
        );

        final clearButton = find.byKey(const Key('clear-bids'));
        await tester.scrollUntilVisible(
            clearButton,
            300,
            scrollable: find
                    .descendant(
                        of: find.byType(ListView),
                        matching: find.byType(Scrollable),
                    )
                    .first,
        );
        await tester.tap(clearButton);
        await tester.pump();

        expect(
            tester.widget<TextField>(find.byType(TextField).first).controller!.text,
            isEmpty,
        );
        expect(
            tester.widget<TextField>(find.byType(TextField).last).controller!.text,
            isEmpty,
        );
        expect(
            find.text('Bids cleared locally. Select Save bids to apply this change.'),
            findsOneWidget,
        );
        expect(api.saveCalls, 0);
    });

    testWidgets(
        'reservation context is visible and read-only on the bidding page',
        (tester) async {
            final monday = DateTime(2026, 8, 10);
            final api = _FakeApi(
                _context(
                    days: [
                        BidDay(
                            date: monday,
                            weekday: 'MONDAY',
                            tokens: 0,
                            reservedSeatCount: 2,
                            assignableSeatCapacity: 4,
                            reservationDescription: 'Customer workshop',
                        ),
                    ],
                ),
            );

            await tester.pumpWidget(
                MaterialApp(
                    home: Scaffold(body: BidsScreen(api: api)),
                ),
            );
            await tester.pumpAndSettle();

            expect(find.text('2 reserved'), findsOneWidget);
            expect(
                find.text('4 of 6 seats available for assignment'),
                findsOneWidget,
            );
            expect(find.text('Customer workshop'), findsOneWidget);
            expect(find.byType(TextField), findsOneWidget);
            expect(
                find.text(
                    'Reservations reduce seats available for assignment. They do not change your token balance, bid limits, or bid cost.',
                ),
                findsOneWidget,
            );
        },
    );

    testWidgets('the shared bidding screen shows reservations on Android', (
        tester,
    ) async {
        final api = _FakeApi(
            _context(
                days: [
                    BidDay(
                        date: DateTime(2026, 8, 10),
                        weekday: 'MONDAY',
                        tokens: 0,
                        reservedSeatCount: 1,
                        assignableSeatCapacity: 5,
                    ),
                ],
            ),
        );

        await tester.pumpWidget(
            MaterialApp(
                theme: ThemeData(platform: TargetPlatform.android),
                home: Scaffold(body: BidsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();

        expect(find.text('1 reserved'), findsOneWidget);
        expect(find.text('5 of 6 seats available for assignment'), findsOneWidget);
    });

    testWidgets('successful save displays authoritative reservation metadata', (
        tester,
    ) async {
        final monday = DateTime(2026, 8, 10);
        final initial = _context(
            days: [BidDay(date: monday, weekday: 'MONDAY', tokens: 0)],
        );
        final saved = _context(
            days: [
                BidDay(
                    date: monday,
                    weekday: 'MONDAY',
                    tokens: 3,
                    reservedSeatCount: 2,
                    assignableSeatCapacity: 4,
                    reservationDescription: 'Training day',
                ),
            ],
        );
        final api = _FakeApi(initial)..saveResult = saved;

        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: BidsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();
        await tester.enterText(find.byType(TextField), '3');
        final save = find.text('Save bids');
        await tester.scrollUntilVisible(
            save,
            300,
            scrollable: find
                    .descendant(
                        of: find.byType(ListView),
                        matching: find.byType(Scrollable),
                    )
                    .first,
        );
        await tester.pump();
        await tester.tap(save);
        await tester.pumpAndSettle();

        expect(api.saveCalls, 1);
        expect(find.text('2 reserved'), findsOneWidget);
        expect(find.text('Training day'), findsOneWidget);
        expect(find.text('Bids saved.'), findsOneWidget);
    });

    testWidgets('resume refresh preserves a same-round unsaved bid draft', (
        tester,
    ) async {
        final monday = DateTime(2026, 8, 10);
        final initial = _context(
            days: [BidDay(date: monday, weekday: 'MONDAY', tokens: 0)],
        );
        final refreshed = _context(
            days: [
                BidDay(
                    date: monday,
                    weekday: 'MONDAY',
                    tokens: 0,
                    reservedSeatCount: 2,
                    assignableSeatCapacity: 4,
                ),
            ],
        );
        final api = _FakeApi(initial)..loadResults.add(refreshed);

        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: BidsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();
        await tester.enterText(find.byType(TextField), '3');
        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
        tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
        await tester.pumpAndSettle();

        expect(api.loadCalls, 2);
        expect(
            tester.widget<TextField>(find.byType(TextField)).controller!.text,
            '3',
        );
        expect(find.text('2 reserved'), findsOneWidget);
        expect(
            find.text(
                'Seat availability refreshed; your unsaved bids were preserved.',
            ),
            findsOneWidget,
        );
    });

    testWidgets('refresh discards a draft when the authoritative round changes', (
        tester,
    ) async {
        final monday = DateTime(2026, 8, 10);
        final successorMonday = DateTime(2026, 8, 17);
        final api =
                _FakeApi(
                        _context(
                            days: [BidDay(date: monday, weekday: 'MONDAY', tokens: 0)],
                        ),
                    )
                    ..loadResults.add(
                        _context(
                            roundId: 2,
                            days: [
                                BidDay(date: successorMonday, weekday: 'MONDAY', tokens: 0),
                            ],
                        ),
                    );

        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: BidsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();
        await tester.enterText(find.byType(TextField), '4');
        await tester.tap(find.byKey(const Key('refresh-bidding-context')));
        await tester.pumpAndSettle();

        expect(
            tester.widget<TextField>(find.byType(TextField)).controller!.text,
            '',
        );
        expect(
            find.text(
                'The bidding round changed. Your previous unsaved draft was discarded.',
            ),
            findsOneWidget,
        );
    });

    testWidgets(
        'a same-round save conflict refreshes context and preserves draft',
        (tester) async {
            final monday = DateTime(2026, 8, 10);
            final initial = _context(
                days: [BidDay(date: monday, weekday: 'MONDAY', tokens: 0)],
            );
            final api = _FakeApi(initial)
                ..saveError = const Problem(
                    status: 409,
                    code: 'ROUND_PROCESSING',
                    detail: 'Bidding has closed.',
                )
                ..loadResults.add(
                    _context(
                        days: [
                            BidDay(
                                date: monday,
                                weekday: 'MONDAY',
                                tokens: 0,
                                reservedSeatCount: 1,
                                assignableSeatCapacity: 5,
                            ),
                        ],
                    ),
                );

            await tester.pumpWidget(
                MaterialApp(
                    home: Scaffold(body: BidsScreen(api: api)),
                ),
            );
            await tester.pumpAndSettle();
            await tester.enterText(find.byType(TextField), '3');
            final save = find.text('Save bids');
            await tester.scrollUntilVisible(
                save,
                300,
                scrollable: find
                        .descendant(
                            of: find.byType(ListView),
                            matching: find.byType(Scrollable),
                        )
                        .first,
            );
            await tester.pump();
            final saveButton = tester.widget<FilledButton>(
                find.widgetWithText(FilledButton, 'Save bids'),
            );
            expect(saveButton.onPressed, isNotNull);
            saveButton.onPressed!();
            await tester.pumpAndSettle();

            expect(
                tester.widget<TextField>(find.byType(TextField)).controller!.text,
                '3',
            );
            expect(api.saveCalls, 1);
            expect(api.loadCalls, 2);
            await tester.drag(find.byType(ListView), const Offset(0, 600));
            await tester.pumpAndSettle();
            expect(find.text('1 reserved'), findsOneWidget);
        },
    );
}

BiddingContext _context({required List<BidDay> days, int roundId = 1}) =>
        BiddingContext(
            roundId: roundId,
            status: 'OPEN',
            cutoffAt: DateTime.now().toUtc().add(const Duration(days: 1)),
            cutoffTimeZone: 'Europe/Berlin',
            serverTime: DateTime.now().toUtc(),
            seatCapacity: 6,
            startingBalance: 10,
            bidTotal: days.fold(0, (total, day) => total + day.tokens),
            availableToBid: 10 - days.fold(0, (total, day) => total + day.tokens),
            days: days,
        );

class _FakeApi extends ApiClient {
    _FakeApi(this.biddingContext)
        : super(AuthService.testing(Dio(), _NoopCookieStore()));

    final BiddingContext biddingContext;
    final List<BiddingContext> loadResults = [];
    BiddingContext? saveResult;
    Object? saveError;
    int saveCalls = 0;
    int loadCalls = 0;

    @override
    Future<BiddingContext> currentBidding() async {
        loadCalls++;
        if (loadCalls == 1 || loadResults.isEmpty) return biddingContext;
        return loadResults.removeAt(0);
    }

    @override
    Future<BiddingContext> replaceBids(
        int roundId,
        Map<DateTime, int> values, [
        Map<DateTime, AttendancePeriod>? attendancePeriods,
    ]) async {
        saveCalls++;
        if (saveError != null) throw saveError!;
        return saveResult ?? biddingContext;
    }
}

class _NoopCookieStore implements ManagedCookieStore {
    @override
    Future<void> attach(Dio dio) async {}

    @override
    Future<void> clear() async {}
}