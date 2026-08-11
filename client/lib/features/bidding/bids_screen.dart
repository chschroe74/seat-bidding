import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import '../../core/api_client.dart';
import '../../core/models.dart';
import 'bid_draft.dart';

class BidsScreen extends StatefulWidget {
  const BidsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<BidsScreen> createState() => _BidsScreenState();
}

class _BidsScreenState extends State<BidsScreen> with WidgetsBindingObserver {
  BiddingContext? contextData;
  BidDraft? draft;
  final controllers = <DateTime, TextEditingController>{};
  final selected = <DateTime>{};
  String? message;
  bool dirty = false;
  bool saving = false;
  bool refreshing = false;
  Timer? ticker;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    load();
    ticker = Timer.periodic(
      const Duration(seconds: 30),
      (_) => mounted ? setState(() {}) : null,
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    ticker?.cancel();
    for (final value in controllers.values) {
      value.dispose();
    }
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && contextData != null) {
      load();
    }
  }

  Future<void> load() async {
    if (refreshing) return;
    if (mounted) setState(() => refreshing = true);
    try {
      final loaded = await widget.api.currentBidding();
      if (!mounted) return;
      if (contextData?.roundId == loaded.roundId && dirty) {
        setState(() {
          contextData = loaded;
          refreshing = false;
          message =
              'Seat availability refreshed; your unsaved bids were preserved.';
        });
        return;
      }
      final discardedDraft = dirty && contextData?.roundId != loaded.roundId;
      for (final value in controllers.values) {
        value.dispose();
      }
      controllers.clear();
      final values = {for (final day in loaded.days) day.date: day.tokens};
      for (final entry in values.entries) {
        controllers[entry.key] = TextEditingController(
          text: entry.value == 0 ? '' : '${entry.value}',
        );
      }
      setState(() {
        contextData = loaded;
        draft = BidDraft(
          startingBalance: loaded.startingBalance,
          values: values,
        );
        dirty = false;
        refreshing = false;
        message = discardedDraft
            ? 'The bidding round changed. Your previous unsaved draft was discarded.'
            : null;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          refreshing = false;
          message = 'Bids could not be loaded. Check your connection.';
        });
      }
    }
  }

  void changed(DateTime date, String raw) {
    final value = int.tryParse(raw) ?? 0;
    setState(() {
      draft!.values[date] = value;
      dirty = true;
      message = null;
      if (value > 0) selected.remove(date);
    });
  }

  void autoDistribute() {
    if (draft!.autoDistribute(selected)) {
      for (final date in selected) {
        final value = draft!.values[date] ?? 0;
        controllers[date]!.text = value == 0 ? '' : '$value';
      }
      setState(() {
        dirty = true;
        message =
            'Remaining tokens were divided evenly; any remainder stays unallocated.';
      });
    } else {
      setState(
        () => message =
            'Too few tokens remain, or no eligible zero-value days are selected.',
      );
    }
  }

  void clear() {
    draft!.clear();
    for (final controller in controllers.values) {
      controller.clear();
    }
    setState(() {
      selected.clear();
      dirty = true;
      message = 'Bids cleared locally. Select Save bids to apply this change.';
    });
  }

  Future<void> save() async {
    setState(() {
      saving = true;
      message = null;
    });
    try {
      final updated = await widget.api.replaceBids(
        contextData!.roundId,
        draft!.values,
      );
      if (!mounted) return;
      setState(() {
        contextData = updated;
        draft = BidDraft(
          startingBalance: updated.startingBalance,
          values: {for (final day in updated.days) day.date: day.tokens},
        );
        dirty = false;
        saving = false;
        message = 'Bids saved.';
      });
    } catch (error) {
      if (!mounted) return;
      final attemptedRound = contextData!.roundId;
      if (_status(error) == 409) {
        setState(() => saving = false);
        await load();
        if (!mounted) return;
        setState(() {
          message = contextData!.roundId == attemptedRound
              ? '${_detail(error)} Your unsaved bids were preserved.'
              : 'The bidding round changed. Your previous unsaved draft was discarded.';
        });
        return;
      }
      setState(() {
        saving = false;
        message = error.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (contextData == null || draft == null) {
      return Center(
        child: message == null
            ? const CircularProgressIndicator()
            : Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(message!),
                  FilledButton(onPressed: load, child: const Text('Retry')),
                ],
              ),
      );
    }
    final cutoff = contextData!.cutoffAt.toLocal();
    final remainingTime = contextData!.cutoffAt.difference(
      DateTime.now().toUtc(),
    );
    return PopScope(
      canPop: !dirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (!didPop && dirty) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Save or discard your changes before leaving.'),
            ),
          );
        }
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Place bids',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
              ),
              IconButton(
                key: const Key('refresh-bidding-context'),
                tooltip: 'Refresh bids and seat availability',
                onPressed: refreshing || saving ? null : load,
                icon: refreshing
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.refresh),
              ),
            ],
          ),
          Text(
            'Cutoff: ${DateFormat.yMd().add_Hm().format(cutoff)} (${contextData!.cutoffTimeZone})',
          ),
          Text(
            remainingTime.isNegative
                ? 'Bidding has closed; refresh for the next round.'
                : '${remainingTime.inHours} hours remaining',
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Wrap(
                spacing: 24,
                children: [
                  Text('Starting: ${draft!.startingBalance}'),
                  Text('Bid total: ${draft!.total}'),
                  Text(
                    'Available: ${draft!.remaining}',
                    style: TextStyle(color: draft!.isValid ? null : Colors.red),
                  ),
                ],
              ),
            ),
          ),
          if (!draft!.isValid)
            const Text(
              'Bid total must not exceed your starting balance.',
              style: TextStyle(color: Colors.red),
            ),
          for (final day in contextData!.days)
            Card(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ListTile(
                    title: Text(
                      '${DateFormat.EEEE().format(day.date)}  ${DateFormat('dd/MM').format(day.date)}',
                    ),
                    leading: Checkbox(
                      value: selected.contains(day.date),
                      onChanged: (draft!.values[day.date] ?? 0) > 0
                          ? null
                          : (value) => setState(
                              () => value!
                                  ? selected.add(day.date)
                                  : selected.remove(day.date),
                            ),
                    ),
                    subtitle: TextField(
                      controller: controllers[day.date],
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      onChanged: (value) => changed(day.date, value),
                      decoration: const InputDecoration(
                        labelText: 'Tokens',
                        helperText: 'Whole tokens, zero means no bid',
                      ),
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          tooltip: 'Add one token',
                          onPressed: draft!.remaining <= 0
                              ? null
                              : () {
                                  final next =
                                      (draft!.values[day.date] ?? 0) + 1;
                                  controllers[day.date]!.text = '$next';
                                  changed(day.date, '$next');
                                },
                          icon: const Icon(Icons.add),
                        ),
                        IconButton(
                          tooltip: 'Remove one token',
                          onPressed: (draft!.values[day.date] ?? 0) == 0
                              ? null
                              : () {
                                  final next =
                                      (draft!.values[day.date] ?? 0) - 1;
                                  controllers[day.date]!.text = next == 0
                                      ? ''
                                      : '$next';
                                  changed(day.date, '$next');
                                },
                          icon: const Icon(Icons.remove),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    key: Key(
                      'reservation-context-${day.date.toIso8601String()}',
                    ),
                    margin: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.surfaceContainerLow,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Wrap(
                          spacing: 8,
                          runSpacing: 6,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            if (day.reservedSeatCount > 0)
                              Chip(
                                avatar: const Icon(Icons.event_busy, size: 18),
                                label: Text(
                                  '${day.reservedSeatCount} reserved',
                                ),
                              ),
                            Text(
                              '${day.assignableSeatCapacity} of ${contextData!.seatCapacity} seats available for assignment',
                              style: Theme.of(context).textTheme.labelLarge,
                            ),
                          ],
                        ),
                        if (day.reservationDescription != null) ...[
                          const SizedBox(height: 6),
                          Text(
                            day.reservationDescription!,
                            key: Key(
                              'reservation-description-${day.date.toIso8601String()}',
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
          const Text(
            'Reservations reduce seats available for assignment. They do not change your token balance, bid limits, or bid cost.',
          ),
          const Text(
            'Only successful bids are deducted. All remaining tokens—including unsuccessful bids—are still subject to the carry-over cap.',
          ),
          if (message != null)
            Semantics(
              liveRegion: true,
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(message!),
              ),
            ),
          Wrap(
            spacing: 12,
            children: [
              OutlinedButton(
                onPressed: autoDistribute,
                child: const Text('Auto-distribute'),
              ),
              OutlinedButton(
                key: const Key('clear-bids'),
                onPressed: saving ? null : clear,
                child: const Text('Clear'),
              ),
              FilledButton(
                onPressed:
                    !draft!.isValid ||
                        saving ||
                        !dirty ||
                        remainingTime.isNegative
                    ? null
                    : save,
                child: Text(saving ? 'Saving…' : 'Save bids'),
              ),
            ],
          ),
        ],
      ),
    );
  }

  static int? _status(Object error) => error is DioException
      ? error.response?.statusCode ??
            (error.error is Problem ? (error.error as Problem).status : null)
      : error is Problem
      ? error.status
      : null;

  static String _detail(Object error) =>
      error is DioException && error.error is Problem
      ? (error.error as Problem).detail
      : error is Problem
      ? error.detail
      : 'Bids could not be saved.';
}
