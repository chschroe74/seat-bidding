import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:go_router/go_router.dart';
import 'package:dio/dio.dart';
import '../../core/api_client.dart';
import '../../core/models.dart';
import 'late_seat_selector_dialog.dart';

class AssignmentsScreen extends StatefulWidget {
    const AssignmentsScreen({super.key, required this.api});
    final ApiClient api;
    @override
    State<AssignmentsScreen> createState() => _AssignmentsScreenState();
}

class _AssignmentsScreenState extends State<AssignmentsScreen> {
    late Future<Assignments> future = widget.api.latestAssignments();
    bool busy = false;
    void retry() => setState(() => future = widget.api.latestAssignments());

    Future<void> _run(Future<void> Function() action) async {
        if (busy) return;
        setState(() => busy = true);
        try {
            await action();
            retry();
            await future;
        } catch (error) {
            if (!mounted) return;
            final message = _problemDetail(error)
                    ?? 'The assignment changed. Refresh and try again.';
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
            retry();
        } finally {
            if (mounted) setState(() => busy = false);
        }
    }

    Future<bool> _confirm(String title, String detail, String action) async =>
            await showDialog<bool>(
                context: context,
                builder: (context) => AlertDialog(
                    title: Text(title),
                    content: Text(detail),
                    actions: [
                        TextButton(
                            onPressed: () => Navigator.pop(context, false),
                            child: const Text('Cancel'),
                        ),
                        FilledButton(
                            onPressed: () => Navigator.pop(context, true),
                            child: Text(action),
                        ),
                    ],
                ),
            ) ??
            false;

    Future<AvailableSeat?> _chooseSeat(
        Assignments assignments,
        AssignmentDay day,
        AttendancePeriod period,
    ) async {
        final availability = await widget.api.seatAvailability(
            assignments.roundId,
            day.date,
            period,
        );
        final plan = await widget.api.assignmentsFloorPlan(assignments.roundId);
        final svg = await widget.api.floorPlanSvg(plan.svgUrl);
        if (!mounted) return null;
        return showDialog<AvailableSeat>(
            context: context,
            builder: (context) => LateSeatSelectorDialog(
                plan: plan,
                svg: svg,
                availability: availability,
            ),
        );
    }

    Future<void> _relinquish(Assignments assignments, AssignmentDay day) async {
        final action = day.relinquishmentAction!;
        final confirmed = await _confirm(
            'Relinquish this assignment?',
            day.myStatus == MyStatus.assigned
                    ? 'This is irreversible. Your original result stays visible and the seat is released. '
                        'A credit equal to your original individual bid is created for this target week; '
                        'it is spent first and expires without carry-over.'
                    : 'This is irreversible. The seat is released and your original result stays visible. '
                        'Relinquishing a later claim or booking creates no refund or second credit.',
            'Relinquish',
        );
        if (!confirmed) return;
        await _run(() async {
            await widget.api.relinquishAssignment(assignments.roundId, day.date, action.occupancyId);
        });
    }

    Future<void> _claim(
        Assignments assignments,
        AssignmentDay day,
        OfferHistory offer,
    ) async {
        AvailableSeat? seat;
        if (offer.seatSelectionRequired) {
            try {
                seat = await _chooseSeat(assignments, day, offer.attendancePeriod);
            } catch (error) {
                _showActionError(error);
                return;
            }
            if (seat == null) return;
        }
        final confirmed = await _confirm(
            'Claim this seat offer?',
            '${offer.requiredTokens} tokens will be charged, using relinquishment credits first. '
            '${seat == null ? '' : 'Seat ${seat.seatNumber} was selected. '}'
            'The offer is rechecked when you confirm.',
            'Claim offer',
        );
        if (!confirmed) return;
        await _run(() async {
            await widget.api.claimOffer(
                assignments.roundId,
                day.date,
                offer,
                seat?.physicalSeatId,
            );
        });
    }

    Future<void> _decline(
        Assignments assignments,
        AssignmentDay day,
        OfferHistory offer,
    ) async {
        final confirmed = await _confirm(
            'Decline this offer?',
            'Declining is permanent for this released-capacity offer.',
            'Decline offer',
        );
        if (!confirmed) return;
        await _run(() async {
            await widget.api.declineOffer(assignments.roundId, day.date, offer);
        });
    }

