package win.fantest.chatweblite;

final class CodespaceReconnectPolicy {
    static final long DISCONNECT_GRACE_MS = 15_000L;
    static final long RECOVERY_COOLDOWN_MS = 60_000L;

    private CodespaceReconnectPolicy() {
    }

    static boolean shouldRecover(
            boolean foreground,
            boolean disconnected,
            long disconnectedForMs,
            long sinceLastRecoveryMs
    ) {
        return foreground
                && disconnected
                && disconnectedForMs >= DISCONNECT_GRACE_MS
                && sinceLastRecoveryMs >= RECOVERY_COOLDOWN_MS;
    }
}
