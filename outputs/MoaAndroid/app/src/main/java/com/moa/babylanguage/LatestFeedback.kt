package com.moa.babylanguage

import androidx.annotation.MainThread
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Show feedback for the latest action rather than queue obsolete confirmations. */
internal class LatestFeedback(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    private var pending: Job? = null

    @MainThread fun show(message: String) {
        // Cancellation removes the old SnackbarHostState request, including one
        // waiting for its mutex. Rapid taps therefore keep at most one request.
        pending?.cancel()
        pending = scope.launch { host.showSnackbar(message) }
    }
}
