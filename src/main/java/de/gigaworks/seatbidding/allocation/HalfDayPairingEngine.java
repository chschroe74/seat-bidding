package de.gigaworks.seatbidding.allocation;

import de.gigaworks.seatbidding.persistence.AllocationUnitType;
import de.gigaworks.seatbidding.persistence.AttendancePeriod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class HalfDayPairingEngine {

    private static final Comparator<RoundAllocation.Bid> STABLE_BID = Comparator
            .comparingLong(RoundAllocation.Bid::employeeId).thenComparingLong(RoundAllocation.Bid::bidId);
    private final PairingRandomSelector randomSelector;

    @Inject
    public HalfDayPairingEngine(PairingRandomSelector randomSelector) {
        this.randomSelector = randomSelector;
    }

    public RoundAllocation.Pairing pair(List<RoundAllocation.TargetDate> dates, List<RoundAllocation.Bid> bids) {
        validate(dates, bids);
        Map<Long, List<RoundAllocation.Bid>> byDate = bids.stream()
                .collect(Collectors.groupingBy(RoundAllocation.Bid::dateId));
        var units = new ArrayList<RoundAllocation.Unit>();
        var audit = new StringBuilder("{\"encoding\":\"pairing-v1\",\"dates\":[");
        boolean firstDate = true;
        for (var date : dates.stream().sorted(Comparator.comparing(RoundAllocation.TargetDate::targetDate)
                .thenComparingLong(RoundAllocation.TargetDate::dateId)).toList()) {
            var dateBids = byDate.getOrDefault(date.dateId(), List.of()).stream().sorted(STABLE_BID).toList();
            var morning = filter(dateBids, AttendancePeriod.MORNING_ONLY);
            var afternoon = filter(dateBids, AttendancePeriod.AFTERNOON_ONLY);
            int count = Math.min(morning.size(), afternoon.size());
            var choices = pairingChoices(date, morning, afternoon, count);
            PairingChoice selected = choices.getFirst();
            String draw = null;
            if (choices.size() > 1) {
                var selection = randomSelector.select(choices);
                selected = selection.value();
                draw = selection.auditValue();
            }
            units.addAll(selected.units());
            var pairedBidIds = selected.units().stream().flatMap(unit -> unit.members().stream())
                    .map(RoundAllocation.Member::bidId).collect(Collectors.toSet());
            dateBids.stream().filter(bid -> !pairedBidIds.contains(bid.bidId()))
                    .map(HalfDayPairingEngine::single).forEach(units::add);
            if (!firstDate) {
                audit.append(',');
            }
            firstDate = false;
            audit.append("{\"dateId\":").append(date.dateId()).append(",\"pairCount\":").append(count)
                    .append(",\"morningCandidates\":[").append(bidAudit(morning)).append(']')
                    .append(",\"afternoonCandidates\":[").append(bidAudit(afternoon)).append(']')
                    .append(",\"alternativeCount\":").append(choices.size()).append(",\"selectionValue\":")
                    .append(draw == null ? "null" : "\"" + draw + "\"")
                    .append(",\"pairs\":[")
                    .append(selected.units().stream().map(HalfDayPairingEngine::pairAudit)
                            .collect(Collectors.joining(",")))
                    .append("]}");
        }
        audit.append("]}");
        return new RoundAllocation.Pairing(units.stream().sorted(unitOrder()).toList(), audit.toString());
    }

    private static void validate(List<RoundAllocation.TargetDate> dates, List<RoundAllocation.Bid> bids) {
        var dateValues = new LinkedHashMap<Long, java.time.LocalDate>();
        for (var date : dates) {
            if (date.targetDate() == null || dateValues.putIfAbsent(date.dateId(), date.targetDate()) != null) {
                throw new IllegalArgumentException("target dates must have unique identifiers and non-null dates");
            }
        }
        var bidIds = new HashSet<Long>();
        var employeeDates = new HashSet<String>();
        for (var bid : bids) {
            if (bid.tokens() <= 0 || bid.attendancePeriod() == null) {
                throw new IllegalArgumentException("all bids must be positive and have an attendance period");
            }
            if (bid.targetDate() == null || !bid.targetDate().equals(dateValues.get(bid.dateId()))) {
                throw new IllegalArgumentException("every bid must reference a canonical target date");
            }
            if (!bidIds.add(bid.bidId()) || !employeeDates.add(bid.dateId() + ":" + bid.employeeId())) {
                throw new IllegalArgumentException("bid identifiers and employee/date relationships must be unique");
            }
        }
    }

    private static List<RoundAllocation.Bid> filter(List<RoundAllocation.Bid> bids, AttendancePeriod period) {
        return bids.stream().filter(bid -> bid.attendancePeriod() == period).toList();
    }

    private static String bidAudit(List<RoundAllocation.Bid> bids) {
        return bids.stream().sorted(STABLE_BID).map(bid -> "{\"bidId\":" + bid.bidId()
                + ",\"employeeId\":" + bid.employeeId() + ",\"tokens\":" + bid.tokens() + '}')
                .collect(Collectors.joining(","));
    }

    private static String pairAudit(RoundAllocation.Unit unit) {
        var morning = unit.members().stream().filter(member -> member.memberOrder() == 1).findFirst().orElseThrow();
        var afternoon = unit.members().stream().filter(member -> member.memberOrder() == 2).findFirst().orElseThrow();
        return "{\"identity\":\"" + unit.fairnessIdentity() + "\",\"morningBidId\":" + morning.bidId()
                + ",\"afternoonBidId\":" + afternoon.bidId() + ",\"scoreTokens\":" + unit.scoreTokens() + '}';
    }

    private static List<PairingChoice> pairingChoices(RoundAllocation.TargetDate date,
            List<RoundAllocation.Bid> morning, List<RoundAllocation.Bid> afternoon, int count) {
        var morningSelections = selectHighestAlternatives(morning, count);
        var afternoonSelections = selectHighestAlternatives(afternoon, count);
        var choices = new LinkedHashMap<String, PairingChoice>();
        for (var selectedMorning : morningSelections) {
            for (var selectedAfternoon : afternoonSelections) {
                var morningOrder = order(selectedMorning, true);
                var afternoonOrder = order(selectedAfternoon, false);
                var paired = new ArrayList<RoundAllocation.Unit>();
                for (int index = 0; index < count; index++) {
                    paired.add(pair(date, morningOrder.get(index), afternoonOrder.get(index)));
                }
                paired.sort(Comparator.comparing(RoundAllocation.Unit::fairnessIdentity));
                var choice = new PairingChoice(List.copyOf(paired));
                choices.putIfAbsent(choice.canonicalKey(), choice);
            }
        }
        return choices.values().stream().sorted(Comparator.comparing(PairingChoice::canonicalKey)).toList();
    }

    private static List<List<RoundAllocation.Bid>> selectHighestAlternatives(List<RoundAllocation.Bid> bids, int count) {
        if (count == 0) {
            return List.of(List.of());
        }
        var sorted = bids.stream().sorted(Comparator.comparingInt(RoundAllocation.Bid::tokens).reversed()
                .thenComparing(STABLE_BID)).toList();
        if (sorted.size() <= count) {
            return List.of(sorted);
        }
        int boundary = sorted.get(count - 1).tokens();
        var fixed = sorted.stream().filter(bid -> bid.tokens() > boundary).toList();
        var tied = sorted.stream().filter(bid -> bid.tokens() == boundary).sorted(STABLE_BID).toList();
        var combinations = new ArrayList<List<RoundAllocation.Bid>>();
        choose(tied, count - fixed.size(), 0, new ArrayList<>(), combinations);
        return combinations.stream().map(choice -> {
            var result = new ArrayList<>(fixed);
            result.addAll(choice);
            return List.copyOf(result);
        }).toList();
    }

    private static void choose(List<RoundAllocation.Bid> values, int required, int start,
            List<RoundAllocation.Bid> selected, List<List<RoundAllocation.Bid>> results) {
        if (selected.size() == required) {
            results.add(List.copyOf(selected));
            return;
        }
        for (int index = start; index <= values.size() - (required - selected.size()); index++) {
            selected.add(values.get(index));
            choose(values, required, index + 1, selected, results);
            selected.removeLast();
        }
    }

    private static List<RoundAllocation.Bid> order(List<RoundAllocation.Bid> bids, boolean descending) {
        Comparator<RoundAllocation.Bid> comparator = Comparator.comparingInt(RoundAllocation.Bid::tokens);
        if (descending) {
            comparator = comparator.reversed();
        }
        return bids.stream().sorted(comparator.thenComparing(STABLE_BID)).toList();
    }

    private static RoundAllocation.Unit single(RoundAllocation.Bid bid) {
        return new RoundAllocation.Unit(bid.dateId(), bid.targetDate(), AllocationUnitType.SINGLE,
                "EMPLOYEE:" + bid.employeeId(), bid.tokens(), List.of(new RoundAllocation.Member(
                        bid.bidId(), bid.employeeId(), bid.tokens(), bid.attendancePeriod(), (short) 1)));
    }

    private static RoundAllocation.Unit pair(RoundAllocation.TargetDate date,
            RoundAllocation.Bid morning, RoundAllocation.Bid afternoon) {
        long low = Math.min(morning.employeeId(), afternoon.employeeId());
        long high = Math.max(morning.employeeId(), afternoon.employeeId());
        return new RoundAllocation.Unit(date.dateId(), date.targetDate(), AllocationUnitType.HALF_DAY_PAIR,
                "PAIR:" + low + ':' + high, morning.tokens() + afternoon.tokens(), List.of(
                        new RoundAllocation.Member(morning.bidId(), morning.employeeId(), morning.tokens(),
                                AttendancePeriod.MORNING_ONLY, (short) 1),
                        new RoundAllocation.Member(afternoon.bidId(), afternoon.employeeId(), afternoon.tokens(),
                                AttendancePeriod.AFTERNOON_ONLY, (short) 2)));
    }

    private static Comparator<RoundAllocation.Unit> unitOrder() {
        return Comparator.comparing(RoundAllocation.Unit::targetDate).thenComparingLong(RoundAllocation.Unit::dateId)
                .thenComparing(RoundAllocation.Unit::fairnessIdentity);
    }

    private record PairingChoice(List<RoundAllocation.Unit> units) {
        String canonicalKey() {
            return units.stream().map(RoundAllocation.Unit::fairnessIdentity).sorted().collect(Collectors.joining("|"));
        }
    }

}