import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/models.dart';

class SkipRemindersScreen extends StatefulWidget {
    const SkipRemindersScreen({
        super.key,
        required this.api,
        this.requestedRoundId,
    });
    final ApiClient api;
    final int? requestedRoundId;

    @override
    State<SkipRemindersScreen> createState() => _SkipRemindersScreenState();
}

class _SkipRemindersScreenState extends State<SkipRemindersScreen> {
    NotificationSettings? _settings;
    String? _message;
    bool _busy = false;

    @override
    void initState() {
        super.initState();
        _load();
    }

    Future<void> _load() async {
        final value = await widget.api.notificationSettings();
        if (!mounted) return;
        setState(() {
            _settings = value;
            final round = value.currentRound;
            if (round == null ||
                    widget.requestedRoundId != null &&
                            widget.requestedRoundId != round.roundId) {
                _message = 'That bidding round is no longer open.';
            } else if (round.suppressed) {
                _message = 'Reminders are already skipped for the current round.';
            } else if (!round.suppressionAvailable) {
                _message =
                        'A positive bid has already been saved, so no reminder suppression is needed.';
            } else {
                _message = null;
            }
        });
    }

    Future<void> _confirm() async {
        final round = _settings!.currentRound!;
        setState(() => _busy = true);
        try {
            await widget.api.suppressBidReminders(round.roundId);
            await _load();
        } catch (_) {
            await _load();
            if (mounted && _message == null) {
                setState(
                    () => _message =
                            'The round changed. Review the refreshed state before continuing.',
                );
            }
        } finally {
            if (mounted) setState(() => _busy = false);
        }
    }

    @override
    Widget build(BuildContext context) => Scaffold(
        body: Center(
            child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 560),
                child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: _settings == null
                            ? const CircularProgressIndicator()
                            : Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment: CrossAxisAlignment.stretch,
                                    children: [
                                        Text(
                                            'Skip reminders this week?',
                                            style: Theme.of(context).textTheme.headlineSmall,
                                        ),
                                        const SizedBox(height: 16),
                                        const Text(
                                            'This stops reminders only for the current bidding round. The choice cannot be undone. If reminders remain enabled, they resume automatically for the next round.',
                                        ),
                                        if (_message != null) ...[
                                            const SizedBox(height: 16),
                                            Text(_message!),
                                        ],
                                        const SizedBox(height: 24),
                                        Wrap(
                                            spacing: 12,
                                            children: [
                                                OutlinedButton(
                                                    onPressed: () => context.go('/bids'),
                                                    child: const Text('Back to bids'),
                                                ),
                                                if (_message == null)
                                                    FilledButton(
                                                        onPressed: _busy ? null : _confirm,
                                                        child: const Text('Confirm skip'),
                                                    ),
                                            ],
                                        ),
                                    ],
                                ),
                ),
            ),
        ),
    );
}