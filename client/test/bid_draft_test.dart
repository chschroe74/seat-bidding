import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/features/bidding/bid_draft.dart';
import 'package:seat_bidding/core/models.dart';

void main() {
    final monday = DateTime(2026, 8, 10);
    final tuesday = DateTime(2026, 8, 11);
    final wednesday = DateTime(2026, 8, 12);

    test('auto-distribution uses integer division and leaves remainder', () {
        final draft = BidDraft(
            startingBalance: 10,
            values: {monday: 1, tuesday: 0, wednesday: 0},
        );
        expect(draft.autoDistribute({tuesday, wednesday}), isTrue);
        expect(draft.values[tuesday], 4);
        expect(draft.values[wednesday], 4);
        expect(draft.remaining, 1);
    });

    test('positive days are ineligible and too-small shares do nothing', () {
        final draft = BidDraft(
            startingBalance: 2,
            values: {monday: 1, tuesday: 1, wednesday: 0},
        );
        expect(draft.autoDistribute({monday}), isFalse);
        expect(draft.values[monday], 1);
        expect(draft.autoDistribute({wednesday}), isFalse);
        expect(draft.values[wednesday], 0);
    });

    test('overspent draft disables saving', () {
        final draft = BidDraft(startingBalance: 5, values: {monday: 6});
        expect(draft.isValid, isFalse);
        expect(draft.remaining, -1);
    });

    test('clear resets every bid without persistence', () {
        final draft = BidDraft(
            startingBalance: 10,
            values: {monday: 6, tuesday: 4, wednesday: 0},
        );

        draft.clear();

        expect(draft.values, {monday: 0, tuesday: 0, wednesday: 0});
        expect(draft.remaining, 10);
        expect(
            draft.attendancePeriods.values,
            everyElement(AttendancePeriod.fullDay),
        );
    });

    test('auto-distribution preserves independent attendance periods', () {
        final draft = BidDraft(
            startingBalance: 10,
            values: {monday: 0, tuesday: 0},
            attendancePeriods: {
                monday: AttendancePeriod.morningOnly,
                tuesday: AttendancePeriod.afternoonOnly,
            },
        );

        expect(draft.autoDistribute({monday, tuesday}), isTrue);
        expect(draft.attendancePeriods[monday], AttendancePeriod.morningOnly);
        expect(draft.attendancePeriods[tuesday], AttendancePeriod.afternoonOnly);
    });
}