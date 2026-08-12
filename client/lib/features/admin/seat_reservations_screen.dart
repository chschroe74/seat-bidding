import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/api_client.dart';
import '../../core/auth_service.dart';
import '../../core/models.dart';

class SeatReservationAdminGate extends StatelessWidget {
  const SeatReservationAdminGate({
    super.key,
    required this.auth,
    required this.api,
    bool? isWeb,
  }) : isWeb = isWeb ?? kIsWeb;
  final AuthService auth;
  final ApiClient api;
  final bool isWeb;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      if (!isWeb || constraints.maxWidth < 800) {
        return const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'Seat reservation management is available in the desktop web application.',
              textAlign: TextAlign.center,
            ),
          ),
        );
      }
      if (auth.user?.isAdmin != true) {
        return const Center(child: Text('Administrator access is required.'));
      }
      return SeatReservationsScreen(api: api);
    },
  );
}

class SeatReservationsScreen extends StatefulWidget {
  const SeatReservationsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<SeatReservationsScreen> createState() => _SeatReservationsScreenState();
}

class _SeatReservationsScreenState extends State<SeatReservationsScreen> {
  final date = TextEditingController();
  final count = TextEditingController();
  final description = TextEditingController();
  final from = TextEditingController();
  final to = TextEditingController();
  SeatReservationList? data;
  bool loading = true;
  bool saving = false;
  String? message;
  String? error;

  @override
  void initState() {
    super.initState();
    final today = DateTime.now();
    from.text = _format(today);
    to.text = _format(today.add(const Duration(days: 90)));
    load();
  }

  @override
  void dispose() {
    date.dispose();
    count.dispose();
    description.dispose();
    from.dispose();
    to.dispose();
    super.dispose();
  }

