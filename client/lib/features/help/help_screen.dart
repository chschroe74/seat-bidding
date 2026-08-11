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
        'Enter whole tokens for any weekday. Save replaces your complete bid set. Zero removes a bid. Auto-distribute divides remaining tokens across selected zero-value days and leaves any remainder unallocated. Reserved seats reduce the seats available for assignment, but do not change your tokens, bid limits, or bid cost.',
      ),
      _Help(
        'Cutoff and privacy',
        'You can change bids until the exact displayed cutoff. Before cutoff, nobody else can see your bids.',
      ),
      _Help(
        'How seats are assigned',
        'Bids rank from highest to lowest for each day. When equal bids compete for the remaining seats, tied situations across the whole week are considered together and successful tie-breaks are distributed as evenly as possible. If several equally fair allocations remain, the final choice is random. Published participant order and results are permanent.',
      ),
      _Help(
        'Assignment cards',
        'Green with a check means assigned, red with a cross means not assigned, and neutral means no bid. Today has a visible border and label. Reserved seats reduce the number available for assignment. A reservation description explains why seats were reserved and is visible to every signed-in employee. The participant divider marks the assignable-seat boundary.',
      ),
      _Help(
        'Attendance',
        'Surplus seats and attendance without an assignment are outside this application.',
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