    Future<void> _book(
        Assignments assignments,
        AssignmentDay day,
        OpenBookingOption option,
    ) async {
        AvailableSeat? seat;
        if (option.seatSelectionRequired) {
            try {
                seat = await _chooseSeat(assignments, day, option.attendancePeriod);
            } catch (error) {
                _showActionError(error);
                return;
            }
            if (seat == null) return;
        }
        final confirmed = await _confirm(
            'Book available capacity?',
            '${option.requiredTokens} tokens will be charged, using relinquishment credits first. '
            '${seat == null ? '' : 'Seat ${seat.seatNumber} was selected. '}'
            'Capacity and funding are rechecked when you confirm.',
            'Book',
        );
        if (!confirmed) return;
        await _run(() async {
            await widget.api.createOpenBooking(
                assignments.roundId,
                day.date,
                option,
                seat?.physicalSeatId,
            );
        });
    }

    void _showActionError(Object error) {
        if (!mounted) return;
        final message = _problemDetail(error)
                ?? 'Seat availability changed. Refresh and try again.';
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
        retry();
    }

    static String? _problemDetail(Object error) => switch (error) {
        Problem problem => problem.detail,
        DioException failure when failure.error is Problem =>
            (failure.error as Problem).detail,
        _ => null,
    };

    @override
    Widget build(BuildContext context) => FutureBuilder<Assignments>(
        future: future,
        builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
                return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
                return _ErrorState(
                    message: 'Assignments could not be loaded.',
                    retry: retry,
                );
            }
            final assignments = snapshot.data!;
            return RefreshIndicator(
                onRefresh: () async {
                    retry();
                    await future;
                },
                child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                        Text(
                            'Seat assignments',
                            style: Theme.of(context).textTheme.headlineMedium,
                        ),
                        if (assignments.floorPlanVersionId != null)
                            Align(alignment: Alignment.centerLeft, child: OutlinedButton.icon(
                                onPressed: () => context.go(
                                    '/assignments/${assignments.roundId}/floor-plan',
                                ),
                                icon: const Icon(Icons.map_outlined),
                                label: const Text('View floor plan'),
                            )),
                        const SizedBox(height: 8),
                        Text(
                            'Published ${DateFormat.yMd().add_Hm().format(assignments.publishedAt.toLocal())}',
                        ),
                        if (assignments.availableCreditTokens > 0)
                            Card(
                                child: ListTile(
                                    leading: const Icon(Icons.toll),
                                    title: Text(
                                        '${assignments.availableCreditTokens} relinquishment '
                                        '${assignments.availableCreditTokens == 1 ? 'credit' : 'credits'} available',
                                    ),
                                    subtitle: Text(
                                        assignments.creditExpiresAt == null
                                                ? 'Credits are applied before regular tokens in this round.'
                                                : 'Credits are applied first and expire '
                                                    '${DateFormat.yMd().add_Hm().format(assignments.creditExpiresAt!.toLocal())}.',
                                    ),
                                ),
                            ),
                        const SizedBox(height: 12),
                        for (final day in assignments.days)
                            _DayCard(
                                day: day,
                                capacity: assignments.seatCapacity,
                                busy: busy,
                                onRelinquish: () => _relinquish(assignments, day),
                                onClaim: (offer) => _claim(assignments, day, offer),
                                onDecline: (offer) => _decline(assignments, day, offer),
                                onBook: (option) => _book(assignments, day, option),
                            ),
                    ],
                ),
            );
        },
    );
}

