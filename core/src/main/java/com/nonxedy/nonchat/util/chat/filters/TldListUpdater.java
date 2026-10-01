package com.nonxedy.nonchat.util.chat.filters;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import org.bukkit.Bukkit;

/**
 * Downloads a fresh copy of the TLD registry (by default the official IANA
 * list) and swaps it into {@link TldList}.
 *
 * <p>The download always runs asynchronously off the main thread and never
 * blocks chat: until it succeeds (or if it fails entirely) the registry
 * bundled in the jar stays active. A truncated download or an error page is
 * rejected by {@link TldList#updateRegistry(java.util.Collection)}, so a bad
 * response can never weaken advertisement detection.
 */
public final class TldListUpdater {

    private static final Logger LOGGER = Logger.getLogger("nonchat");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private TldListUpdater() {
    }

    /**
     * Schedules an asynchronous registry refresh.
     *
     * @param plugin owning plugin (for the scheduler)
     * @param url    source of a {@code tlds-alpha-by-domain.txt}-style file
     */
    public static void updateAsync(Plugin plugin, String url) {
        if (plugin == null || url == null || url.isBlank()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> update(url));
    }

    /**
     * Performs the download and registry swap. Package-visible for tests;
     * production callers should use {@link #updateAsync(Plugin, String)}.
     */
    static void update(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "nonchat-plugin")
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                LOGGER.warning("[nonchat] TLD list update failed: HTTP "
                        + response.statusCode() + "; keeping the current registry");
                return;
            }

            try (InputStream in = response.body()) {
                Set<String> tlds = TldList.parseRegistry(in);
                if (TldList.updateRegistry(tlds)) {
                    LOGGER.info("[nonchat] TLD registry updated from " + url
                            + " (" + tlds.size() + " entries)");
                } else {
                    LOGGER.warning("[nonchat] Downloaded TLD list looks invalid; "
                            + "keeping the current registry");
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[nonchat] Could not download the TLD list ("
                    + e.getMessage() + "); keeping the current registry");
        }
    }
}
