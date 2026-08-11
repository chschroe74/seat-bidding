import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/admin/seat_reservations_screen.dart';
import 'package:seat_bidding/app.dart';

void main() {
  testWidgets(
    'wide web admin sees reservation controls and can add a reservation',
    (tester) async {
      final auth = _auth(admin: true);
      final api = _FakeApi(auth);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SizedBox(
              width: 1000,
              child: SeatReservationAdminGate(
                auth: auth,
                api: api,
                isWeb: true,
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Seat reservations'), findsOneWidget);
      expect(find.text('Add reservation'), findsNWidgets(2));
      await tester.enterText(
        find.widgetWithText(TextField, 'Date (YYYY-MM-DD)'),
        '2030-01-07',
      );
      await tester.enterText(
        find.widgetWithText(TextField, 'Reserved seats'),
        '2',
      );
      await tester.enterText(
        find.widgetWithText(TextField, 'Description (optional)'),
        'Workshop',
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Add reservation'));
      await tester.pumpAndSettle();

      expect(api.createCalls, 1);
      expect(find.text('Reservation added.'), findsOneWidget);
      expect(api.listCalls, 2);
    },
  );

  testWidgets('compact web and native clients never load management controls', (
    tester,
  ) async {
    final auth = _auth(admin: true);
    final compactApi = _FakeApi(auth);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 500,
            child: SeatReservationAdminGate(
              auth: auth,
              api: compactApi,
              isWeb: true,
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.textContaining('desktop web application'), findsOneWidget);
    expect(find.text('Add reservation'), findsNothing);
    expect(compactApi.listCalls, 0);

    final nativeApi = _FakeApi(auth);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 1000,
            child: SeatReservationAdminGate(
              auth: auth,
              api: nativeApi,
              isWeb: false,
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('Add reservation'), findsNothing);
    expect(nativeApi.listCalls, 0);
  });

  testWidgets('admin navigation is present only on wide web layouts', (
    tester,
  ) async {
    final auth = _auth(admin: true);
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: SizedBox(
            width: 1000,
            child: AppShell(
              auth: auth,
              location: '/assignments',
              isWeb: true,
              child: const SizedBox(),
            ),
          ),
        ),
      ),
    );
    expect(find.text('Admin'), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: SizedBox(
            width: 500,
            child: AppShell(
              auth: auth,
              location: '/assignments',
              isWeb: true,
              child: const SizedBox(),
            ),
          ),
        ),
      ),
    );
    expect(find.text('Admin'), findsNothing);

    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: SizedBox(
            width: 1000,
            child: AppShell(
              auth: auth,
              location: '/assignments',
              isWeb: false,
              child: const SizedBox(),
            ),
          ),
        ),
      ),
    );
    expect(find.text('Admin'), findsNothing);
  });
}

AuthService _auth({required bool admin}) {
  final auth = AuthService.testing(Dio(), _NoopCookieStore());
  auth.user = CurrentUser(
    id: 1,
    firstName: 'Ada',
    lastName: 'Admin',
    email: 'ada@example.com',
    isAdmin: admin,
  );
  return auth;
}

class _FakeApi extends ApiClient {
  _FakeApi(super.auth);
  int listCalls = 0;
  int createCalls = 0;
  final reservations = <SeatReservation>[];

  @override
  Future<SeatReservationList> seatReservations(
    DateTime from,
    DateTime to,
  ) async {
    listCalls++;
    return SeatReservationList(
      serverTime: DateTime.utc(2026, 8, 11),
      timeZone: 'Europe/Berlin',
      reservations: List.of(reservations),
    );
  }

  @override
  Future<SeatReservation> createSeatReservation(
    DateTime date,
    int count,
    String? description,
  ) async {
    createCalls++;
    final value = SeatReservation(
      id: 1,
      date: date,
      reservedSeatCount: count,
      physicalSeatCapacity: 9,
      description: description,
      mutable: true,
    );
    reservations.add(value);
    return value;
  }
}

class _NoopCookieStore implements ManagedCookieStore {
  @override
  Future<void> attach(Dio dio) async {}
  @override
  Future<void> clear() async {}
}
