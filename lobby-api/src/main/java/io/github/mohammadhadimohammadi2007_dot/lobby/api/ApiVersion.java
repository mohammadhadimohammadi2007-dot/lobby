package io.github.mohammadhadimohammadi2007_dot.lobby.api;

/**
 * The version of this API, and which addons can run on it.
 *
 * <p>Semantic versioning: an addon's {@code api-version} ({@code major.minor}, a patch number is ignored) works
 * on this API if the major version is the same and its minor version is not newer. While the major version
 * is 0 the API is not final yet, and the minor version must match exactly.
 */
public final class ApiVersion {

    /** The version of the API in this lobby. */
    public static final String CURRENT = "0.1.0";

    private ApiVersion() {
    }

    /** True if an addon written for {@code required} runs on this API. */
    public static boolean supports(String required) {
        return supports(CURRENT, required);
    }

    /** True if an addon written for {@code required} runs on API version {@code available}. */
    public static boolean supports(String available, String required) {
        int[] have = parse(available);
        int[] want = parse(required);
        if (have == null || want == null || have[0] != want[0]) {
            return false;
        }
        return have[0] == 0 ? have[1] == want[1] : want[1] <= have[1];
    }

    /** Major and minor version, or {@code null} if the text is not a version. */
    private static int[] parse(String version) {
        String[] parts = version.strip().split("\\.");
        if (parts.length < 1 || parts.length > 3) {
            return null;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (major < 0 || minor < 0) {
                return null;
            }
            if (parts.length > 2) {
                Integer.parseInt(parts[2]);
            }
            return new int[] {major, minor};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
