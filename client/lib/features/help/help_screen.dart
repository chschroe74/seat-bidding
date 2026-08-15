import 'package:flutter/material.dart';

class HelpScreen extends StatelessWidget {
    const HelpScreen({super.key});
    @override
    Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(16),
        children: const [
            Text('Help', style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
            _Help(
                'Tokens and balances',
                'Each weekly round grants tokens plus capped carry-over. Only winning bids are charged. Unsuccessful bids remain unspent, but every remaining token is still subject to the carry-over cap.',
            ),
            _Help(
                'Place and change bids',
                'Enter whole tokens for any weekday and choose Full day, Morning, or Afternoon independently. Tap the attendance badge to cycle through the choices. A half-day bid costs its full individual amount; there is no discount. Save replaces your complete bid set. Zero removes a bid and returns to Full day when reloaded. Auto-distribute keeps your attendance choices. Reserved seats reduce the seats available for assignment, but do not change your tokens, bid limits, or bid cost.',
            ),
            _Help(
                'Cutoff and privacy',
                'You can change bids until the exact displayed cutoff. Before cutoff, nobody else can see your bids.',
            ),
            _Help(
                'How seats are assigned',
                'Complementary morning and afternoon bids are paired before seats are ranked. When one side is larger, the highest bids on that side are paired; high morning bids are matched with lower afternoon bids. A pair shares one physical seat and competes using its combined bid. Unmatched half-day bids still compete individually. Equal bids at the seat boundary are considered across the whole week and successful tie-breaks are distributed as evenly as possible. Random choice is used only when equally valid pairing or equally fair allocation choices remain.',
            ),
            _Help(
                'Assignment cards',
                'Green with a check means assigned, red with a cross means not assigned, and neutral means no bid. Today has a visible border and label. Morning and Afternoon badges identify half-day attendance; paired employees are grouped to show that they share one physical seat. The employee count can therefore be higher than the occupied-seat count. Reserved seats reduce the number available for assignment. The participant divider marks the assignable-seat boundary.',
            ),
            _Help(
                'First sign-in',
                'Enter your provisioned email address. If you have no password yet, a six-digit activation code is emailed to you. Verify it, create a password that meets the displayed requirements, and you are signed in automatically.',
            ),
            _Help(
                'Sessions and logout',
                'Your encrypted sign-in cookie normally remains active across restarts and expires after inactivity. Log out from the menu to remove it from this device.',
            ),
            _Help(
                'Android reminders',
                'The optional native Android app can schedule device-local bidding reminders. The web app does not schedule reminders.',
            ),
        ],
    );
}

class _Help extends StatelessWidget {
    const _Help(this.title, this.body);
    final String title;
    final String body;
    @override
    Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 20),
        child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
                Text(
                    title,
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                Text(body),
            ],
        ),
    );
}