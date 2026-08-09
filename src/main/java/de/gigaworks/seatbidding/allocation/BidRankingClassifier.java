package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class BidRankingClassifier {

    private static final Comparator<RoundAllocation.TargetDate> DATE_ORDER = Comparator
            .comparing(RoundAllocation.TargetDate::targetDate)
            .thenComparingLong(RoundAllocation.TargetDate::dateId);
    private static final Comparator<RoundAllocation.Bid> BID_ORDER = Comparator
            .comparingInt(RoundAllocation.Bid::tokens).reversed()
            .thenComparingLong(RoundAllocation.Bid::bidId);

    public RoundAllocation.Problem classify(long roundId, int capacity,
            List<RoundAllocation.TargetDate> targetDates, List<RoundAllocation.Bid> bids) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least one");
        }
        validate(targetDates, bids);
        Map<Long, List<RoundAllocation.Bid>> bidsByDate = bids.stream()
                .collect(Collectors.groupingBy(RoundAllocation.Bid::dateId));
        var dates = targetDates.stream().sorted(DATE_ORDER)
                .map(date -> classifyDate(date, capacity, bidsByDate.getOrDefault(date.dateId(), List.of())))
                .toList();
        return new RoundAllocation.Problem(roundId, capacity, dates);
    }

    private RoundAllocation.ClassifiedDate classifyDate(
            RoundAllocation.TargetDate date, int capacity, List<RoundAllocation.Bid> bids) {
        var byTokens = new LinkedHashMap<Integer, List<RoundAllocation.Bid>>();
        bids.stream().sorted(BID_ORDER)
                .forEach(bid -> byTokens.computeIfAbsent(bid.tokens(), _ -> new ArrayList<>()).add(bid));
        var classified = new ArrayList<RoundAllocation.ClassifiedBid>(bids.size());
        int biddersAbove = 0;
        int tokenRank = 0;
        int unresolvedSeats = 0;
        for (var entry : byTokens.entrySet()) {
            tokenRank++;
            var group = entry.getValue();
            int groupEnd = biddersAbove + group.size();
            RoundAllocation.Classification classification;
            String boundaryGroup = null;
            if (groupEnd <= capacity) {
                classification = RoundAllocation.Classification.FIXED_WINNER;
            }
            else if (biddersAbove >= capacity) {
                classification = RoundAllocation.Classification.FIXED_LOSER;
            }
            else {
                classification = RoundAllocation.Classification.BOUNDARY;
                unresolvedSeats = capacity - biddersAbove;
                boundaryGroup = "date:" + date.dateId() + ":tokens:" + entry.getKey();
            }
            for (var bid : group) {
                classified.add(new RoundAllocation.ClassifiedBid(date.dateId(), date.targetDate(), bid.bidId(),
                        bid.employeeId(), bid.tokens(), tokenRank, classification, boundaryGroup));
            }
            biddersAbove = groupEnd;
        }
        return new RoundAllocation.ClassifiedDate(
                date.dateId(), date.targetDate(), capacity, unresolvedSeats, classified);
    }

    private static void validate(List<RoundAllocation.TargetDate> targetDates, List<RoundAllocation.Bid> bids) {
        Map<Long, java.time.LocalDate> datesById = new LinkedHashMap<>();
        for (var date : targetDates) {
            if (date.targetDate() == null || datesById.putIfAbsent(date.dateId(), date.targetDate()) != null) {
                throw new IllegalArgumentException("target dates must have unique identifiers and non-null dates");
            }
        }
        Set<Long> bidIds = new HashSet<>();
        Set<String> employeeDates = new HashSet<>();
        for (var bid : bids) {
            if (bid.tokens() <= 0) {
                throw new IllegalArgumentException("all bids must be positive");
            }
            if (bid.targetDate() == null || !bid.targetDate().equals(datesById.get(bid.dateId()))) {
                throw new IllegalArgumentException("every bid must reference a canonical target date");
            }
            if (!bidIds.add(bid.bidId()) || !employeeDates.add(bid.dateId() + ":" + bid.employeeId())) {
                throw new IllegalArgumentException("bid identifiers and employee/date relationships must be unique");
            }
        }
    }

}
