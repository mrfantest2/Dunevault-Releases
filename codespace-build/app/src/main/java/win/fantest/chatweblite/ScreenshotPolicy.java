package win.fantest.chatweblite;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class ScreenshotPolicy {
    static final String RELATIVE_DIRECTORY = "Pictures/FantestCodespace";

    private ScreenshotPolicy() {
    }

    static String fileName(long timestampMs, TimeZone timeZone) {
        SimpleDateFormat format = new SimpleDateFormat(
                "'FantestCodespace_'yyyyMMdd_HHmmss'.png'",
                Locale.US
        );
        format.setTimeZone(timeZone == null ? TimeZone.getDefault() : timeZone);
        return format.format(new Date(timestampMs));
    }
}
