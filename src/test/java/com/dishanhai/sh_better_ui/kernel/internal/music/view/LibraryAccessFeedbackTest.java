// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryAccessFeedbackTest {
    @Test void guestAndRejectedAuthorizationHaveDifferentHints() {
        assertEquals("library.sign_in", LibraryAccessFeedback.signInHint(""));
        assertEquals("library.sign_in", LibraryAccessFeedback.signInHint("library.sign_in"));
        assertEquals("library.login_required", LibraryAccessFeedback.signInHint("library.login_required"));
    }

    @Test void rejectedAuthorizationSurvivesAsyncWrappers() {
        var denied = new OnlineMusicSource.LoginRequired();
        assertEquals("library.login_required", LibraryAccessFeedback.failure(denied));
        assertEquals("library.login_required", LibraryAccessFeedback.failure(
                new CompletionException(new ExecutionException(denied))));
    }

    @Test void networkFailureDoesNotClaimTheAccountNeedsAuthorization() {
        assertEquals("library.failed", LibraryAccessFeedback.failure(new CompletionException(new IOException("offline"))));
        assertEquals("library.failed", LibraryAccessFeedback.failure(new UnsupportedOperationException()));
    }
}