  Future<void> load() async {
    final range = _range();
    if (range == null) return;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final loaded = await widget.api.seatReservations(range.$1, range.$2);
      if (mounted) {
        setState(() {
          data = loaded;
          loading = false;
        });
      }
    } on DioException catch (failure) {
      if (!mounted) return;
      if (_status(failure) == 403) {
        context.go('/assignments');
        return;
      }
      setState(() {
        loading = false;
        error = _detail(failure);
      });
    }
  }

  Future<void> add() async {
    final target = DateTime.tryParse(date.text.trim());
    final seats = int.tryParse(count.text.trim());
    if (target == null ||
        date.text.trim().length != 10 ||
        seats == null ||
        seats < 1) {
      setState(
        () => error =
            'Enter a valid YYYY-MM-DD date and a positive whole-number seat count.',
      );
      return;
    }
    setState(() {
      saving = true;
      error = null;
      message = null;
    });
    try {
      await widget.api.createSeatReservation(target, seats, description.text);
      date.clear();
      count.clear();
      description.clear();
      message = 'Reservation added.';
      await load();
    } on DioException catch (failure) {
      if (!mounted) return;
      if (_status(failure) == 403) {
        context.go('/assignments');
        return;
      }
      if (_status(failure) == 409) await load();
      setState(() => error = _detail(failure));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> remove(SeatReservation reservation) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete reservation?'),
        content: Text(
          'Delete the reservation for ${_format(reservation.date)}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() {
      saving = true;
      error = null;
      message = null;
    });
    try {
      await widget.api.deleteSeatReservation(reservation.id);
      message = 'Reservation deleted.';
      await load();
    } on DioException catch (failure) {
      if (!mounted) return;
      if (_status(failure) == 403) {
        context.go('/assignments');
        return;
      }
      if (_status(failure) == 409) await load();
      setState(() => error = _detail(failure));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      Text(
        'Seat reservations',
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 16),
      Text('Add reservation', style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 8),
      Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 210,
            child: TextField(
              controller: date,
              decoration: InputDecoration(
                labelText: 'Date (YYYY-MM-DD)',
                suffixIcon: IconButton(
                  tooltip: 'Choose date',
                  icon: const Icon(Icons.calendar_month),
                  onPressed: chooseDate,
                ),
              ),
            ),
          ),
          const SizedBox(width: 16),
          SizedBox(
            width: 220,
            child: TextField(
              controller: count,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Reserved seats',
                helperText: 'Limited by physical capacity',
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: TextField(
              controller: description,
              maxLength: 500,
              decoration: const InputDecoration(
                labelText: 'Description (optional)',
                helperText:
                    'Visible to all authenticated users. Plain text only.',
              ),
            ),
          ),
        ],
      ),
      const SizedBox(height: 12),
      Align(
        alignment: Alignment.centerLeft,
        child: FilledButton.icon(
          onPressed: saving ? null : add,
          icon: const Icon(Icons.add),
          label: const Text('Add reservation'),
        ),
      ),
      if (message != null)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(
            message!,
            style: TextStyle(color: Theme.of(context).colorScheme.primary),
          ),
        ),
      if (error != null)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(
            error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ),
      const Divider(height: 40),
      Text('Reservations', style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 8),
      Wrap(
        spacing: 12,
        runSpacing: 8,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          SizedBox(
            width: 180,
            child: TextField(
              controller: from,
              decoration: const InputDecoration(labelText: 'From (inclusive)'),
            ),
          ),
          SizedBox(
            width: 180,
            child: TextField(
              controller: to,
              decoration: const InputDecoration(labelText: 'To (inclusive)'),
            ),
          ),
          OutlinedButton.icon(
            onPressed: loading ? null : load,
            icon: const Icon(Icons.filter_alt),
            label: const Text('Apply filter'),
          ),
        ],
      ),
      const SizedBox(height: 16),
      if (loading)
        const Center(child: CircularProgressIndicator())
      else if (data == null || data!.reservations.isEmpty)
        const Text('No reservations in this date range.')
      else
        for (final reservation in data!.reservations)
          _ReservationCard(
            reservation: reservation,
            deleting: saving,
            onDelete: () => remove(reservation),
          ),
      if (!loading && error != null)
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: load,
            icon: const Icon(Icons.refresh),
            label: const Text('Retry'),
          ),
        ),
    ],
  );

  Future<void> chooseDate() async {
    final selected = await showDatePicker(
      context: context,
      initialDate: DateTime.tryParse(date.text) ?? DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 3650)),
    );
    if (selected != null) date.text = _format(selected);
  }

  (DateTime, DateTime)? _range() {
    final start = DateTime.tryParse(from.text.trim());
    final end = DateTime.tryParse(to.text.trim());
    if (start == null ||
        end == null ||
        start.isAfter(end) ||
        end.difference(start).inDays > 365) {
      setState(() {
        loading = false;
        error = 'Enter an inclusive date range of no more than 366 days.';
      });
      return null;
    }
    return (start, end);
  }

  static int? _status(DioException error) => error.error is Problem
      ? (error.error as Problem).status
      : error.response?.statusCode;
  static String _detail(DioException error) => error.error is Problem
      ? (error.error as Problem).detail
      : 'The request failed. Check your connection and retry.';
  static String _format(DateTime value) =>
      DateFormat('yyyy-MM-dd').format(value);
}

class _ReservationCard extends StatelessWidget {
  const _ReservationCard({
    required this.reservation,
    required this.deleting,
    required this.onDelete,
  });
  final SeatReservation reservation;
  final bool deleting;
  final VoidCallback onDelete;
  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      title: Text(
        '${DateFormat.yMMMMEEEEd().format(reservation.date)} · ${reservation.reservedSeatCount} reserved',
      ),
      subtitle: Text(
        [
          '${reservation.physicalSeatCapacity} physical seats',
          if (reservation.description != null) reservation.description!,
          if (reservation.roundStatus != null)
            'Round: ${reservation.roundStatus}',
          if (reservation.cutoffAt != null)
            'Cutoff: ${DateFormat.yMd().add_Hm().format(reservation.cutoffAt!.toLocal())}',
          reservation.mutable
              ? 'May still be deleted'
              : 'Immutable because the cutoff has passed',
        ].join('\n'),
      ),
      isThreeLine: true,
      trailing: IconButton(
        icon: const Icon(Icons.delete_outline),
        tooltip: reservation.mutable
            ? 'Delete reservation'
            : 'Deletion is unavailable after cutoff',
        onPressed: reservation.mutable && !deleting ? onDelete : null,
      ),
    ),
  );
}
