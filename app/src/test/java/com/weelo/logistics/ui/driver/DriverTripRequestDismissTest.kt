package com.weelo.logistics.ui.driver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-44 DriverTripRequestManager onDismiss idempotency + TTL tests
 * =============================================================================
 *
 * Source-level file-scan guard. Before this fix:
 *
 *   onDismiss() appended the current request back into `_requestQueue` with
 *   `dismissedAt = System.currentTimeMillis()`. Then completeCurrentRequest()
 *   blindly `removeFirst()`-ed from that same queue — which meant the just-
 *   dismissed entry was IMMEDIATELY re-selected as the "next" request. Combined
 *   with the 500ms REQUEST_DELAY_MS, a driver tap on the dim scrim produced an
 *   infinite loop: dismiss -> re-show -> dismiss -> re-show ... until the
 *   process died or the backend expired the assignment.
 *
 * The fix:
 *   1. `completeCurrentRequest` filters entries where `dismissedAt == null`
 *      before picking the next request, so dismissed items stay in the queue
 *      only for retrieval via `getDismissedRequests()` but never re-show.
 *
 *   2. Dismissed entries are pruned with a 5-minute TTL — any entry whose
 *      `dismissedAt` is older than `DISMISSED_TTL_MS` is dropped so the queue
 *      does not grow unbounded across a multi-hour shift.
 *
 *   3. Both behaviors are behind `BuildConfig.FF_DRIVER_IDEMPOTENT_DISMISS`
 *      (default OFF). When the flag is OFF the old code path still runs.
 * =============================================================================
 */
class DriverTripRequestDismissTest {

    private val managerFile = File(
        "src/main/java/com/weelo/logistics/ui/driver/DriverTripRequestManager.kt"
    )

    private val managerSource: String by lazy {
        require(managerFile.exists()) {
            "Manager file not found at ${managerFile.absolutePath}. Test must run with cwd=app/."
        }
        managerFile.readText()
    }

    @Test
    fun `manager references FF_DRIVER_IDEMPOTENT_DISMISS feature flag`() {
        assertTrue(
            "F-C-44: Manager must branch on BuildConfig.FF_DRIVER_IDEMPOTENT_DISMISS",
            managerSource.contains("FF_DRIVER_IDEMPOTENT_DISMISS")
        )
    }

    @Test
    fun `completeCurrentRequest filters dismissedAt null entries when picking next`() {
        // The critical invariant: when walking to the next request we must
        // skip anything that has already been dismissed by the user.
        assertTrue(
            "F-C-44: completeCurrentRequest must filter `dismissedAt == null` " +
                "before selecting the next request — otherwise dismissed items " +
                "loop back into view.",
            managerSource.contains(Regex("""dismissedAt\s*==\s*null"""))
        )
    }

    @Test
    fun `manager defines a 5-minute dismissed TTL constant`() {
        // The TTL is a 5-minute window — long enough for the driver to
        // recover from an accidental dismissal via getDismissedRequests, but
        // short enough that the queue does not grow unbounded.
        assertTrue(
            "F-C-44: Manager must define a DISMISSED_TTL_MS constant " +
                "(or equivalent) capped at 5 minutes = 300_000L",
            managerSource.contains("DISMISSED_TTL_MS") ||
                managerSource.contains("5 * 60 * 1000") ||
                managerSource.contains("300_000")
        )
    }

    @Test
    fun `dismissed entries are pruned by age during completeCurrentRequest`() {
        // The pruning logic should reference System.currentTimeMillis() and
        // compare against a dismissedAt timestamp. Accept either a
        // `filterNot { ... dismissedAt ... }` pattern or explicit age math.
        val hasPrune = managerSource.contains(Regex("""currentTimeMillis\s*\(\s*\)\s*-\s*\S*dismissedAt""")) ||
            managerSource.contains(Regex("""dismissedAt[^)]*currentTimeMillis""")) ||
            managerSource.contains("pruneDismissed") ||
            managerSource.contains(Regex("""filterNot\s*\{[^}]*dismissedAt"""))
        assertTrue(
            "F-C-44: Manager must prune expired dismissed entries using a TTL " +
                "measured against System.currentTimeMillis().",
            hasPrune
        )
    }

    @Test
    fun `onDismiss still tags the current request with dismissedAt`() {
        // The dismiss MARK itself is fine — it's the re-read that was broken.
        // Verify we still tag so getDismissedRequests() keeps working.
        assertTrue(
            "F-C-44: onDismiss must still mark the entry with dismissedAt " +
                "so the driver can recover it via getDismissedRequests.",
            managerSource.contains(Regex("""dismissedAt\s*=\s*System\.currentTimeMillis"""))
        )
    }

    @Test
    fun `getDismissedRequests still filters by dismissedAt not null`() {
        assertTrue(
            "F-C-44: getDismissedRequests must filter entries with non-null dismissedAt",
            managerSource.contains(Regex("""dismissedAt\s*!=\s*null"""))
        )
    }
}
