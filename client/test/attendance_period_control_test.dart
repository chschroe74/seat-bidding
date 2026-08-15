import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/models.dart';
import 'package:seat_bidding/features/bidding/attendance_period_control.dart';

void main() {
    for (final brightness in Brightness.values) {
        testWidgets(
            '${brightness.name} theme maps every period to its icon and color category',
            (tester) async {
                final theme = ThemeData(
                    brightness: brightness,
                    colorSchemeSeed: Colors.teal,
                );
                AttendancePeriod period = AttendancePeriod.fullDay;

                Future<void> show() => tester.pumpWidget(
                    _Harness(
                        theme: theme,
                        child: StatefulBuilder(
                            builder: (context, setState) => AttendancePeriodControl(
                                period: period,
                                onChanged: () => setState(() => period = period.next),
                            ),
                        ),
                    ),
                );

                await show();
                _expectVisuals(
                    tester,
                    period: AttendancePeriod.fullDay,
                    icon: Icons.calendar_today_outlined,
                    background: theme.colorScheme.primaryContainer,
                    foreground: theme.colorScheme.onPrimaryContainer,
                );

                await tester.tap(find.byType(AttendancePeriodControl));
                await tester.pumpAndSettle();
                _expectVisuals(
                    tester,
                    period: AttendancePeriod.morningOnly,
                    icon: Icons.wb_twilight_outlined,
                    background: theme.colorScheme.tertiaryContainer,
                    foreground: theme.colorScheme.onTertiaryContainer,
                );

                await tester.tap(find.byType(AttendancePeriodControl));
                await tester.pumpAndSettle();
                _expectVisuals(
                    tester,
                    period: AttendancePeriod.afternoonOnly,
                    icon: Icons.light_mode_outlined,
                    background: theme.colorScheme.tertiaryContainer,
                    foreground: theme.colorScheme.onTertiaryContainer,
                );

                await tester.tap(find.byType(AttendancePeriodControl));
                await tester.pumpAndSettle();
                expect(period, AttendancePeriod.fullDay);
            },
        );
    }

    testWidgets('dimensions remain stable through the complete cycle', (
        tester,
    ) async {
        AttendancePeriod period = AttendancePeriod.fullDay;
        await tester.pumpWidget(
            _Harness(
                child: StatefulBuilder(
                    builder: (context, setState) => AttendancePeriodControl(
                        period: period,
                        onChanged: () => setState(() => period = period.next),
                    ),
                ),
            ),
        );

        final sizes = <Size>[];
        for (var index = 0; index < AttendancePeriod.values.length; index++) {
            sizes.add(tester.getSize(find.byType(AttendancePeriodControl)));
            await tester.tap(find.byType(AttendancePeriodControl));
            await tester.pumpAndSettle();
        }

        expect(sizes, everyElement(sizes.first));
        expect(sizes.first.width, greaterThanOrEqualTo(168));
        expect(sizes.first.height, greaterThanOrEqualTo(48));
    });

    testWidgets('tooltip and a single current-value/next-state semantic node', (
        tester,
    ) async {
        final semantics = tester.ensureSemantics();
        await tester.pumpWidget(
            const _Harness(
                child: AttendancePeriodControl(
                    period: AttendancePeriod.morningOnly,
                    onChanged: _noop,
                ),
            ),
        );

        expect(find.byTooltip('Change attendance period'), findsOneWidget);
        final node = tester.getSemantics(find.byType(AttendancePeriodControl));
        expect(node.label, 'Attendance period');
        expect(node.value, 'Morning');
        expect(node.hint, 'Activate to change to Afternoon');
        expect(node.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);
        expect(find.bySemanticsLabel('Attendance period'), findsOneWidget);
        semantics.dispose();
    });

    testWidgets('keyboard activation retains visible independent focus', (
        tester,
    ) async {
        AttendancePeriod period = AttendancePeriod.fullDay;
        final theme = ThemeData(colorSchemeSeed: Colors.indigo);
        await tester.pumpWidget(
            _Harness(
                theme: theme,
                child: StatefulBuilder(
                    builder: (context, setState) => AttendancePeriodControl(
                        period: period,
                        onChanged: () => setState(() => period = period.next),
                    ),
                ),
            ),
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        final decoration = _decoration(tester);
        expect(decoration.border, isA<Border>());
        expect((decoration.border! as Border).top.color, theme.colorScheme.outline);

        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pumpAndSettle();
        expect(period, AttendancePeriod.morningOnly);
        expect(
            (_decoration(tester).border! as Border).top.color,
            theme.colorScheme.outline,
        );
    });

    testWidgets('reduced motion disables the icon and color animation', (
        tester,
    ) async {
        await tester.pumpWidget(
            const _Harness(
                mediaQuery: MediaQueryData(disableAnimations: true),
                child: AttendancePeriodControl(
                    period: AttendancePeriod.afternoonOnly,
                    onChanged: _noop,
                ),
            ),
        );

        expect(
            tester
                    .widget<AnimatedContainer>(
                        find.byKey(const Key('attendance-period-visual')),
                    )
                    .duration,
            Duration.zero,
        );
        expect(
            tester.widget<AnimatedSwitcher>(find.byType(AnimatedSwitcher)).duration,
            Duration.zero,
        );
    });

    testWidgets('compact, wide, and enlarged-text layouts retain visible text', (
        tester,
    ) async {
        for (final size in [const Size(360, 640), const Size(1280, 800)]) {
            AttendancePeriod period = AttendancePeriod.fullDay;
            tester.view.physicalSize = size;
            tester.view.devicePixelRatio = 1;
            addTearDown(tester.view.resetPhysicalSize);
            addTearDown(tester.view.resetDevicePixelRatio);
            await tester.pumpWidget(
                _Harness(
                    mediaQuery: const MediaQueryData(textScaler: TextScaler.linear(2)),
                    child: StatefulBuilder(
                        builder: (context, setState) => Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                                AttendancePeriodControl(
                                    period: period,
                                    onChanged: () => setState(() => period = period.next),
                                ),
                                const SizedBox(key: Key('surrounding-control'), height: 24),
                            ],
                        ),
                    ),
                ),
            );
            final surroundingPosition = tester.getTopLeft(
                find.byKey(const Key('surrounding-control')),
            );
            await tester.tap(find.byType(AttendancePeriodControl));
            await tester.pumpAndSettle();
            expect(find.text('Morning'), findsOneWidget);
            expect(
                tester.getTopLeft(find.byKey(const Key('surrounding-control'))),
                surroundingPosition,
            );
            expect(tester.takeException(), isNull);
        }
    });
}

