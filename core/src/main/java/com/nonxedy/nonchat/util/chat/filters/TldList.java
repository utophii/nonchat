package com.nonxedy.nonchat.util.chat.filters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Validates the top-level domain (TLD) of domain-looking text.
 *
 * <p>The regexes in {@link AdDetector} and {@link LinkDetector} treat any
 * {@code word.word} sequence as a potential domain, so ordinary chat text
 * such as "hello.hello" used to be flagged as an advertisement or rendered
 * as a clickable link. Validating the last label against the registry of
 * real TLDs removes that class of false positives: "hello" is not a TLD,
 * while "com", "ru" or "gg" are.
 *
 * <p>The full registry is parsed from the official IANA list
 * (https://data.iana.org/TLD/tlds-alpha-by-domain.txt), bundled in the jar
 * as {@code tlds-alpha-by-domain.txt} and loaded once. The registry changes
 * only a few times per year, so the bundled snapshot is refreshed together
 * with plugin releases; no runtime network access is required.
 *
 * <p>URLs with an explicit scheme are unambiguous and are handled without
 * this list.
 */
public final class TldList {

    private static final Logger LOGGER = Logger.getLogger("nonchat");

    private static final String TLD_RESOURCE = "/tlds-alpha-by-domain.txt";

    /** IPv4 literals have no TLD but are still real, connectable hosts. */
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

    /** Used only when the bundled registry somehow cannot be read. */
    private static final Set<String> FALLBACK_TLDS = Set.of(
            "com", "net", "org", "info", "biz", "ru", "su", "ua", "by", "kz",
            "io", "gg", "co", "me", "tv", "xyz", "top", "online", "site",
            "shop", "club", "live", "pro", "fun");

    /** A downloaded registry with fewer entries is treated as corrupt. */
    private static final int MIN_REGISTRY_SIZE = 100;

    private static final Set<String> BUNDLED_TLDS = loadBundledRegistry();

    /** Active registry: the bundled snapshot until a download replaces it. */
    private static volatile Set<String> knownTlds = BUNDLED_TLDS;

    private TldList() {
    }

    /**
     * Returns whether the given TLD label is a real TLD according to the
     * IANA registry.
     *
     * @param tld TLD label, case-insensitive
     * @return true when the label is a real TLD
     */
    public static boolean isKnownTld(String tld) {
        return tld != null && knownTlds.contains(tld.toLowerCase(Locale.ROOT));
    }

    /**
     * Replaces the active TLD registry, typically with a freshly downloaded
     * copy of the IANA list (see {@link TldListUpdater}). The swap is atomic;
     * readers on other threads see either the old or the new registry.
     *
     * <p>A null collection restores the bundled snapshot. Registries that
     * are suspiciously small (fewer than {@value #MIN_REGISTRY_SIZE} valid
     * entries) are rejected, so a truncated download or an error page can
     * never silently disable advertisement detection.
     *
     * @param newTlds TLD labels of the new registry, or null to restore defaults
     * @return true when the registry was replaced (or restored)
     */
    public static boolean updateRegistry(Collection<String> newTlds) {
        if (newTlds == null) {
            knownTlds = BUNDLED_TLDS;
            return true;
        }
        Set<String> lowered = new HashSet<>();
        for (String tld : newTlds) {
            if (tld == null) {
                continue;
            }
            String candidate = tld.trim().toLowerCase(Locale.ROOT);
            if (candidate.length() > 63 || !candidate.matches("[a-z0-9-]+")) {
                continue;
            }
            lowered.add(candidate);
        }
        if (lowered.size() < MIN_REGISTRY_SIZE) {
            return false;
        }
        knownTlds = Set.copyOf(lowered);
        return true;
    }

    /**
     * Parses a {@code tlds-alpha-by-domain.txt}-style stream: one TLD per
     * line, '#' comments and blank lines are skipped.
     *
     * @param in registry data
     * @return the parsed labels, lowercased (possibly empty; callers must
     *         validate the size before {@link #updateRegistry(Collection)})
     * @throws java.io.IOException when the stream cannot be read
     */
    public static Set<String> parseRegistry(java.io.InputStream in) throws java.io.IOException {
        Set<String> tlds = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                    continue;
                }
                tlds.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return tlds;
    }

    /**
     * Extracts the TLD (the last dot-separated label of the host) from a
     * URL- or domain-like string. Scheme, userinfo, port, path, query,
     * fragment and trailing sentence punctuation are ignored.
     *
     * @param candidate URL- or domain-like text, e.g. "https://Mc.Example.com:25565/join"
     * @return the lowercase TLD, or null when the text has no alphabetic TLD
     */
    public static String extractTld(String candidate) {
        String host = extractHost(candidate);
        if (host == null) {
            return null;
        }
        int lastDot = host.lastIndexOf('.');
        if (lastDot < 0 || lastDot == host.length() - 1) {
            return null;
        }
        String tld = host.substring(lastDot + 1);
        for (int i = 0; i < tld.length(); i++) {
            char c = tld.charAt(i);
            if (c < 'a' || c > 'z') {
                return null;
            }
        }
        return tld;
    }

    /**
     * Returns whether the candidate ends in a TLD from the registry.
     *
     * @param candidate URL- or domain-like text
     * @return true when the host part ends in a real TLD
     */
    public static boolean hasKnownTld(String candidate) {
        return isKnownTld(extractTld(candidate));
    }

    /**
     * Decides whether a bare (scheme-less) match looks like a real,
     * connectable host: either an IPv4 literal or a domain whose TLD is in
     * the registry. This is what keeps ordinary "word.word" chat text
     * such as "hello.hello" from being treated as an advertisement.
     *
     * @param candidate bare host, IP or domain-like match
     * @return true when the candidate is plausibly a real host
     */
    public static boolean isPlausibleHost(String candidate) {
        String host = extractHost(candidate);
        if (host == null) {
            return false;
        }
        if (IPV4_PATTERN.matcher(host).matches()) {
            return true;
        }
        return isKnownTld(extractTld(host));
    }

    /**
     * Loads the bundled IANA registry snapshot from the jar resources.
     */
    private static Set<String> loadBundledRegistry() {
        try (InputStream in = TldList.class.getResourceAsStream(TLD_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Bundled TLD registry not found: " + TLD_RESOURCE);
            }
            Set<String> tlds = parseRegistry(in);
            if (tlds.size() < MIN_REGISTRY_SIZE) {
                throw new IllegalStateException(
                        "Bundled TLD registry is incomplete: " + tlds.size() + " entries");
            }
            return Set.copyOf(tlds);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "[nonchat] Failed to load the bundled IANA TLD registry; "
                            + "falling back to a minimal built-in list", e);
            return FALLBACK_TLDS;
        }
    }

    /**
     * Normalizes a URL- or domain-like string down to its bare host:
     * scheme, userinfo, port, path, query, fragment and surrounding
     * sentence punctuation are removed.
     */
    private static String extractHost(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        String host = candidate.toLowerCase(Locale.ROOT);

        int schemeEnd = host.indexOf("://");
        if (schemeEnd >= 0) {
            host = host.substring(schemeEnd + 3);
        }

        int at = host.indexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }

        int cut = -1;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '/' || c == '?' || c == '#' || c == ':') {
                cut = i;
                break;
            }
        }
        if (cut >= 0) {
            host = host.substring(0, cut);
        }

        // Drop surrounding punctuation such as the trailing comma of
        // "google.com," or the parentheses of "(discord.gg/abc)".
        int start = 0;
        int end = host.length();
        while (start < end && !isHostChar(host.charAt(start))) {
            start++;
        }
        while (end > start && !isHostChar(host.charAt(end - 1))) {
            end--;
        }
        host = host.substring(start, end);

        return host.isEmpty() ? null : host;
    }

    private static boolean isHostChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-';
    }
}
