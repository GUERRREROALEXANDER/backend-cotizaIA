package com.cotizaia.ingest.gmail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GmailIngestSchedulerTests {

    private final GmailIngestService ingest = mock(GmailIngestService.class);

    @Test
    void withPushTheWatchIsRenewedBeforeTheFirstSync() {
        new GmailIngestScheduler(ingest, true).onStart();

        InOrder order = inOrder(ingest);
        order.verify(ingest).renewWatch();
        order.verify(ingest).requestSync();
    }

    @Test
    void withoutPushOnlyTheFirstSyncRuns() {
        GmailIngestScheduler scheduler = new GmailIngestScheduler(ingest, false);

        scheduler.onStart();
        scheduler.renewWatch();

        verify(ingest).requestSync();
        verify(ingest, never()).renewWatch();
    }

    @Test
    void pollingSyncsQuietly() {
        new GmailIngestScheduler(ingest, false).poll();

        verify(ingest).syncQuietly();
    }

    @Test
    void aFailedRenewalDoesNotStopTheApplication() {
        doThrow(new IllegalStateException("Gmail down")).when(ingest).renewWatch();
        GmailIngestScheduler scheduler = new GmailIngestScheduler(ingest, true);

        assertThatCode(scheduler::onStart).doesNotThrowAnyException();
        verify(ingest).requestSync();
    }
}
