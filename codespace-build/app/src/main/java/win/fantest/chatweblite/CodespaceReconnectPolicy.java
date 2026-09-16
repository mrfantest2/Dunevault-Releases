package win.fantest.chatweblite;

final class CodespaceReconnectPolicy {
    enum Signal {
        HEALTHY,
        VS_CODE_DISCONNECTED,
        OFFLINE_PAGE
    }

    enum Decision {
        NONE,
        RELOAD
    }

    static final long VS_CODE_GRACE_MS = 15_000L;
    static final long HARD_COOLDOWN_MS = 60_000L;

    private Signal currentSignal = Signal.HEALTHY;
    private long signalSinceMs = -1L;
    private long lastRecoveryMs = Long.MIN_VALUE;
    private int offlineRecoveryAttempts;
    private boolean previousNetworkValidated;

    Decision onProbe(
            boolean foreground,
            boolean networkValidated,
            Signal signal,
            long nowMs
    ) {
        if (signal == null) signal = Signal.HEALTHY;

        if (signal == Signal.HEALTHY) {
            resetHealthy(networkValidated);
            return Decision.NONE;
        }

        if (!foreground) {
            return Decision.NONE;
        }

        if (!networkValidated) {
            previousNetworkValidated = false;
            return Decision.NONE;
        }

        boolean networkJustReturned = !previousNetworkValidated;
        previousNetworkValidated = true;

        if (networkJustReturned || currentSignal != signal || signalSinceMs < 0L) {
            currentSignal = signal;
            signalSinceMs = nowMs;
            return Decision.NONE;
        }

        long requiredDelayMs = signal == Signal.VS_CODE_DISCONNECTED
                ? VS_CODE_GRACE_MS
                : offlineBackoffMs(offlineRecoveryAttempts);
        long signalElapsedMs = nowMs - signalSinceMs;
        long cooldownElapsedMs = lastRecoveryMs == Long.MIN_VALUE
                ? Long.MAX_VALUE
                : nowMs - lastRecoveryMs;

        if (signalElapsedMs < requiredDelayMs || cooldownElapsedMs < HARD_COOLDOWN_MS) {
            return Decision.NONE;
        }

        lastRecoveryMs = nowMs;
        signalSinceMs = nowMs;
        if (signal == Signal.OFFLINE_PAGE) {
            offlineRecoveryAttempts++;
        }
        return Decision.RELOAD;
    }

    static long offlineBackoffMs(int recoveryAttempt) {
        if (recoveryAttempt <= 0) return 5_000L;
        if (recoveryAttempt == 1) return 15_000L;
        if (recoveryAttempt == 2) return 30_000L;
        return 60_000L;
    }

    private void resetHealthy(boolean networkValidated) {
        currentSignal = Signal.HEALTHY;
        signalSinceMs = -1L;
        lastRecoveryMs = Long.MIN_VALUE;
        offlineRecoveryAttempts = 0;
        previousNetworkValidated = networkValidated;
    }
}