class _DayCard extends StatelessWidget {
    const _DayCard({
        required this.day,
        required this.capacity,
        required this.busy,
        required this.onRelinquish,
        required this.onClaim,
        required this.onDecline,
        required this.onBook,
    });
    final AssignmentDay day;
    final int capacity;
    final bool busy;
    final VoidCallback onRelinquish;
    final ValueChanged<OfferHistory> onClaim;
    final ValueChanged<OfferHistory> onDecline;
    final ValueChanged<OpenBookingOption> onBook;
    @override
    Widget build(BuildContext context) {
        final today = DateUtils.isSameDay(day.date, DateTime.now());
        final color = switch (day.myStatus) {
            MyStatus.assigned => Colors.green.shade50,
            MyStatus.claimedLater || MyStatus.bookedLater => Colors.green.shade50,
            MyStatus.relinquished => Colors.orange.shade50,
            MyStatus.notAssigned => Colors.red.shade50,
            MyStatus.noBid => Theme.of(context).colorScheme.surfaceContainerLow,
        };
        final status = switch (day.myStatus) {
            MyStatus.assigned => 'Assigned',
            MyStatus.claimedLater => 'Claimed later',
            MyStatus.bookedLater => 'Booked later',
            MyStatus.relinquished => 'Relinquished',
            MyStatus.notAssigned => 'Not assigned',
            MyStatus.noBid => 'No bid',
        };
        return Card(
            color: color,
            shape: RoundedRectangleBorder(
                side: today
                        ? BorderSide(color: Theme.of(context).colorScheme.primary, width: 3)
                        : BorderSide.none,
                borderRadius: BorderRadius.circular(12),
            ),
            child: ExpansionTile(
                leading: Icon(
                    day.myStatus == MyStatus.assigned ||
                                    day.myStatus == MyStatus.claimedLater ||
                                    day.myStatus == MyStatus.bookedLater
                            ? Icons.check_circle
                            : day.myStatus == MyStatus.notAssigned
                            ? Icons.cancel
                            : Icons.event_seat_outlined,
                ),
                title: Text(
                    '${DateFormat.EEEE().format(day.date)}  ${DateFormat('dd/MM').format(day.date)}',
                ),
                subtitle: Wrap(
                    spacing: 8,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                        Text('$status${today ? ' · Today' : ''}'),
                        if (day.reservedSeatCount > 0)
                            Chip(
                                avatar: const Icon(Icons.event_busy, size: 16),
                                label: Text('${day.reservedSeatCount} reserved'),
                                visualDensity: VisualDensity.compact,
                            ),
                    ],
                ),
                children: [
                    Padding(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                        child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                                Text(
                                    '${day.reservedSeatCount} of $capacity physical seats reserved',
                                ),
                                Text(
                                    '${day.occupiedSeatCount} of ${day.assignableSeatCapacity} assignable seats occupied',
                                ),
                                Text(
                                    'Current attendance: ${day.morningOccupiedCount} morning · '
                                    '${day.afternoonOccupiedCount} afternoon · '
                                    '${day.activeEmployeeCount} employees',
                                ),
                                if (day.assignedEmployeeCount != day.occupiedSeatCount)
                                    Text('${day.assignedEmployeeCount} employees assigned'),
                                if (day.reservationDescription != null) ...[
                                    const SizedBox(height: 8),
                                    Semantics(
                                        label: 'Reservation description',
                                        child: Container(
                                            padding: const EdgeInsets.all(12),
                                            color: Theme.of(
                                                context,
                                            ).colorScheme.surfaceContainerHighest,
                                            child: Column(
                                                crossAxisAlignment: CrossAxisAlignment.start,
                                                children: [
                                                    const Text(
                                                        'Reservation',
                                                        style: TextStyle(fontWeight: FontWeight.bold),
                                                    ),
                                                    Text(day.reservationDescription!),
                                                ],
                                            ),
                                        ),
                                    ),
                                ],
                                const SizedBox(height: 8),
                                if (day.currentOccupants.isNotEmpty) ...[
                                    Text(
                                        'Current occupants',
                                        style: Theme.of(context).textTheme.titleSmall,
                                    ),
                                    for (final occupant in day.currentOccupants)
                                        ListTile(
                                            dense: true,
                                            selected: occupant.isCurrentUser,
                                            leading: const Icon(Icons.person_outline),
                                            title: Text(occupant.displayName),
                                            subtitle: Text(
                                                '${occupant.attendancePeriod.label} · '
                                                '${_sourceLabel(occupant.sourceType)}'
                                                '${occupant.seatNumber == null ? '' : ' · Seat ${occupant.seatNumber}'}',
                                            ),
                                        ),
                                    const Divider(),
                                ],
                                if (day.relinquishmentAction != null)
                                    Align(
                                        alignment: Alignment.centerLeft,
                                        child: FilledButton.tonalIcon(
                                            onPressed: busy || !day.relinquishmentAction!.allowed
                                                    ? null
                                                    : onRelinquish,
                                            icon: const Icon(Icons.undo),
                                            label: const Text('Relinquish assignment'),
                                        ),
                                    ),
                                if (day.relinquishmentAction?.disabledReason != null)
                                    Text(day.relinquishmentAction!.disabledReason!),
                                for (final offer in day.offerHistory)
                                    Card(
                                        child: Padding(
                                            padding: const EdgeInsets.all(12),
                                            child: Column(
                                                crossAxisAlignment: CrossAxisAlignment.start,
                                                children: [
                                                    Text(
                                                        offer.pending ? 'Seat offer' : 'Seat-offer history',
                                                        style: Theme.of(context).textTheme.titleSmall,
                                                    ),
                                                    Text(
                                                        '${offer.attendancePeriod.label} · '
                                                        '${offer.requiredTokens} tokens · ${offer.statusText}',
                                                    ),
                                                    if (offer.deadline != null && offer.pending)
                                                        Text(
                                                            'Respond by ${DateFormat.yMd().add_Hm().format(offer.deadline!.toLocal())}',
                                                        ),
                                                    if (offer.pending)
                                                        Wrap(
                                                            spacing: 8,
                                                            children: [
                                                                FilledButton(
                                                                    onPressed: busy || !offer.claimAllowed
                                                                            ? null
                                                                            : () => onClaim(offer),
                                                                    child: const Text('Claim'),
                                                                ),
                                                                OutlinedButton(
                                                                    onPressed: busy || !offer.declineAllowed
                                                                            ? null
                                                                            : () => onDecline(offer),
                                                                    child: const Text('Decline'),
                                                                ),
                                                            ],
                                                        ),
                                                ],
                                            ),
                                        ),
                                    ),
                                if (day.openBookingOptions.isNotEmpty) ...[
                                    const Divider(),
                                    Text(
                                        'Open booking',
                                        style: Theme.of(context).textTheme.titleSmall,
                                    ),
                                    for (final option in day.openBookingOptions)
                                        ListTile(
                                            contentPadding: EdgeInsets.zero,
                                            title: Text(option.attendancePeriod.label),
                                            subtitle: Text(
                                                option.bookingAllowed
                                                        ? '${option.requiredTokens} tokens · '
                                                            '${option.availableSeatCount} compatible seats'
                                                        : option.disabledReason ?? 'Not available',
                                            ),
                                            trailing: FilledButton.tonal(
                                                onPressed: busy || !option.bookingAllowed
                                                        ? null
                                                        : () => onBook(option),
                                                child: const Text('Book'),
                                            ),
                                        ),
                                ],
                                const Divider(),
                                Text(
                                    'Original published result',
                                    style: Theme.of(context).textTheme.titleSmall,
                                ),
                                if (day.participants.isEmpty)
                                    const Text('No one bid for this day.'),
                                for (final unit in _units(day.participants)) ...[
                                    if (unit.first.unitRank == day.assignableSeatCapacity + 1)
                                        const Divider(thickness: 2),
                                    if (unit.first.unitType == AllocationUnitType.halfDayPair)
                                        Card(
                                            key: Key(
                                                'allocation-unit-${unit.first.allocationUnitId}',
                                            ),
                                            color: Theme.of(context).colorScheme.surfaceContainer,
                                            child: Column(
                                                children: [
                                                    ListTile(
                                                        dense: true,
                                                        leading: Text('#${unit.first.unitRank}'),
                                                        title: const Text('Shared physical seat'),
                                                        subtitle: Text(
                                                            '${unit.first.unitScoreTokens} tokens combined · Morning + Afternoon',
                                                        ),
                                                    ),
                                                    for (final participant in unit)
                                                        _ParticipantRow(participant: participant),
                                                ],
                                            ),
                                        )
                                    else
                                        _ParticipantRow(participant: unit.first),
                                ],
                            ],
                        ),
                    ),
                ],
            ),
        );
    }

    static String _sourceLabel(String source) => switch (source) {
        'ORIGINAL_ASSIGNMENT' => 'Original allocation',
        'WAITLIST_CLAIM' => 'Waiting-list claim',
        'ORIGINAL_REBOOKING' => 'Original rebooking',
        'OPEN_BOOKING' => 'Open booking',
        _ => source,
    };

    static List<List<AssignmentParticipant>> _units(
        List<AssignmentParticipant> participants,
    ) {
        final grouped = <int, List<AssignmentParticipant>>{};
        for (final participant in participants) {
            grouped
                    .putIfAbsent(participant.allocationUnitId, () => [])
                    .add(participant);
        }
        return grouped.values.toList()..sort(
            (left, right) => left.first.unitRank.compareTo(right.first.unitRank),
        );
    }
}

