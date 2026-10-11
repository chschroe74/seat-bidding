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
                'Complementary morning and afternoon bids are paired before seats are ranked. When one side is larger, the highest bids on that side are paired; high morning bids are matched with lower afternoon bids. A pair shares one physical seat and competes using its combined bid. Unmatched half-day bids still compete individually. Equal bids at the seat boundary are considered across the whole week and successful tie-breaks are distributed as evenly as possible. When a floor plan is active, successful employees are then placed globally to protect the worst preference outcomes first and use unit token order only to break equally fair outcomes. Random choice is used only when equally valid choices remain.',
            ),
            _Help(
                'Floor plans and seat preferences',
                'When the current round has a floor plan, its numbered active seats replace the configured seat capacity. Bids still compete for a seat on a date, never for a particular seat. You may rate any number of seats with one or two stars and one preferred seat with three stars. Zero stars means least preferred, not forbidden, so any active seat can still be assigned. Preferences belong to your profile, remain private, and can be changed at any time; a change recorded after cutoff applies only to later rounds. Pair members receive the same physical seat, and each person’s rating is considered in placement.',
            ),
            _Help(
                'Floor-plan privacy',
                'The floor plan shows seat numbers and your private ratings only. It never displays occupants, colleagues’ assigned seats, or colleagues’ preferences. Your own assigned seat number appears in the assignment list; use View floor plan there to locate it on the numbered map for that completed round.',
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
                'Bid reminders',
                'Bid reminders are an account preference in Settings. Choose Monday through Friday as the first reminder day, then explicitly enable notifications on each web device that should receive them. Saving at least one positive bid stops reminders; an all-zero bid set does not. Disabling reminders keeps your weekday and registered devices. You can also permanently skip reminders for the current round after confirmation; they resume for the next round when globally enabled. Delivery is best effort.',
            ),
            _Help(
                'Web Push on your devices',
                'On iPhone or iPad, add the web app to the Home Screen, open it there, and enable notifications from Settings. Compatible Android and desktop browsers can enroll directly; the native Android app is not required. Browser or system permission may still block delivery. You can register several web devices and remove them individually.',
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