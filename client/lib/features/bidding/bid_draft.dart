class BidDraft {
  BidDraft({required this.startingBalance, required Map<DateTime, int> values})
    : values = Map.of(values);
  final int startingBalance;
  final Map<DateTime, int> values;
  int get total => values.values.fold(0, (sum, value) => sum + value);
  int get remaining => startingBalance - total;
  bool get isValid =>
      values.values.every((value) => value >= 0) && remaining >= 0;

  void clear() {
    for (final day in values.keys) {
      values[day] = 0;
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
