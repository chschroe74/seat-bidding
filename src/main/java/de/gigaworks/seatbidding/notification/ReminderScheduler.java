package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.round.SeatBiddingConfiguration;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import lombok.extern.slf4j.Slf4j;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;

@ApplicationScoped
@Slf4j
public class ReminderScheduler {

    @Inject
    ReminderDispatchService dispatches;

    @Inject
    Clock clock;

    @Scheduled(identity = "seat-bidding-bid-reminders", cron = "${seat-bidding.reminders.schedule.cron}",
            timeZone = "${seat-bidding.time-zone}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            skipExecutionIf = SchedulerDisabled.class)
    void run() {
        try {
            var summary = dispatches.dispatch(clock.instant());
            log.info("operation=bid-reminder-scheduler outcome=completed dispatches={} attempts={} accepted={} failed={}",
                    summary.dispatches(), summary.attempts(), summary.accepted(), summary.failed());
        }
        catch (RuntimeException failure) {
            log.error("operation=bid-reminder-scheduler outcome=failed", failure);
            throw failure;
        }
    }

    @ApplicationScoped
    public static class SchedulerDisabled implements Scheduled.SkipPredicate {

        @Inject
        SeatBiddingConfiguration configuration;

        @Override
        public boolean test(ScheduledExecution execution) {
            return !configuration.reminders().schedule().enabled();
        }

    }

}