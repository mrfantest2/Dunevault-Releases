package win.fantest.chatweblite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InputModePolicyTest {
    @Test
    public void normalModeAllowsTouchFocusAndSoftKeyboard() {
        assertFalse(InputModePolicy.shouldConsumeTouch(false));
        assertTrue(InputModePolicy.shouldKeepWebViewFocusable(false));
        assertTrue(InputModePolicy.shouldShowSoftInput(false, false));
    }

    @Test
    public void keyboardLockKeepsTouchAndFocusButSuppressesSoftKeyboard() {
        assertFalse(InputModePolicy.shouldConsumeTouch(false));
        assertTrue(InputModePolicy.shouldKeepWebViewFocusable(false));
        assertFalse(InputModePolicy.shouldShowSoftInput(false, true));
    }

    @Test
    public void touchLockConsumesTouchAndSuppressesSoftKeyboard() {
        assertTrue(InputModePolicy.shouldConsumeTouch(true));
        assertFalse(InputModePolicy.shouldKeepWebViewFocusable(true));
        assertFalse(InputModePolicy.shouldShowSoftInput(true, false));
    }

    @Test
    public void touchLockTakesPrecedenceWhenBothLocksAreEnabled() {
        assertTrue(InputModePolicy.shouldConsumeTouch(true));
        assertFalse(InputModePolicy.shouldKeepWebViewFocusable(true));
        assertFalse(InputModePolicy.shouldShowSoftInput(true, true));
    }
}
