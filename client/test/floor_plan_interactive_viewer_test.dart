import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/features/floor_plan/floor_plan_interactive_viewer.dart';

void main() {
    testWidgets('mouse zoom requires Control and allows a distant overview', (tester) async {
        await tester.pumpWidget(const MaterialApp(home: Scaffold(body: Center(
            child: SizedBox(width: 600, height: 400,
                child: FloorPlanInteractiveViewer(child: ColoredBox(color: Colors.blue))),
        ))));

        InteractiveViewer viewer() => tester.widget(find.byType(InteractiveViewer));
        expect(viewer().minScale, 0.2);
        expect(viewer().scaleFactor, 800);
        expect(viewer().boundaryMargin, const EdgeInsets.all(double.infinity));

        final controller = viewer().transformationController!;
        final distant = Matrix4.diagonal3Values(0.2, 0.2, 0.2);
        distant.storage[12] = -5000;
        distant.storage[13] = -5000;
        controller.value = distant;
        expect(controller.value.storage[12], greaterThanOrEqualTo(-80));
        expect(controller.value.storage[13], greaterThanOrEqualTo(-40));
        await tester.tap(find.byTooltip('Reset map view'));
        await tester.pump();
        expect(controller.value, Matrix4.identity());

        final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
        await mouse.addPointer(location: tester.getCenter(find.byType(FloorPlanInteractiveViewer)));
        await tester.pump();
        expect(viewer().scaleEnabled, isFalse);

        await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
        await tester.pump();
        expect(viewer().scaleEnabled, isTrue);

        await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
        await tester.pump();
        expect(viewer().scaleEnabled, isFalse);
        await mouse.removePointer();
    });
}