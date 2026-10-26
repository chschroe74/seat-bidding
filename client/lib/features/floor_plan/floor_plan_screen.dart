import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/models.dart';
import 'floor_plan_interactive_viewer.dart';
import 'floor_plan_overlay_card.dart';
import 'floor_plan_svg_image.dart';

class FloorPlanScreen extends StatefulWidget {
    const FloorPlanScreen({super.key, required this.api, this.readOnly = false});
    final ApiClient api;
    final bool readOnly;

    @override
    State<FloorPlanScreen> createState() => _FloorPlanScreenState();
}

class _FloorPlanScreenState extends State<FloorPlanScreen> {
    FloorPlan? plan;
    Uint8List? svg;
    bool loading = true;
    bool saving = false;
    bool dirty = false;
    String? error;

    @override
    void initState() {
        super.initState();
        load();
    }

    Future<void> load() async {
        setState(() { loading = true; error = null; });
        try {
            final loaded = widget.readOnly ? await widget.api.latestAssignmentsFloorPlan()
                : await widget.api.currentFloorPlan();
            if (!mounted) return;
            setState(() { plan = loaded; svg = null; dirty = false; });
            if (loaded == null) {
                setState(() => loading = false);
                return;
            }
            final bytes = await widget.api.floorPlanSvg(loaded.svgUrl);
            if (mounted) setState(() { svg = bytes; loading = false; });
        } on DioException catch (failure) {
            if (mounted) setState(() { loading = false; error = _detail(failure); });
        }
    }

    void cycle(FloorPlanSeat seat) {
        if (plan == null || saving || widget.readOnly) return;
        final hasPreferred = plan!.seats.any((value) => value.id != seat.id && value.stars == 3);
        final maximum = hasPreferred ? 2 : 3;
        final next = seat.stars >= maximum ? 0 : seat.stars + 1;
        setState(() {
            final index = plan!.seats.indexWhere((value) => value.id == seat.id);
            plan!.seats[index] = seat.copyWith(stars: next);
            dirty = true;
        });
    }

    Future<void> save() async {
        final current = plan;
        if (current == null) return;
        setState(() { saving = true; error = null; });
        try {
            final saved = await widget.api.replaceSeatPreferences(current, current.seats);
            if (mounted) setState(() { plan = saved; saving = false; dirty = false; });
        } on DioException catch (failure) {
            if (!mounted) return;
            setState(() { saving = false; error = _detail(failure); });
            if (failure.response?.statusCode == 409) await load();
        }
    }

    @override
    Widget build(BuildContext context) {
        if (loading) return const Center(child: CircularProgressIndicator());
        if (plan == null) return const Center(child: Text('No floor plan is available for the current round.'));
        return ListView(
            padding: const EdgeInsets.all(16),
            children: [
                Text(widget.readOnly ? 'Assignment floor plan' : 'Seat preferences',
                    style: Theme.of(context).textTheme.headlineMedium),
                Text(widget.readOnly ? 'Numbered floor plan for the latest published assignments.'
                    : 'Choose one preferred three-star seat and any number of one- or two-star seats. Unrated seats remain possible.'),
                const Text('Drag to pan. Hold Ctrl and use the mouse wheel to zoom; touch users can pinch.'),
                const SizedBox(height: 12),
                AspectRatio(
                    aspectRatio: plan!.viewBox.width / plan!.viewBox.height,
                    child: LayoutBuilder(builder: (context, mapConstraints) => Stack(children: [
                        Positioned.fill(child: FloorPlanInteractiveViewer(
                            key: ValueKey('floor-plan-${plan!.versionId}'),
                            child: LayoutBuilder(builder: (context, constraints) => Stack(children: [
                                if (svg != null) Positioned.fill(child: FloorPlanSvgImage(bytes: svg!,
                                    semanticsLabel: 'Office floor plan')),
                                for (final seat in plan!.seats)
                                    Positioned(
                                        left: seat.x * constraints.maxWidth - 22,
                                        top: seat.y * constraints.maxHeight - 22,
                                        child: _SeatButton(seat: seat, showStars: !widget.readOnly,
                                            onPressed: widget.readOnly ? null : () => cycle(seat)),
                                    ),
                            ])),
                        )),
                        FloorPlanOverlayCard(
                            key: ValueKey('seat-list-${plan!.versionId}'),
                            mapSize: mapConstraints.biggest,
                            title: 'Seats (${plan!.seats.length})',
                            rowCount: plan!.seats.length,
                            rowExtent: 56,
                            child: ListView(padding: EdgeInsets.zero, children: [
                                for (final seat in plan!.seats)
                                    ListTile(
                                        dense: true,
                                        visualDensity: VisualDensity.compact,
                                        title: Text('Seat ${seat.number}'),
                                        subtitle: widget.readOnly ? null : Text(seat.stars == 0
                                            ? 'Unrated' : '${seat.stars} star${seat.stars == 1 ? '' : 's'}'),
                                        trailing: widget.readOnly ? null : _Stars(stars: seat.stars),
                                        onTap: widget.readOnly ? null : () => cycle(seat),
                                    ),
                            ]),
                        ),
                    ])),
                ),
                if (!widget.readOnly && !plan!.effectiveForCurrentRound)
                    const Text('Changes saved now apply to future rounds because the current cutoff has passed.'),
                if (error != null) Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                const SizedBox(height: 8),
                if (!widget.readOnly) Align(alignment: Alignment.centerLeft, child: FilledButton.icon(
                    onPressed: dirty && !saving ? save : null,
                    icon: const Icon(Icons.save),
                    label: Text(saving ? 'Saving…' : 'Save preferences'),
                )),
            ],
        );
    }

    static String _detail(DioException failure) => failure.error is Problem
        ? (failure.error as Problem).detail : 'The request failed. Check your connection and retry.';
}

class _SeatButton extends StatelessWidget {
    const _SeatButton({required this.seat, required this.onPressed, required this.showStars});
    final FloorPlanSeat seat;
    final VoidCallback? onPressed;
    final bool showStars;

    @override
    Widget build(BuildContext context) => Semantics(
        button: true,
        label: showStars ? 'Seat ${seat.number}, ${seat.stars} stars' : 'Seat ${seat.number}',
        child: Material(
            color: Theme.of(context).colorScheme.primary,
            shape: const CircleBorder(),
            elevation: 3,
            child: InkWell(
                customBorder: const CircleBorder(),
                onTap: onPressed,
                child: SizedBox(width: 44, height: 44, child: Center(child: Text(showStars
                    ? '${seat.number}\n${List.filled(seat.stars, '★').join()}' : '${seat.number}',
                    textAlign: TextAlign.center, style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold,
                        color: Theme.of(context).colorScheme.onPrimary)))),
            ),
        ),
    );
}

class _Stars extends StatelessWidget {
    const _Stars({required this.stars});
    final int stars;
    @override
    Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
        for (var index = 1; index <= 3; index++) Icon(index <= stars ? Icons.star : Icons.star_border,
            color: index <= stars ? Colors.amber.shade800 : null),
    ]);
}