package win.fantest.chatweblite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodespaceReconnectPolicyTest {
    @Test
    public void healthyPageNeverRecovers() {
        assertFalse(CodespaceReconnectPolicy.shouldRecover(true, false, 60_000L, Long.MAX_VALUE));
    }

    @Test
    public void backgroundPageNeverRecovers() {
        assertFalse(CodespaceReconnectPolicy.shouldRecover(false, true, 60_000L, Long.MAX_VALUE));
    }

    @Test
    public void transientDisconnectGetsGracePeriod() {
        assertFalse(CodespaceReconnectPolicy.shouldRecover(true, true, 14_999L, Long.MAX_VALUE));
    }

    @Test
    public void persistentDisconnectRecoversAfterGracePeriod() {
        assertTrue(CodespaceReconnectPolicy.shouldRecover(true, true, 15_000L, Long.MAX_VALUE));
    }

    @Test
    public void recoveryAttemptsHaveCooldown() {
        assertFalse(CodespaceReconnectPolicy.shouldRecover(true, true, 60_000L, 59_999L));
        assertTrue(CodespaceReconnectPolicy.shouldRecover(true, true, 60_000L, 60_000L));
    }
}
