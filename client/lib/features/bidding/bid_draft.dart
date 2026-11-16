import '../../core/models.dart';

class BidDraft {
    BidDraft({
        required this.startingBalance,
        this.postAllocationSpend = 0,
        this.lateBookingSpend = 0,
        required Map<DateTime, int> values,
        Map<DateTime, AttendancePeriod>? attendancePeriods,
    }) : values = Map.of(values),
              attendancePeriods = {
                  for (final date in values.keys)
                      date: attendancePeriods?[date] ?? AttendancePeriod.fullDay,
              };
    final int startingBalance;
    final int postAllocationSpend;
    final int lateBookingSpend;
    final Map<DateTime, int> values;
    final Map<DateTime, AttendancePeriod> attendancePeriods;
    int get total => values.values.fold(0, (sum, value) => sum + value);
    int get remaining => startingBalance - postAllocationSpend - total;
    bool get isValid =>
            values.values.every((value) => value >= 0) && remaining >= 0;

    void clear() {
        for (final day in values.keys) {
            values[day] = 0;
            attendancePeriods[day] = AttendancePeriod.fullDay;
        }
    }

    bool autoDistribute(Set<DateTime> selected) {
        final eligible = selected.where((day) => (values[day] ?? 0) == 0).toList();
        if (eligible.isEmpty || remaining <= 0) return false;
        final share = remaining ~/ eligible.length;
        if (share == 0) return false;
        for (final day in eligible) {
            values[day] = share;
        }
        return true;
    }
}