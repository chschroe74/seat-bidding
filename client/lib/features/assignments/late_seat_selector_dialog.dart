import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../core/models.dart';
import '../floor_plan/floor_plan_interactive_viewer.dart';
import '../floor_plan/floor_plan_overlay_card.dart';
import '../floor_plan/floor_plan_svg_image.dart';

class LateSeatSelectorDialog extends StatelessWidget {
    const LateSeatSelectorDialog({
        super.key,
        required this.plan,
        required this.svg,
        required this.availability,
    });

    final FloorPlan plan;
    final Uint8List svg;
    final SeatAvailability availability;

    @override
    Widget build(BuildContext context) {
        final availableById = {
            for (final seat in availability.availableSeats)
                seat.physicalSeatId: seat,
        };
        final availableIds = availableById.keys.toSet();
        return Dialog(
            child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 980, maxHeight: 760),
                child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                            Row(
                                children: [
                                    Expanded(
                                        child: Text(
                                            'Choose a numbered seat · '
                                            '${availability.attendancePeriod.label}',
                                            style: Theme.of(context).textTheme.headlineSmall,
                                        ),
                                    ),
                                    IconButton(
                                        tooltip: 'Close seat selector',
                                        onPressed: () => Navigator.pop(context),
                                        icon: const Icon(Icons.close),
                                    ),
                                ],
                            ),
                            const Text(
                                'Available markers can be selected on the exact round floor plan or '
                                'from the accessible numbered list. Opening this view reserves nothing.',
                            ),
                            const SizedBox(height: 12),
                            Expanded(
                                child: LayoutBuilder(
                                    builder: (context, constraints) => Stack(
                                        children: [
                                            Positioned.fill(
                                                child: Center(
                                                    child: AspectRatio(
                                                        aspectRatio:
                                                                plan.viewBox.width / plan.viewBox.height,
                                                        child: FloorPlanInteractiveViewer(
                                                            child: LayoutBuilder(
                                                                builder: (context, mapConstraints) => Stack(
                                                                    children: [
                                                                        Positioned.fill(
                                                                            child: FloorPlanSvgImage(
                                                                                bytes: svg,
                                                                                semanticsLabel:
                                                                                        'Office floor plan for seat selection',
                                                                            ),
                                                                        ),
                                                                        for (final seat in plan.seats)
                                                                            Positioned(
                                                                                left: seat.x *
                                                                                                mapConstraints
                                                                                                        .maxWidth -
                                                                                        22,
                                                                                top: seat.y *
                                                                                                mapConstraints
                                                                                                        .maxHeight -
                                                                                        22,
                                                                                child: _AvailabilitySeatMarker(
                                                                                    seat: seat,
                                                                                    available:
                                                                                            availableIds.contains(
                                                                                                seat.id,
                                                                                            ),
                                                                                    onPressed: () => Navigator.pop(
                                                                                        context,
                                                                                        availableById[seat.id],
                                                                                    ),
                                                                                ),
                                                                            ),
                                                                    ],
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                            FloorPlanOverlayCard(
                                                mapSize: constraints.biggest,
                                                title: 'Seats (${availability.availableSeats.length} available)',
                                                rowCount: plan.seats.length,
                                                rowExtent: 56,
                                                child: ListView(
                                                    padding: EdgeInsets.zero,
                                                    children: [
                                                        for (final seat in plan.seats)
                                                            ListTile(
                                                                dense: true,
                                                                enabled: availableIds.contains(seat.id),
                                                                leading: Icon(
                                                                    availableIds.contains(seat.id)
                                                                            ? Icons.event_seat
                                                                            : Icons.block,
                                                                ),
                                                                title: Text('Seat ${seat.number}'),
                                                                subtitle: Text(
                                                                    availableIds.contains(seat.id)
                                                                            ? 'Available for this action'
                                                                            : 'Unavailable',
                                                                ),
                                                                onTap: availableIds.contains(seat.id)
                                                                        ? () => Navigator.pop(
                                                                                context,
                                                                                availableById[seat.id],
                                                                            )
                                                                        : null,
                                                            ),
                                                    ],
                                                ),
                                            ),
                                        ],
                                    ),
                                ),
                            ),
                        ],
                    ),
                ),
            ),
        );
    }
}

class _AvailabilitySeatMarker extends StatelessWidget {
    const _AvailabilitySeatMarker({
        required this.seat,
        required this.available,
        required this.onPressed,
    });

    final FloorPlanSeat seat;
    final bool available;
    final VoidCallback onPressed;

    @override
    Widget build(BuildContext context) => Semantics(
        button: available,
        enabled: available,
        label: 'Seat ${seat.number}, ${available ? 'available' : 'unavailable'}',
        child: Material(
            color: available
                    ? Theme.of(context).colorScheme.primary
                    : Theme.of(context).colorScheme.surfaceContainerHighest,
            shape: const CircleBorder(),
            elevation: available ? 3 : 0,
            child: InkWell(
                customBorder: const CircleBorder(),
                onTap: available ? onPressed : null,
                child: SizedBox(
                    width: 44,
                    height: 44,
                    child: Center(
                        child: Text(
                            '${seat.number}',
                            style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: available
                                        ? Theme.of(context).colorScheme.onPrimary
                                        : Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    );
}