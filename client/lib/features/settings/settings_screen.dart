import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/api_client.dart';
import '../../core/app_version.dart';
import '../../core/models.dart';
import '../../core/web_push_client.dart';

class SettingsScreen extends StatefulWidget {
    SettingsScreen({super.key, required this.api, WebPushClient? webPush})
        : webPush = webPush ?? WebPushClient.platform();

    final ApiClient api;
    final WebPushClient webPush;

    @override
    State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
    NotificationSettings? _settings;
    WebPushCapability? _capability;
    int? _currentDeviceId;
    Object? _error;
    bool _busy = false;

    @override
    void initState() {
        super.initState();
        _load();
    }

    Future<void> _load() async {
        setState(() {
            _error = null;
        });
        try {
            var settings = await widget.api.notificationSettings();
            final capability = await widget.webPush.capability();
            final local = await widget.webPush.currentSubscription();
            int? currentDeviceId;
            if (local != null) {
                final registered = await widget.api.registerPushDevice(
                    local,
                    capability.deviceLabel,
                );
                currentDeviceId = registered.id;
                settings = await widget.api.notificationSettings();
            }
            if (!mounted) return;
            setState(() {
                _settings = settings;
                _capability = capability;
                _currentDeviceId = currentDeviceId;
            });
        } catch (error) {
            if (mounted) setState(() => _error = error);
        }
    }

    Future<void> _update(bool enabled, ReminderWeekday weekday) async {
        setState(() => _busy = true);
        try {
            final value = await widget.api.updateNotificationSettings(
                enabled,
                weekday,
            );
            if (mounted) setState(() => _settings = value);
        } catch (error) {
            if (mounted) setState(() => _error = error);
        } finally {
            if (mounted) setState(() => _busy = false);
        }
    }

    Future<void> _enroll() async {
        final settings = _settings!;
        setState(() => _busy = true);
        try {
            final enrollment = await widget.webPush.enroll(
                settings.webPushApplicationServerKey,
            );
            final capability = await widget.webPush.capability();
            if (enrollment.subscription != null) {
                final device = await widget.api.registerPushDevice(
                    enrollment.subscription!,
                    capability.deviceLabel,
                );
                _currentDeviceId = device.id;
            }
            if (mounted) setState(() => _capability = capability);
            await _load();
        } catch (error) {
            if (mounted) setState(() => _error = error);
        } finally {
            if (mounted) setState(() => _busy = false);
        }
    }

    Future<void> _remove(RegisteredPushDevice device) async {
        final confirmed = await showDialog<bool>(
            context: context,
            builder: (context) => AlertDialog(
                title: const Text('Remove registered device?'),
                content: Text('${device.label} will no longer receive bid reminders.'),
                actions: [
                    TextButton(
                        onPressed: () => Navigator.pop(context, false),
                        child: const Text('Cancel'),
                    ),
                    FilledButton(
                        onPressed: () => Navigator.pop(context, true),
                        child: const Text('Remove device'),
                    ),
                ],
            ),
        );
        if (confirmed != true) return;
        setState(() => _busy = true);
        try {
            await widget.api.removePushDevice(device.id);
            if (_currentDeviceId == device.id) {
                await widget.webPush.unsubscribe();
                _currentDeviceId = null;
            }
            await _load();
        } finally {
            if (mounted) setState(() => _busy = false);
        }
    }

