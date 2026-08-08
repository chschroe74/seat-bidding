package de.gigaworks.seatbidding.round;

import de.gigaworks.seatbidding.allocation.RoundProcessingService;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import lombok.extern.slf4j.Slf4j;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Slf4j
public class RoundScheduler {
    
    @Inject
    RoundProcessingService processing;
    
    @Scheduled(identity = "seat-bidding-round-processing", cron = "{seat-bidding.scheduler.cron}",
            timeZone = "{seat-bidding.scheduler.time-zone}", skipExecutionIf = SchedulerDisabled.class)
    void run() {
        try {
            boolean processed = processing.processDueRound();
            log.info("operation=round-processing outcome={}", processed ? "completed" : "no-due-round");
        }
        catch (RuntimeException failure) {
            log.error("operation=round-processing outcome=failed", failure);
            throw failure;
        }
    }
    
    @ApplicationScoped
    public static class SchedulerDisabled implements Scheduled.SkipPredicate {
        
        @Inject
        SeatBiddingConfiguration configuration;
        
        @Override
        public boolean test(ScheduledExecution execution) {
            return !configuration.scheduler().enabled();
        }
        
    }
    
}
