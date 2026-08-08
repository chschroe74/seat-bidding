package de.gigaworks.seatbidding.bidding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BidSetValidatorTest {
    private final LocalDate monday = LocalDate.parse("2026-08-10");
    private final Set<LocalDate> dates = Set.of(monday, monday.plusDays(1), monday.plusDays(2), monday.plusDays(3), monday.plusDays(4));

    @Test void zeroIsOmittedFromNormalizedSet() {
        var result = BidSetValidator.validateAndNormalize(List.of(
                new BidSetValidator.SubmittedBid(monday, 0), new BidSetValidator.SubmittedBid(monday.plusDays(1), 8)), dates, 50);
        assertFalse(result.containsKey(monday));
        assertEquals(8, result.get(monday.plusDays(1)));
    }
    @Test void rejectsDuplicateAndOverspentSets() {
        assertEquals("DUPLICATE_BID_DATE", assertThrows(BidValidationException.class,
                () -> BidSetValidator.validateAndNormalize(List.of(new BidSetValidator.SubmittedBid(monday, 1),
                        new BidSetValidator.SubmittedBid(monday, 2)), dates, 50)).code());
        assertEquals("BID_BUDGET_EXCEEDED", assertThrows(BidValidationException.class,
                () -> BidSetValidator.validateAndNormalize(List.of(new BidSetValidator.SubmittedBid(monday, 51)), dates, 50)).code());
    }
}
