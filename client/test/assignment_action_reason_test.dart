import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/features/assignments/assignment_action_reason.dart';

void main() {
    test('assignment action reason codes have user-facing messages', () {
        expect(
            assignmentActionReasonText('ALREADY_OCCUPIES_DATE'),
            'You already have a seat for this day.',
        );
        expect(
            assignmentActionReasonText('CAPACITY_STATE_UNAVAILABLE'),
            'Booking information is currently unavailable.',
        );
        expect(
            assignmentActionReasonText('CAPACITY_UNAVAILABLE'),
            'No compatible capacity is currently available.',
        );
        expect(
            assignmentActionReasonText('ORIGINAL_UNSUCCESSFUL_BID'),
            'Open booking is unavailable because your original bid was unsuccessful.',
        );
        expect(
            assignmentActionReasonText('ORIGINAL_PERIOD_ONLY'),
            'You can only rebook the same period as your relinquished assignment.',
        );
        expect(
            assignmentActionReasonText('TARGET_DATE_ENDED'),
            'This day has ended.',
        );
    });

    test('missing or unknown reason uses a safe fallback', () {
        expect(
            assignmentActionReasonText(null),
            'This action is currently unavailable.',
        );
        expect(
            assignmentActionReasonText('NEW_BACKEND_REASON'),
            'This action is currently unavailable.',
        );
    });
}