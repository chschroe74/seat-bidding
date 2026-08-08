package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@ApplicationScoped
public class AllocationEngine {
    
    public static final String ALGORITHM_VERSION = "v1";

    private final RandomSelector randomSelector;
    
    @Inject
    public AllocationEngine(RandomSelector randomSelector) {
        this.randomSelector = randomSelector;
    }
    
    public List<Result> allocate(List<Bid> bids, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least one");
        }
        if (bids.stream().anyMatch(b -> b.tokens() <= 0)) {
            throw new IllegalArgumentException("all bids must be positive");
        }
        var byTokens = new LinkedHashMap<Integer, List<Bid>>();
        bids.stream().sorted(Comparator.comparingInt(Bid::tokens).reversed().thenComparingLong(Bid::bidId))
                .forEach(bid -> byTokens.computeIfAbsent(bid.tokens(), ignored -> new ArrayList<>()).add(bid));
        
        var results = new ArrayList<Result>(bids.size());
        int position = 0;
        for (var entry : byTokens.entrySet()) {
            var group = entry.getValue();
            int available = capacity - position;
            String tieGroup = group.size() > 1 ? "tokens:" + entry.getKey() : null;
            if (available > 0 && available < group.size()) {
                var draws = randomSelector.drawOrder(group);
                for (int i = 0; i < draws.size(); i++) {
                    var draw = draws.get(i);
                    results.add(new Result(draw.value().bidId(), draw.value().tokens(), i < available,
                            ++position, tieGroup, draw.auditValue(), ALGORITHM_VERSION));
                }
            }
            else {
                boolean assigned = available > 0;
                for (var bid : group) {
                    results.add(new Result(bid.bidId(), bid.tokens(), assigned,
                            ++position, tieGroup, null, ALGORITHM_VERSION));
                }
            }
        }
        return List.copyOf(results);
    }
    
    public record Bid(
            long bidId,
            int tokens) {
        
    }
    
    public record Result(
            long bidId,
            int tokens,
            boolean assigned,
            int finalRank,
            String tieGroup,
            String drawValue,
            String algorithmVersion) {
        
    }
    
}

