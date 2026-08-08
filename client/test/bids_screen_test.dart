import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/bidding/bids_screen.dart';

void main() {
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
    await tester.ensureVisible(clearButton);
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
}

class _FakeApi extends ApiClient {
  _FakeApi(this.biddingContext)
    : super(AuthService.testing(Dio(), _NoopCookieStore()));

  final BiddingContext biddingContext;
  int saveCalls = 0;

  @override
  Future<BiddingContext> currentBidding() async => biddingContext;

  @override
  Future<BiddingContext> replaceBids(
    int roundId,
    Map<DateTime, int> values,
  ) async {
    saveCalls++;
    return biddingContext;
  }
}

class _NoopCookieStore implements ManagedCookieStore {
  @override
  Future<void> attach(Dio dio) async {}

  @override
  Future<void> clear() async {}
}
