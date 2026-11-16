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
                'Each weekly round grants new tokens plus capped carry-over for regular bids. Only winning bids are charged. Unsuccessful bids remain unspent. Your full remainder from the previous round is available for late bookings, even above the carry-over cap; only the capped portion can be used for upcoming-week bids.',
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
                'When the current round has a floor plan, its numbered active seats replace the configured seat capacity. An administrator may activate a plan immediately before cutoff when its seat count matches a configuration-only round; this adds physical placement without changing existing bids, reservations, winner selection, or token accounting. Other plan changes start with a later round. Bids still compete for a seat on a date, never for a particular seat. You may rate any number of seats with one or two stars and one preferred seat with three stars. Zero stars means least preferred, not forbidden, so any active seat can still be assigned. Preferences belong to your profile, remain private, and can be changed at any time; a change recorded after cutoff applies only to later rounds. Pair members receive the same physical seat, and each person’s rating is considered in placement.',
            ),
            _Help(
                'Floor-plan privacy',
                'The floor plan shows seat numbers and your private ratings only. It never displays occupant overlays or colleagues’ preferences. The authenticated assignment list shows each employee’s assigned seat number; use View floor plan there to locate numbered seats on the map for that completed round.',
            ),
            _Help(
                'Assignment cards',
                'The original published result never changes. Each card separately shows current occupancy, your current status, available actions, and your permanent offer history. Green with a check means currently assigned, claimed, or booked; orange means relinquished; red with a cross means not assigned; and neutral means no bid. Today has a visible border and label. Morning and Afternoon badges identify half-day attendance; paired employees are grouped to show that they share one physical seat. The employee count can therefore be higher than the occupied-seat count. Reserved seats reduce the number available for assignment. The participant divider marks the original assignable-seat boundary.',
            ),
            _Help(
                'Relinquishing an assignment',
                'You may irreversibly relinquish an active occupancy until that target date ends. The original assignment, placement, rank, and bid charge stay unchanged. Relinquishing an original successful assignment creates same-round credits equal to your individual winning bid; credits expire at the end of the target week and are spent before predecessor tokens. Relinquishing a later claim or booking restores its original credit and predecessor-token funding without creating a new credit. If its funding round has already closed, it can no longer be relinquished.',
            ),
            _Help(
                'Waiting-list offers',
                'Released capacity is offered to compatible unsuccessful original bidders in descending original individual-bid tiers. Affordable employees in the same tier may claim first-come-first-served; merely receiving or opening an offer never reserves capacity. Each release keeps one deadline, at most 24 hours after relinquishment and never after the target date ends. Claiming costs exactly your original individual bid. Declining is permanent for that offer. Insufficient tokens, another eligible claim, or expiry creates a disabled historical outcome and does not change the original result.',
            ),
            _Help(
                'Open booking',
                'Capacity becomes openly bookable when it was originally surplus or no compatible waiting-list candidate remains. Ordinary booking is only for an employee who placed no bid for that date and does not already occupy it. A relinquished original winner may instead rebook only the same attendance period for their original individual bid. An unsuccessful bidder cannot bypass the waiting list. Prices come from the immutable original result and all bookings use credits first, then the full uncommitted remainder from the previous round. The carry-over cap does not limit late bookings, but spending enough of that remainder can reduce the capped tokens still available for upcoming-week bids. The new grant is never available for late bookings, so some seats may be unaffordable.',
            ),
            _Help(
                'Choosing a late numbered seat',
                'In floor-plan mode every claim or booking requires an explicit compatible numbered seat. The exact completed-round map and its accessible numbered list mark seats only as available or unavailable for that action; they do not reveal why a seat is unavailable or who occupies it. Choosing a marker reserves nothing, and the backend rechecks the stable seat ID when you confirm. Configuration-capacity rounds have no synthetic seat selector.',
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
                'Seat-offer notifications',
                'Seat availability offers are a separate account preference and reuse the same registered web devices. A notification is created only when you first reach an affordable waiting-list tier. Delivery is best effort, does not reserve capacity, never extends the deadline, and cannot claim or decline from the notification itself. Opening View offer refreshes the assignment card so every decision uses current state.',
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