void _expectVisuals(
    WidgetTester tester, {
    required AttendancePeriod period,
    required IconData icon,
    required Color background,
    required Color foreground,
}) {
    expect(find.text(period.label), findsOneWidget);
    expect(find.byIcon(icon), findsOneWidget);
    expect(_decoration(tester).color, background);
    expect(tester.widget<Icon>(find.byIcon(icon)).color, foreground);
    expect(_contrastRatio(background, foreground), greaterThanOrEqualTo(4.5));
}

BoxDecoration _decoration(WidgetTester tester) =>
        tester
                        .widget<AnimatedContainer>(
                            find.byKey(const Key('attendance-period-visual')),
                        )
                        .decoration!
                as BoxDecoration;

double _contrastRatio(Color first, Color second) {
    final light = first.computeLuminance() > second.computeLuminance()
            ? first
            : second;
    final dark = identical(light, first) ? second : first;
    return (light.computeLuminance() + 0.05) / (dark.computeLuminance() + 0.05);
}

void _noop() {}

class _Harness extends StatelessWidget {
    const _Harness({
        required this.child,
        this.theme,
        this.mediaQuery = const MediaQueryData(),
    });

    final Widget child;
    final ThemeData? theme;
    final MediaQueryData mediaQuery;

    @override
    Widget build(BuildContext context) => MaterialApp(
        theme: theme,
        home: MediaQuery(
            data: mediaQuery,
            child: Scaffold(body: Center(child: child)),
        ),
    );
}