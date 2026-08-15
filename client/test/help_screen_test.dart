import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/features/help/help_screen.dart';

void main() {
    testWidgets(
        'help explains privacy, ties, carry-over, and Web Push reminders',
        (tester) async {
            await tester.pumpWidget(
                const MaterialApp(home: Scaffold(body: HelpScreen())),
            );
            expect(find.text('Cutoff and privacy'), findsOneWidget);
            expect(find.text('How seats are assigned'), findsOneWidget);
            expect(find.textContaining('across the whole week'), findsOneWidget);
            expect(
                find.textContaining('distributed as evenly as possible'),
                findsOneWidget,
            );
            expect(find.text('Tokens and balances'), findsOneWidget);
            expect(find.textContaining('do not change your tokens'), findsOneWidget);
            await tester.scrollUntilVisible(find.text('Assignment cards'), 200);
            expect(find.textContaining('Reserved seats reduce'), findsAtLeast(1));
            await tester.scrollUntilVisible(find.text('Bid reminders'), 200);
            expect(find.text('Bid reminders'), findsOneWidget);
            expect(
                find.textContaining('add the web app to the Home Screen'),
                findsOneWidget,
            );
            expect(
                find.textContaining('native Android app is not required'),
                findsOneWidget,
            );
        },
    );
}