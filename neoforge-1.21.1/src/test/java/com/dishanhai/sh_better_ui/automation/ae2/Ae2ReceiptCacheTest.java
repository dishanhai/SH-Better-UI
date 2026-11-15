// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.ae2;

import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleAdapter.Submission;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleAdapter.SubmissionStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Ae2ReceiptCacheTest {
    @Test void missingIngredientsCanRetryTheSameIntentAndLaterBecomeAccepted() {
        var receipts = new Ae2ReceiptCache(2);
        var intent = UUID.randomUUID();
        receipts.record(intent, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        assertNull(receipts.get(intent));
        assertTrue(receipts.hasCapacity(0));
        var accepted = new Submission(SubmissionStatus.ACCEPTED, "real-ae2-link");
        receipts.record(intent, accepted);
        assertSame(accepted, receipts.get(intent));
    }

    @Test void acceptedAndUnknownReceiptsCannotBeClearedByStaleFailureOrCapacityPressure() {
        var receipts = new Ae2ReceiptCache(2);
        var acceptedId = UUID.randomUUID();
        var unknownId = UUID.randomUUID();
        var accepted = new Submission(SubmissionStatus.ACCEPTED, "real-ae2-link");
        var unknown = new Submission(SubmissionStatus.UNKNOWN, null);
        receipts.record(acceptedId, accepted);
        receipts.record(unknownId, unknown);
        receipts.record(acceptedId, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        receipts.record(unknownId, new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        assertSame(accepted, receipts.get(acceptedId));
        assertSame(unknown, receipts.get(unknownId));
        assertFalse(receipts.hasCapacity(0));
        assertThrows(IllegalStateException.class, () -> receipts.record(UUID.randomUUID(), unknown));
        assertSame(accepted, receipts.get(acceptedId));
        assertSame(unknown, receipts.get(unknownId));
    }

    @Test void pendingPlansReserveSlotsAndProvenRejectionsReleaseThem() {
        var receipts = new Ae2ReceiptCache(2);
        assertTrue(receipts.hasCapacity(1));
        assertFalse(receipts.hasCapacity(2));
        receipts.record(UUID.randomUUID(), new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        assertTrue(receipts.hasCapacity(1));
        receipts.record(UUID.randomUUID(), new Submission(SubmissionStatus.ACCEPTED, "link"));
        assertFalse(receipts.hasCapacity(1));
        assertTrue(receipts.hasCapacity(0));
    }
}
