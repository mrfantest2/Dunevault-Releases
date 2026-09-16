package win.fantest.chatweblite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CodespaceUrlPolicyTest {
    @Test
    public void homeUrlIsConfiguredForRequestedCodespace() {
        assertEquals(
                "https://super-halibut-4pv9gpxjr7wcqgqv.github.dev/",
                CodespaceUrlPolicy.HOME_URL
        );
    }

    @Test
    public void acceptsHttpsGithubNavigation() {
        assertTrue(CodespaceUrlPolicy.isInternalWebUrl("https://github.com/login"));
    }

    @Test
    public void rejectsPlainHttpAndCustomSchemes() {
        assertFalse(CodespaceUrlPolicy.isInternalWebUrl("http://example.test/path"));
        assertFalse(CodespaceUrlPolicy.isInternalWebUrl("intent://example/#Intent;scheme=test;end"));
        assertFalse(CodespaceUrlPolicy.isInternalWebUrl("mailto:test@example.com"));
    }

    @Test
    public void recoveryKeepsHttpsAndRejectsUnsafeUrls() {
        String url = "https://super-halibut-4pv9gpxjr7wcqgqv.github.dev/?workspace=1";
        assertEquals(url, CodespaceUrlPolicy.recoveryUrlOrHome(url));
        assertEquals(CodespaceUrlPolicy.HOME_URL, CodespaceUrlPolicy.recoveryUrlOrHome("http://example.test/"));
        assertEquals(CodespaceUrlPolicy.HOME_URL, CodespaceUrlPolicy.recoveryUrlOrHome("javascript:alert(1)"));
        assertEquals(CodespaceUrlPolicy.HOME_URL, CodespaceUrlPolicy.recoveryUrlOrHome(null));
    }
}
