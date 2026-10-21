import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/admin/floor_plan_admin_screen.dart';
import 'package:seat_bidding/features/floor_plan/floor_plan_interactive_viewer.dart';

void main() {
    testWidgets('add-seats toggle remains active across consecutive placements', (tester) async {
        final api = _FloorPlanApi(AuthService.testing(Dio(), _NoopCookieStore()));
        await tester.pumpWidget(MaterialApp(home: Scaffold(body: FloorPlanAdminScreen(api: api))));
        await tester.pumpAndSettle();

        expect(find.text('Add seats on map'), findsOneWidget);
        await tester.tap(find.text('Add seats on map'));
        await tester.pump();
        expect(tester.widget<FilterChip>(find.byType(FilterChip)).selected, isTrue);

        final map = tester.getRect(find.byType(FloorPlanInteractiveViewer));
        await tester.tapAt(Offset(map.left + 100, map.top + 100));
        await tester.pumpAndSettle();
        expect(api.addCalls, 1);
        expect(tester.widget<FilterChip>(find.byType(FilterChip)).selected, isTrue);

        final updatedMap = tester.getRect(find.byType(FloorPlanInteractiveViewer));
        await tester.tapAt(Offset(updatedMap.left + 300, updatedMap.top + 150));
        await tester.pumpAndSettle();
        expect(api.addCalls, 2);
        expect(tester.widget<FilterChip>(find.byType(FilterChip)).selected, isTrue);

        await tester.tap(find.text('Add seats on map'));
        await tester.pump();
        expect(tester.widget<FilterChip>(find.byType(FilterChip)).selected, isFalse);

        final controller = tester.widget<InteractiveViewer>(find.byType(InteractiveViewer))
            .transformationController!;
        controller.value = Matrix4.diagonal3Values(0.5, 0.5, 0.5);
        await tester.tap(find.byTooltip('Select seat 1'));
        await tester.pump();
        expect(controller.value.getMaxScaleOnAxis(), 0.5);
        expect(find.byTooltip('Clear seat selection'), findsOneWidget);
        await tester.tap(find.byTooltip('Unselect seat 1'));
        await tester.pump();
        expect(controller.value.getMaxScaleOnAxis(), 0.5);
        expect(find.byTooltip('Clear seat selection'), findsNothing);

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

    int addCalls = 0;
    int revision = 0;
    final seats = <AdminFloorPlanSeat>[];

    AdminFloorPlan get value => AdminFloorPlan(
        stateRevision: revision,
        watermark: 0,
        pendingDeactivation: false,
        draft: AdminFloorPlanVersion(
            id: 1,
            versionNumber: 1,
            status: 'DRAFT',
            draftRevision: revision,
            svgUrl: '/floor-plan/versions/1/svg',
            viewBox: const FloorPlanViewBox(minX: 0, minY: 0, width: 100, height: 100),
            seats: List.of(seats),
        ),
    );

    @override
    Future<AdminFloorPlan> adminFloorPlan() async => value;

    @override
    Future<Uint8List> floorPlanSvg(String url) async => Uint8List.fromList(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">'
        '<rect width="100" height="100" fill="white"/></svg>'.codeUnits,
    );

    @override
    Future<AdminFloorPlan> addFloorPlanSeat(int draftRevision, double x, double y) async {
        addCalls++;
        revision++;
        seats.add(AdminFloorPlanSeat(
            id: addCalls,
            number: addCalls,
            x: x,
            y: y,
            inherited: false,
            state: 'ACTIVE',
        ));
        return value;
    }
}

class _NoopCookieStore implements ManagedCookieStore {
    @override
    Future<void> attach(Dio dio) async {}

    @override
    Future<void> clear() async {}
}