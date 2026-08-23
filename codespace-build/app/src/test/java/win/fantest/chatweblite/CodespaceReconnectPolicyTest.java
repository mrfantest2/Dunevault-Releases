package win.fantest.chatweblite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CodespaceReconnectPolicyTest {
    @Test
    public void neverRecoversWhileBackgrounded() {
        CodespaceReconnectPolicy policy = new CodespaceReconnectPolicy();
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        false,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        100_000L
                )
        );
    }

    @Test
    public void neverRecoversWithoutValidatedNetwork() {
        CodespaceReconnectPolicy policy = new CodespaceReconnectPolicy();
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        false,
                        CodespaceReconnectPolicy.Signal.OFFLINE_PAGE,
                        100_000L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        false,
                        CodespaceReconnectPolicy.Signal.OFFLINE_PAGE,
                        200_000L
                )
        );
    }

    @Test
    public void waitsFifteenSecondsForVsCodeReconnect() {
        CodespaceReconnectPolicy policy = new CodespaceReconnectPolicy();
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        1_000L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        15_999L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.RELOAD,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        16_000L
                )
        );
    }

    @Test
    public void networkReturnStartsFreshOfflineGraceWindow() {
        CodespaceReconnectPolicy policy = new CodespaceReconnectPolicy();
        policy.onProbe(
                true,
                false,
                CodespaceReconnectPolicy.Signal.OFFLINE_PAGE,
                1_000L
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.OFFLINE_PAGE,
                        60_000L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.OFFLINE_PAGE,
                        64_999L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.RELOAD,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.OFFLINE_PAGE,
                        65_000L
                )
        );
    }

    @Test
    public void offlineBackoffProgressesAndCaps() {
        assertEquals(5_000L, CodespaceReconnectPolicy.offlineBackoffMs(0));
        assertEquals(15_000L, CodespaceReconnectPolicy.offlineBackoffMs(1));
        assertEquals(30_000L, CodespaceReconnectPolicy.offlineBackoffMs(2));
        assertEquals(60_000L, CodespaceReconnectPolicy.offlineBackoffMs(3));
        assertEquals(60_000L, CodespaceReconnectPolicy.offlineBackoffMs(99));
    }

    @Test
    public void hardCooldownBlocksRapidRepeatRecovery() {
        CodespaceReconnectPolicy policy = new CodespaceReconnectPolicy();
        policy.onProbe(
                true,
                true,
                CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                1_000L
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.RELOAD,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        16_000L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        75_999L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.RELOAD,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        76_000L
                )
        );
    }

    @Test
    public void healthyStateResetsOfflineAttempts() {
        CodespaceReconnectPolicy policy = new CodespaceReconnectPolicy();
        policy.onProbe(true, true, CodespaceReconnectPolicy.Signal.OFFLINE_PAGE, 0L);
        assertEquals(
                CodespaceReconnectPolicy.Decision.RELOAD,
                policy.onProbe(true, true, CodespaceReconnectPolicy.Signal.OFFLINE_PAGE, 5_000L)
        );
        policy.onProbe(true, true, CodespaceReconnectPolicy.Signal.HEALTHY, 6_000L);
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(true, true, CodespaceReconnectPolicy.Signal.OFFLINE_PAGE, 7_000L)
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(true, true, CodespaceReconnectPolicy.Signal.OFFLINE_PAGE, 11_999L)
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.RELOAD,
                policy.onProbe(true, true, CodespaceReconnectPolicy.Signal.OFFLINE_PAGE, 12_000L)
        );
    }

    @Test
    public void changedFailureSignalGetsFreshTimer() {
        CodespaceReconnectPolicy policy = new CodespaceReconnectPolicy();
        policy.onProbe(true, true, CodespaceReconnectPolicy.Signal.OFFLINE_PAGE, 0L);
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        4_000L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.NONE,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        18_999L
                )
        );
        assertEquals(
                CodespaceReconnectPolicy.Decision.RELOAD,
                policy.onProbe(
                        true,
                        true,
                        CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED,
                        19_000L
                )
        );
    }
}
