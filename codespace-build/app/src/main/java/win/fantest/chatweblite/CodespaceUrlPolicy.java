package win.fantest.chatweblite;

import java.net.URI;

public final class CodespaceUrlPolicy {
    public static final String HOME_URL = "https://super-halibut-4pv9gpxjr7wcqgqv.github.dev/";

    private CodespaceUrlPolicy() {
    }

    public static boolean isInternalWebUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public static String recoveryUrlOrHome(String url) {
        if (url == null) return HOME_URL;
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) ? url : HOME_URL;
        } catch (IllegalArgumentException error) {
            return HOME_URL;
        }
    }
}
