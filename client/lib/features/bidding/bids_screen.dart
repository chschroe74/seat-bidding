import 'dart:async';
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

class _BidsScreenState extends State<BidsScreen> {
  BiddingContext? contextData;
  BidDraft? draft;
  final controllers = <DateTime, TextEditingController>{};
  final selected = <DateTime>{};
  String? message;
  bool dirty = false;
  bool saving = false;
  Timer? ticker;

  @override
  void initState() {
    super.initState();
    load();
    ticker = Timer.periodic(
      const Duration(seconds: 30),
      (_) => mounted ? setState(() {}) : null,
    );
  }

  @override
  void dispose() {
    ticker?.cancel();
    for (final value in controllers.values) {
      value.dispose();
    }
    super.dispose();
  }

  Future<void> load() async {
    try {
      final loaded = await widget.api.currentBidding();
      if (!mounted) return;
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
        message = null;
      });
    } catch (_) {
      if (mounted) {
        setState(
          () => message = 'Bids could not be loaded. Check your connection.',
        );
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
          Text('Place bids', style: Theme.of(context).textTheme.headlineMedium),
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
              child: ListTile(
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
                              final next = (draft!.values[day.date] ?? 0) + 1;
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
                              final next = (draft!.values[day.date] ?? 0) - 1;
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
}
