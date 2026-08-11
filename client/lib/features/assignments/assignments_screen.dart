import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../core/api_client.dart';
import '../../core/models.dart';

class AssignmentsScreen extends StatefulWidget {
  const AssignmentsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<AssignmentsScreen> createState() => _AssignmentsScreenState();
}

class _AssignmentsScreenState extends State<AssignmentsScreen> {
  late Future<Assignments> future = widget.api.latestAssignments();
  void retry() => setState(() => future = widget.api.latestAssignments());

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
            const SizedBox(height: 8),
            Text(
              'Published ${DateFormat.yMd().add_Hm().format(assignments.publishedAt.toLocal())}',
            ),
            const SizedBox(height: 12),
            for (final day in assignments.days)
              _DayCard(day: day, capacity: assignments.seatCapacity),
          ],
        ),
      );
    },
  );
}

class _DayCard extends StatelessWidget {
  const _DayCard({required this.day, required this.capacity});
  final AssignmentDay day;
  final int capacity;
  @override
  Widget build(BuildContext context) {
    final today = DateUtils.isSameDay(day.date, DateTime.now());
    final color = switch (day.myStatus) {
      MyStatus.assigned => Colors.green.shade50,
      MyStatus.notAssigned => Colors.red.shade50,
      MyStatus.noBid => Theme.of(context).colorScheme.surfaceContainerLow,
    };
    final status = switch (day.myStatus) {
      MyStatus.assigned => 'Assigned',
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
          day.myStatus == MyStatus.assigned
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
                  '${day.assignedCount} of ${day.assignableSeatCapacity} assignable seats assigned',
                ),
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
                if (day.participants.isEmpty)
                  const Text('No one bid for this day.'),
                for (final participant in day.participants) ...[
                  if (participant.rank == day.assignableSeatCapacity + 1)
                    const Divider(thickness: 2),
                  ListTile(
                    dense: true,
                    selected: participant.isCurrentUser,
                    leading: Text('#${participant.rank}'),
                    title: Text(participant.displayName),
                    subtitle: Text('${participant.tokens} tokens'),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(participant.assigned ? Icons.check : Icons.close),
                        const SizedBox(width: 4),
                        Text(
                          participant.assigned ? 'Assigned' : 'Not assigned',
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
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
