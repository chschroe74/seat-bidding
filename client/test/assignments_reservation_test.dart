import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/assignments/assignments_screen.dart';

void main() {
  testWidgets(
    'assignment card shows reserved and assignable capacity plus public description',
    (tester) async {
      final day = AssignmentDay(
        date: DateTime(2026, 8, 17),
        weekday: 'MONDAY',
        myStatus: MyStatus.assigned,
        assignedCount: 3,
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
      expect(find.text('3 of 3 assignable seats assigned'), findsOneWidget);
      expect(find.text('Customer workshop'), findsOneWidget);
    },
  );
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
