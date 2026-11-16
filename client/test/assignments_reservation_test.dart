import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/assignments/assignments_screen.dart';

void main() {
    testWidgets('paired half-day employees are grouped as one occupied seat', (
        tester,
    ) async {
        final participants = [
            const AssignmentParticipant(
                allocationUnitId: 901,
                unitType: AllocationUnitType.halfDayPair,
                unitRank: 1,
                unitScoreTokens: 30,
                employeeId: 1,
                firstName: 'Ada',
                lastName: 'Morning',
                tokens: 20,
                attendancePeriod: AttendancePeriod.morningOnly,
                assigned: true,
                displayRank: 1,
                isCurrentUser: true,
                seatNumber: 7,
            ),
            const AssignmentParticipant(
                allocationUnitId: 901,
                unitType: AllocationUnitType.halfDayPair,
                unitRank: 1,
                unitScoreTokens: 30,
                employeeId: 2,
                firstName: 'Bob',
                lastName: 'Afternoon',
                tokens: 10,
                attendancePeriod: AttendancePeriod.afternoonOnly,
                assigned: true,
                displayRank: 2,
                isCurrentUser: false,
                seatNumber: 7,
            ),
        ];
        final api = _FakeApi(
            Assignments(
                roundId: 1,
                publishedAt: DateTime.utc(2026, 8, 14),
                seatCapacity: 1,
                days: [
                    AssignmentDay(
                        date: DateTime(2026, 8, 17),
                        weekday: 'MONDAY',
                        myStatus: MyStatus.assigned,
                        occupiedSeatCount: 1,
                        assignedEmployeeCount: 2,
                        assignableSeatCapacity: 1,
                        morningOccupiedCount: 1,
                        afternoonOccupiedCount: 1,
                        activeEmployeeCount: 2,
                        participants: participants,
                        currentOccupants: const [
                            CurrentOccupant(
                                occupancyId: 1001,
                                employeeId: 1,
                                firstName: 'Ada',
                                lastName: 'Morning',
                                attendancePeriod: AttendancePeriod.morningOnly,
                                sourceType: 'ORIGINAL_ASSIGNMENT',
                                isCurrentUser: true,
                                seatNumber: 7,
                            ),
                            CurrentOccupant(
                                occupancyId: 1002,
                                employeeId: 2,
                                firstName: 'Bob',
                                lastName: 'Afternoon',
                                attendancePeriod: AttendancePeriod.afternoonOnly,
                                sourceType: 'ORIGINAL_ASSIGNMENT',
                                isCurrentUser: false,
                                seatNumber: 7,
                            ),
                        ],
                    ),
                ],
            ),
        );
        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: AssignmentsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byType(ExpansionTile));
        await tester.pumpAndSettle();

        expect(find.text('Shared physical seat'), findsOneWidget);
        expect(find.text('Morning'), findsOneWidget);
        expect(find.text('Afternoon'), findsOneWidget);
        expect(
            find.text('Current attendance: 1 morning · 1 afternoon · 2 employees'),
            findsOneWidget,
        );
        expect(find.textContaining('employees assigned'), findsNothing);
        expect(find.textContaining('Seat 7'), findsNWidgets(4));
    });

    testWidgets(
        'assignment card shows reserved and assignable capacity plus public description',
        (tester) async {
            final day = AssignmentDay(
                date: DateTime(2026, 8, 17),
                weekday: 'MONDAY',
                myStatus: MyStatus.assigned,
                occupiedSeatCount: 3,
                assignedEmployeeCount: 3,
                reservedSeatCount: 1,
                assignableSeatCapacity: 3,
                reservationDescription: 'Customer workshop',
                participants: const [],
            );
            final api = _FakeApi(
                Assignments(
                    roundId: 1,
                    publishedAt: DateTime.utc(2026, 8, 14),
                    seatCapacity: 4,
                    days: [day],
                ),
            );
            await tester.pumpWidget(
                MaterialApp(
                    home: Scaffold(body: AssignmentsScreen(api: api)),
                ),
            );
            await tester.pumpAndSettle();

            expect(find.text('1 reserved'), findsOneWidget);
            await tester.tap(find.byType(ExpansionTile));
            await tester.pumpAndSettle();
            expect(find.text('1 of 4 physical seats reserved'), findsOneWidget);
            expect(find.text('3 of 3 assignable seats occupied'), findsOneWidget);
            expect(find.text('Customer workshop'), findsOneWidget);
        },
    );

    testWidgets('assignment card hides open booking when employee has a seat', (
        tester,
    ) async {
        final api = _FakeApi(
            Assignments(
                roundId: 1,
                publishedAt: DateTime.utc(2026, 8, 14),
                seatCapacity: 4,
                days: [
                    AssignmentDay(
                        date: DateTime(2026, 8, 17),
                        weekday: 'MONDAY',
                        myStatus: MyStatus.assigned,
                        occupiedSeatCount: 1,
                        assignedEmployeeCount: 1,
                        assignableSeatCapacity: 4,
                        participants: const [],
                        openBookingOptions: const [
                            OpenBookingOption(
                                attendancePeriod: AttendancePeriod.fullDay,
                                available: false,
                                requiredTokens: 1,
                                bookingAllowed: false,
                                seatSelectionRequired: false,
                                availableSeatCount: 0,
                                disabledReason: 'ALREADY_OCCUPIES_DATE',
                            ),
                        ],
                    ),
                ],
            ),
        );
        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: AssignmentsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byType(ExpansionTile));
        await tester.pumpAndSettle();

        expect(find.text('Open booking'), findsNothing);
    });

    testWidgets('assignment card explains disabled open booking options without a seat', (
        tester,
    ) async {
        final api = _FakeApi(
            Assignments(
                roundId: 1,
                publishedAt: DateTime.utc(2026, 8, 14),
                seatCapacity: 4,
                days: [
                    AssignmentDay(
                        date: DateTime(2026, 8, 17),
                        weekday: 'MONDAY',
                        myStatus: MyStatus.noBid,
                        occupiedSeatCount: 1,
                        assignedEmployeeCount: 1,
                        assignableSeatCapacity: 4,
                        participants: const [],
                        openBookingOptions: const [
                            OpenBookingOption(
                                attendancePeriod: AttendancePeriod.fullDay,
                                available: false,
                                requiredTokens: 45,
                                bookingAllowed: false,
                                seatSelectionRequired: false,
                                availableSeatCount: 0,
                                disabledReason: 'INSUFFICIENT_POST_ALLOCATION_FUNDS',
                            ),
                        ],
                    ),
                ],
            ),
        );
        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(body: AssignmentsScreen(api: api)),
            ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byType(ExpansionTile));
        await tester.pumpAndSettle();

        expect(find.text('Open booking'), findsOneWidget);
        expect(
            find.text(
                'This booking requires 45 tokens. You do not have enough relinquishment credits '
                'or predecessor tokens available.',
            ),
            findsOneWidget,
        );
        expect(tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Book')).onPressed, isNull);
    });

    testWidgets('seat assignments show total late-booking funds and their sources', (
        tester,
    ) async {
        final api = _FakeApi(
            Assignments(
                roundId: 1,
                publishedAt: DateTime.utc(2026, 8, 14),
                seatCapacity: 4,
                availableCreditTokens: 12,
                lateBookingAvailableTokens: 52,
                lateBookingCreditTokens: 12,
                lateBookingCarryInTokens: 40,
                creditExpiresAt: DateTime.utc(2026, 8, 22),
                days: [
                    AssignmentDay(
                        date: DateTime(2026, 8, 17),
                        weekday: 'MONDAY',
                        myStatus: MyStatus.noBid,
                        occupiedSeatCount: 0,
                        assignedEmployeeCount: 0,
                        assignableSeatCapacity: 4,
                        participants: const [],
                    ),
                ],
            ),
        );
        await tester.pumpWidget(
            MaterialApp(home: Scaffold(body: AssignmentsScreen(api: api))),
        );
        await tester.pumpAndSettle();

        expect(find.text('52 tokens available for late bookings'), findsOneWidget);
        expect(find.textContaining('12 relinquishment credits + 40 predecessor tokens'), findsOneWidget);
        expect(find.textContaining('Relinquishment credits are used first'), findsOneWidget);
    });
}

class _FakeApi extends ApiClient {
    _FakeApi(this.value) : super(AuthService.testing(Dio(), _NoopCookieStore()));
    final Assignments value;
    @override
    Future<Assignments> latestAssignments() async => value;
}

class _NoopCookieStore implements ManagedCookieStore {
    @override
    Future<void> attach(Dio dio) async {}
    @override
    Future<void> clear() async {}
}