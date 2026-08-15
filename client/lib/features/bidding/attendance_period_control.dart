import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/models.dart';

class AttendancePeriodControl extends StatefulWidget {
    const AttendancePeriodControl({
        super.key,
        required this.period,
        required this.onChanged,
        this.enabled = true,
    });

    final AttendancePeriod period;
    final VoidCallback onChanged;
    final bool enabled;

    @override
    State<AttendancePeriodControl> createState() =>
            _AttendancePeriodControlState();
}

class _AttendancePeriodControlState extends State<AttendancePeriodControl> {
    bool focused = false;

    @override
    Widget build(BuildContext context) {
        final colorScheme = Theme.of(context).colorScheme;
        final isFullDay = widget.period == AttendancePeriod.fullDay;
        final backgroundColor = isFullDay
                ? colorScheme.primaryContainer
                : colorScheme.tertiaryContainer;
        final foregroundColor = isFullDay
                ? colorScheme.onPrimaryContainer
                : colorScheme.onTertiaryContainer;
        final reduceMotion =
                MediaQuery.maybeOf(context)?.disableAnimations ?? false;
        final duration = reduceMotion
                ? Duration.zero
                : const Duration(milliseconds: 140);
        final textScale = MediaQuery.textScalerOf(context).scale(1);
        final width = math.max(168.0, 168 + (textScale - 1) * 104);
        final borderColor = focused ? colorScheme.outline : Colors.transparent;

        return Semantics(
            container: true,
            button: true,
            enabled: widget.enabled,
            label: 'Attendance period',
            value: widget.period.label,
            hint: 'Activate to change to ${widget.period.next.label}',
            onTap: widget.enabled ? widget.onChanged : null,
            excludeSemantics: true,
            child: Tooltip(
                message: 'Change attendance period',
                child: FocusableActionDetector(
                    enabled: widget.enabled,
                    onShowFocusHighlight: (value) => setState(() => focused = value),
                    shortcuts: const {
                        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
                        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
                    },
                    actions: {
                        ActivateIntent: CallbackAction<ActivateIntent>(
                            onInvoke: (_) {
                                if (widget.enabled) widget.onChanged();
                                return null;
                            },
                        ),
                    },
                    child: AnimatedContainer(
                        key: const Key('attendance-period-visual'),
                        width: width,
                        constraints: const BoxConstraints(minHeight: 48),
                        duration: duration,
                        curve: Curves.easeOut,
                        decoration: BoxDecoration(
                            color: backgroundColor,
                            borderRadius: BorderRadius.circular(24),
                            border: Border.all(color: borderColor, width: 3),
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: Opacity(
                            opacity: widget.enabled ? 1 : 0.38,
                            child: Material(
                                color: Colors.transparent,
                                child: InkWell(
                                    excludeFromSemantics: true,
                                    canRequestFocus: false,
                                    onTap: widget.enabled ? widget.onChanged : null,
                                    hoverColor: foregroundColor.withValues(alpha: 0.08),
                                    highlightColor: foregroundColor.withValues(alpha: 0.12),
                                    splashColor: foregroundColor.withValues(alpha: 0.12),
                                    child: Padding(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 16,
                                            vertical: 10,
                                        ),
                                        child: Row(
                                            mainAxisAlignment: MainAxisAlignment.center,
                                            children: [
                                                AnimatedSwitcher(
                                                    duration: duration,
                                                    transitionBuilder: (child, animation) =>
                                                            FadeTransition(opacity: animation, child: child),
                                                    child: Icon(
                                                        _icon(widget.period),
                                                        key: ValueKey(widget.period),
                                                        size: 20,
                                                        color: foregroundColor,
                                                    ),
                                                ),
                                                const SizedBox(width: 8),
                                                Expanded(
                                                    child: Text(
                                                        widget.period.label,
                                                        maxLines: 1,
                                                        textAlign: TextAlign.center,
                                                        style: Theme.of(context).textTheme.labelLarge
                                                                ?.copyWith(color: foregroundColor),
                                                    ),
                                                ),
                                            ],
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        );
    }

    static IconData _icon(AttendancePeriod period) => switch (period) {
        AttendancePeriod.fullDay => Icons.calendar_today_outlined,
        AttendancePeriod.morningOnly => Icons.wb_twilight_outlined,
        AttendancePeriod.afternoonOnly => Icons.light_mode_outlined,
    };
}