    @override
    Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
            child: Center(
                child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 760),
                    child: _content(),
                ),
            ),
        ),
    );

    Widget _content() {
        if (_settings == null && _error == null) {
            return const Center(child: CircularProgressIndicator());
        }
        if (_settings == null) {
            return Center(
                child: FilledButton.icon(
                    onPressed: _load,
                    icon: const Icon(Icons.refresh),
                    label: const Text('Retry settings'),
                ),
            );
        }
        final settings = _settings!;
        return ListView(
            padding: const EdgeInsets.all(24),
            children: [
                Text('Settings', style: Theme.of(context).textTheme.headlineMedium),
                const SizedBox(height: 16),
                SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Bid reminders enabled'),
                    subtitle: const Text(
                        'This account preference is synchronized across your devices.',
                    ),
                    value: settings.bidRemindersEnabled,
                    onChanged: _busy
                            ? null
                            : (enabled) => _update(enabled, settings.bidReminderStartWeekday),
                ),
                if (settings.bidRemindersEnabled) ..._enabledContent(settings),
                if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(
                        'Settings could not be updated. Please try again.',
                        style: TextStyle(color: Theme.of(context).colorScheme.error),
                    ),
                ],
                const SizedBox(height: 32),
                const Divider(),
                Center(
                    child: Text(
                        'Version $applicationVersion',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                    ),
                ),
            ],
        );
    }

    List<Widget> _enabledContent(NotificationSettings settings) => [
        const Divider(),
        DropdownButtonFormField<ReminderWeekday>(
            initialValue: settings.bidReminderStartWeekday,
            decoration: const InputDecoration(labelText: 'Start reminders on'),
            items: settings.schedule.weekdays
                    .map(
                        (weekday) =>
                                DropdownMenuItem(value: weekday, child: Text(weekday.label)),
                    )
                    .toList(),
            onChanged: _busy
                    ? null
                    : (weekday) {
                            if (weekday != null) _update(true, weekday);
                        },
        ),
        const SizedBox(height: 12),
        Text(
            'Reminder time: ${settings.schedule.localTime} ${settings.schedule.timeZone}',
        ),
        const SizedBox(height: 8),
        const Text(
            'Reminders continue on every following weekday until you save at least one positive bid or skip reminders for the current round.',
        ),
        if (!settings.schedule.systemEnabled) ...[
            const SizedBox(height: 12),
            const Card(
                child: ListTile(
                    leading: Icon(Icons.warning_amber),
                    title: Text('Reminder delivery is currently unavailable'),
                    subtitle: Text(
                        'Your preferences and registered devices remain saved.',
                    ),
                ),
            ),
        ],
        const SizedBox(height: 24),
        Text('This device', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        _currentDeviceControl(settings),
        const SizedBox(height: 24),
        Text(
            'Registered web devices',
            style: Theme.of(context).textTheme.titleLarge,
        ),
        if (settings.devices.isEmpty)
            const Card(
                child: ListTile(
                    leading: Icon(Icons.notifications_off),
                    title: Text('No active device is registered'),
                    subtitle: Text(
                        'Reminders are enabled, but there is nowhere to send them yet.',
                    ),
                ),
            )
        else
            ...settings.devices.map(_deviceTile),
    ];

    Widget _currentDeviceControl(NotificationSettings settings) {
        final capability = _capability;
        if (capability == null) return const LinearProgressIndicator();
        if (capability.ios && !capability.standalone) {
            return const Text(
                'On iPhone and iPad, add this site to the Home Screen and open it there before enabling notifications.',
            );
        }
        if (!capability.supported || !capability.canEnroll) {
            return const Text(
                'Web Push is not available in this browser or application context.',
            );
        }
        if (capability.permission == WebPushPermission.denied) {
            return const Text(
                'Notifications are blocked. Enable them in your browser or system settings; this application will not prompt repeatedly.',
            );
        }
        if (_currentDeviceId != null) {
            return const ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.notifications_active),
                title: Text('Notifications are enabled on this device'),
                subtitle: Text('Operating-system delivery remains best effort.'),
            );
        }
        return Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.icon(
                onPressed: _busy ? null : _enroll,
                icon: const Icon(Icons.add_alert),
                label: const Text('Enable notifications on this device'),
            ),
        );
    }

    Widget _deviceTile(RegisteredPushDevice device) {
        final date = DateFormat.yMMMd().add_Hm();
        return Card(
            child: ListTile(
                leading: Icon(
                    device.id == _currentDeviceId ? Icons.devices : Icons.web,
                ),
                title: Text(device.label),
                subtitle: Text(
                    'Registered ${date.format(device.registeredAt.toLocal())}\n'
                    'Last confirmed ${date.format(device.lastSeenAt.toLocal())}'
                    '${device.lastSuccessfulPushAt == null ? '' : '\nLast accepted push ${date.format(device.lastSuccessfulPushAt!.toLocal())}'}',
                ),
                isThreeLine: true,
                trailing: IconButton(
                    tooltip: 'Remove ${device.label}',
                    onPressed: _busy ? null : () => _remove(device),
                    icon: const Icon(Icons.delete_outline),
                ),
            ),
        );
    }
}