package win.fantest.chatweblite;

import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public final class ScreenshotPolicyTest {
    @Test
    public void screenshotDirectoryIsStable() {
        assertEquals("Pictures/FantestCodespace", ScreenshotPolicy.RELATIVE_DIRECTORY);
    }

    @Test
    public void screenshotFilenameUsesRequestedPattern() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        assertEquals(
                "FantestCodespace_20260823_023500.png",
                ScreenshotPolicy.fileName(1787452500000L, utc)
        );
    }
}
