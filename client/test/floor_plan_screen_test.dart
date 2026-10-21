import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/floor_plan/floor_plan_overlay_card.dart';
import 'package:seat_bidding/features/floor_plan/floor_plan_screen.dart';

void main() {
    testWidgets('seat preferences use the shared movable collapsible overlay', (tester) async {
        final api = _FloorPlanApi(AuthService.testing(Dio(), _NoopCookieStore()));
        await tester.pumpWidget(MaterialApp(home: Scaffold(body: FloorPlanScreen(api: api))));
        await tester.pumpAndSettle();

        expect(find.byType(FloorPlanOverlayCard), findsOneWidget);
        expect(find.text('Seats (2)'), findsOneWidget);
        await tester.tap(find.text('Seat 1'));
        await tester.pump();
        expect(find.text('1 star'), findsOneWidget);

        final originalCardPosition = tester.getTopLeft(find.byType(Card));
        await tester.drag(find.text('Seats (2)'), const Offset(-80, 40));
        await tester.pump();
        expect(tester.getTopLeft(find.byType(Card)), isNot(originalCardPosition));

        await tester.tap(find.byTooltip('Collapse seat list'));
        await tester.pump();
        expect(find.text('Seat 1'), findsNothing);
        expect(find.byTooltip('Expand seat list'), findsOneWidget);
    });
}

class _FloorPlanApi extends ApiClient {
    _FloorPlanApi(super.auth);

    @override
    Future<FloorPlan?> currentFloorPlan() async => FloorPlan(
        roundId: 1,
        versionId: 1,
        svgUrl: '/floor-plan/versions/1/svg',
        viewBox: const FloorPlanViewBox(minX: 0, minY: 0, width: 100, height: 100),
        cutoffAt: DateTime.utc(2026, 10, 20),
        serverTime: DateTime.utc(2026, 10, 16),
        preferenceRevision: 0,
        effectiveForCurrentRound: true,
        seats: [
            const FloorPlanSeat(id: 1, number: 1, x: 0.25, y: 0.25, stars: 0),
            const FloorPlanSeat(id: 2, number: 2, x: 0.75, y: 0.75, stars: 0),
        ],
    );

    @override
    Future<Uint8List> floorPlanSvg(String url) async => Uint8List.fromList(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">'
        '<rect width="100" height="100" fill="white"/></svg>'.codeUnits,
    );
}

class _NoopCookieStore implements ManagedCookieStore {
    @override
    Future<void> attach(Dio dio) async {}

    @override
    Future<void> clear() async {}
}