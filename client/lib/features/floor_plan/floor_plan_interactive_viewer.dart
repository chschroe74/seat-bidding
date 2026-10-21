import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class FloorPlanInteractiveViewer extends StatefulWidget {
    const FloorPlanInteractiveViewer({
        super.key,
        required this.child,
        this.controller,
        this.panEnabled = true,
    });

    final Widget child;
    final TransformationController? controller;
    final bool panEnabled;

    @override
    State<FloorPlanInteractiveViewer> createState() => _FloorPlanInteractiveViewerState();
}

class _FloorPlanInteractiveViewerState extends State<FloorPlanInteractiveViewer> {
    late final TransformationController transformationController;
    late final bool ownsTransformationController;
    bool controlPressed = false;
    bool mouseInside = false;
    bool touchActive = false;
    bool correctingTransform = false;
    Size viewportSize = Size.zero;

    @override
    void initState() {
        super.initState();
        ownsTransformationController = widget.controller == null;
        transformationController = widget.controller ?? TransformationController();
        transformationController.addListener(_keepPlanVisible);
        controlPressed = HardwareKeyboard.instance.isControlPressed;
        HardwareKeyboard.instance.addHandler(_handleKeyEvent);
    }

    @override
    void dispose() {
        HardwareKeyboard.instance.removeHandler(_handleKeyEvent);
        transformationController.removeListener(_keepPlanVisible);
        if (ownsTransformationController) transformationController.dispose();
        super.dispose();
    }

    bool _handleKeyEvent(KeyEvent event) {
        final pressed = HardwareKeyboard.instance.isControlPressed;
        if (mounted && pressed != controlPressed) setState(() => controlPressed = pressed);
        return false;
    }

    void _setMouseInside(bool value) {
        if (value != mouseInside) setState(() => mouseInside = value);
    }

    void _setTouchActive(PointerEvent event, bool value) {
        if (event.kind == PointerDeviceKind.mouse || value == touchActive) return;
        setState(() => touchActive = value);
    }

    void _keepPlanVisible() {
        if (correctingTransform || viewportSize.isEmpty) return;
        final matrix = transformationController.value;
        final scale = matrix.getMaxScaleOnAxis();
        final minimumVisible = 40.0;
        var x = matrix.storage[12];
        var y = matrix.storage[13];
        final right = x + viewportSize.width * scale;
        final bottom = y + viewportSize.height * scale;
        if (right < minimumVisible) x += minimumVisible - right;
        if (x > viewportSize.width - minimumVisible) x -= x - viewportSize.width + minimumVisible;
        if (bottom < minimumVisible) y += minimumVisible - bottom;
        if (y > viewportSize.height - minimumVisible) y -= y - viewportSize.height + minimumVisible;
        if (x == matrix.storage[12] && y == matrix.storage[13]) return;
        final corrected = matrix.clone();
        corrected.storage[12] = x;
        corrected.storage[13] = y;
        correctingTransform = true;
        transformationController.value = corrected;
        correctingTransform = false;
    }

    void _resetView() => transformationController.value = Matrix4.identity();

    @override
    Widget build(BuildContext context) {
        final allowScale = !mouseInside || controlPressed || touchActive;
        return LayoutBuilder(builder: (context, constraints) {
            viewportSize = constraints.biggest;
            return Stack(children: [
                Positioned.fill(child: MouseRegion(
                    onEnter: (_) => _setMouseInside(true),
                    onExit: (_) => _setMouseInside(false),
                    child: Listener(
                        onPointerDown: (event) => _setTouchActive(event, true),
                        onPointerUp: (event) => _setTouchActive(event, false),
                        onPointerCancel: (event) => _setTouchActive(event, false),
                        child: InteractiveViewer(
                            transformationController: transformationController,
                            minScale: 0.2,
                            maxScale: 6,
                            scaleFactor: 800,
                            boundaryMargin: const EdgeInsets.all(double.infinity),
                            panEnabled: widget.panEnabled,
                            scaleEnabled: allowScale,
                            trackpadScrollCausesScale: mouseInside && controlPressed,
                            child: widget.child,
                        ),
                    ),
                )),
                Positioned(left: 8, bottom: 8, child: IconButton.filledTonal(
                    onPressed: _resetView,
                    tooltip: 'Reset map view',
                    icon: const Icon(Icons.center_focus_strong),
                )),
            ]);
        });
    }
}