class _ParticipantRow extends StatelessWidget {
    const _ParticipantRow({required this.participant});
    final AssignmentParticipant participant;

    @override
    Widget build(BuildContext context) => ListTile(
        dense: true,
        selected: participant.isCurrentUser,
        leading: Text('#${participant.displayRank}'),
        title: Text(participant.displayName),
        subtitle: Wrap(
            spacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
                Text('${participant.tokens} tokens'),
                if (participant.attendancePeriod != AttendancePeriod.fullDay)
                    Chip(
                        label: Text(participant.attendancePeriod.label),
                        visualDensity: VisualDensity.compact,
                    ),
                if (participant.seatNumber != null)
                    Chip(
                        label: Text('Seat ${participant.seatNumber}'),
                        visualDensity: VisualDensity.compact,
                    ),
            ],
        ),
        trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
                Icon(participant.assigned ? Icons.check : Icons.close),
                const SizedBox(width: 4),
                Text(participant.assigned ? 'Assigned' : 'Not assigned'),
            ],
        ),
    );
}

class _ErrorState extends StatelessWidget {
    const _ErrorState({required this.message, required this.retry});
    final String message;
    final VoidCallback retry;
    @override
    Widget build(BuildContext context) => Center(
        child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
                const Icon(Icons.cloud_off, size: 48),
                Text(message),
                const Text('Check your connection and try again.'),
                FilledButton(onPressed: retry, child: const Text('Retry')),
            ],
        ),
    );
}