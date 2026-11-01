import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/assignments/late_seat_selector_dialog.dart';
import 'package:seat_bidding/features/floor_plan/floor_plan_interactive_viewer.dart';

void main() {
    testWidgets('map and late-seat markers use the floor plan aspect ratio', (tester) async {
        await tester.binding.setSurfaceSize(const Size(1000, 800));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final plan = FloorPlan(
            roundId: 1,
            versionId: 2,
            svgUrl: '/api/floor-plan/versions/2/svg',
            viewBox: const FloorPlanViewBox(minX: 0, minY: 0, width: 200, height: 400),
            cutoffAt: DateTime.utc(2026, 11, 6),
            serverTime: DateTime.utc(2026, 11, 1),
            preferenceRevision: 0,
            effectiveForCurrentRound: true,
            seats: const [
                FloorPlanSeat(id: 11, number: 1, x: 0.25, y: 0.75, stars: 0),
            ],
        );
        final availability = SeatAvailability(
            roundId: 1,
            date: DateTime.utc(2026, 11, 9),
            attendancePeriod: AttendancePeriod.fullDay,
            floorPlanVersionId: 2,
            serverTime: DateTime.utc(2026, 11, 1),
            availableSeats: const [AvailableSeat(physicalSeatId: 11, seatNumber: 1)],
        );
        final svg = Uint8List.fromList(
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 400">'
            '<rect width="200" height="400" fill="white"/></svg>'.codeUnits,
        );

        await tester.pumpWidget(
            MaterialApp(
                home: Scaffold(
                    body: LateSeatSelectorDialog(
                        plan: plan,
                        svg: svg,
                        availability: availability,
                    ),
                ),
            ),
        );
        await tester.pumpAndSettle();

        final map = tester.getRect(find.byType(FloorPlanInteractiveViewer));
        expect(map.width / map.height, closeTo(0.5, 0.001));
        final marker = tester.getRect(find.text('1'));
        expect(marker.center.dx, closeTo(map.left + map.width * 0.25, 0.01));
        expect(marker.center.dy, closeTo(map.top + map.height * 0.75, 0.01));
    });
}