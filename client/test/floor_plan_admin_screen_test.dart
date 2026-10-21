import 'package:dio/dio.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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

    testWidgets('Ctrl aligns add preview independently on both axes', (tester) async {
        final api = _FloorPlanApi(AuthService.testing(Dio(), _NoopCookieStore()), initialSeats: const [
            AdminFloorPlanSeat(id: 1, number: 1, x: 0.2, y: 0.2, inherited: false, state: 'ACTIVE'),
            AdminFloorPlanSeat(id: 2, number: 2, x: 0.7, y: 0.3, inherited: false, state: 'ACTIVE'),
        ]);
        await tester.pumpWidget(MaterialApp(home: Scaffold(body: FloorPlanAdminScreen(api: api))));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Add seats on map'));
        await tester.pump();

        final map = tester.getRect(find.byType(FloorPlanInteractiveViewer));
        final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
        final hoverRegion = tester.widget<MouseRegion>(find.byWidgetPredicate(
            (widget) => widget is MouseRegion && widget.onHover != null));
        hoverRegion.onHover!(PointerHoverEvent(
            position: Offset(map.width / 2, map.height / 2), kind: PointerDeviceKind.mouse));
        await tester.pump();
        expect(find.byKey(const ValueKey('floor-plan-guide-x')), findsOneWidget);
        expect(find.byKey(const ValueKey('floor-plan-guide-y')), findsOneWidget);

        await mouse.addPointer(location: map.center);
        await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
        final target = Offset(map.left + map.width * 0.2 + 5, map.top + map.height * 0.3 - 5);
        await tester.tapAt(target);
        await tester.pumpAndSettle();
        await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);

        expect(api.addedPoints, hasLength(1));
        expect(api.addedPoints.single.dx, closeTo(0.2, 0.0001));
        expect(api.addedPoints.single.dy, closeTo(0.3, 0.0001));
        await mouse.removePointer();
    });

    testWidgets('drag preview excludes the dragged marker and commits its visible position', (tester) async {
        final api = _FloorPlanApi(AuthService.testing(Dio(), _NoopCookieStore()), initialSeats: const [
            AdminFloorPlanSeat(id: 1, number: 1, x: 0.25, y: 0.25, inherited: false, state: 'ACTIVE'),
        ]);
        await tester.pumpWidget(MaterialApp(home: Scaffold(body: FloorPlanAdminScreen(api: api))));
        await tester.pumpAndSettle();

        final map = tester.getRect(find.byType(FloorPlanInteractiveViewer));
        final marker = tester.getCenter(find.byTooltip('Select seat 1'));
        final destination = Offset(marker.dx + 5, marker.dy + 80);
        final controller = tester.widget<InteractiveViewer>(find.byType(InteractiveViewer))
            .transformationController!;
        final initialTransform = controller.value.clone();
        final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
        await mouse.addPointer(location: marker);
        await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
        await mouse.down(marker);
        await mouse.moveTo(destination);
        await tester.pump();
        expect(find.byKey(const ValueKey('floor-plan-guide-x')), findsOneWidget);
        await mouse.up();
        await tester.pumpAndSettle();
        await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);

        expect(api.moveCalls, 1);
        expect(api.movedPoint!.dx, closeTo((destination.dx - map.left) / map.width, 0.001));
        expect(api.movedPoint!.dx, isNot(closeTo(0.25, 0.001)));
        expect(api.movedPoint!.dy, closeTo((destination.dy - map.top) / map.height, 0.001));
        expect(controller.value.storage, orderedEquals(initialTransform.storage));
        await mouse.removePointer();
    });
}

class _FloorPlanApi extends ApiClient {
    _FloorPlanApi(super.auth, {List<AdminFloorPlanSeat> initialSeats = const []}) {
        seats.addAll(initialSeats);
    }

    int addCalls = 0;
    int moveCalls = 0;
    int revision = 0;
    final seats = <AdminFloorPlanSeat>[];
    final addedPoints = <Offset>[];
    Offset? movedPoint;

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
        addedPoints.add(Offset(x, y));
        final nextNumber = seats.isEmpty ? 1
            : seats.map((seat) => seat.number).reduce((left, right) => left > right ? left : right) + 1;
        seats.add(AdminFloorPlanSeat(
            id: nextNumber,
            number: nextNumber,
            x: x,
            y: y,
            inherited: false,
            state: 'ACTIVE',
        ));
        return value;
    }

    @override
    Future<AdminFloorPlan> moveFloorPlanSeat(int seatId, int draftRevision, double x, double y) async {
        moveCalls++;
        revision++;
        movedPoint = Offset(x, y);
        final index = seats.indexWhere((seat) => seat.id == seatId);
        final seat = seats[index];
        seats[index] = AdminFloorPlanSeat(
            id: seat.id,
            number: seat.number,
            x: x,
            y: y,
            inherited: seat.inherited,
            state: seat.state,
        );
        return value;
    }
}

class _NoopCookieStore implements ManagedCookieStore {
    @override
    Future<void> attach(Dio dio) async {}

    @override
    Future<void> clear() async {}
}