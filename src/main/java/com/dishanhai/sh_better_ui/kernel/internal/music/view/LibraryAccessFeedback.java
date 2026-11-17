// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** A guest has not signed in; a rejected request needs authorization, not necessarily an expired session. */
final class LibraryAccessFeedback {
    static final String SIGN_IN = "library.sign_in";
    static final String AUTHORIZATION_REQUIRED = "library.login_required";

    private LibraryAccessFeedback() {}

    static String failure(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) failure = failure.getCause();
        return failure instanceof OnlineMusicSource.LoginRequired ? AUTHORIZATION_REQUIRED : "library.failed";
    }

    static String signInHint(String state) {
        return AUTHORIZATION_REQUIRED.equals(state) ? AUTHORIZATION_REQUIRED : SIGN_IN;
    }
}
