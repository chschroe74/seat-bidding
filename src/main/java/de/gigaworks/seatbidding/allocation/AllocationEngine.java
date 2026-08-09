package de.gigaworks.seatbidding.allocation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class AllocationEngine {

    private final BidRankingClassifier classifier;
    private final GlobalFairnessOptimizer optimizer;

    @Inject
    public AllocationEngine(BidRankingClassifier classifier, GlobalFairnessOptimizer optimizer) {
        this.classifier = classifier;
        this.optimizer = optimizer;
    }

    public RoundAllocation.Solution allocate(long roundId, int capacity,
            List<RoundAllocation.TargetDate> targetDates, List<RoundAllocation.Bid> bids) {
        return optimizer.solve(classifier.classify(roundId, capacity, targetDates, bids));
    }

}
