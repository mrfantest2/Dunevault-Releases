package win.fantest.chatweblite;

final class InputModePolicy {
    private InputModePolicy() {
    }

    static boolean shouldConsumeTouch(boolean touchLocked) {
        return touchLocked;
    }

    static boolean shouldKeepWebViewFocusable(boolean touchLocked) {
        return !touchLocked;
    }

    static boolean shouldShowSoftInput(boolean touchLocked, boolean keyboardLocked) {
        return !touchLocked && !keyboardLocked;
    }
}
