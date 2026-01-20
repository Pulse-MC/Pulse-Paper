package com.destroystokyo.paper;

import com.destroystokyo.paper.util.VersionFetcher;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import io.papermc.paper.ServerBuildInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.TextColor.color;
import static io.papermc.paper.ServerBuildInfo.StringRepresentation.VERSION_SIMPLE;

@DefaultQualifier(NonNull.class)
public class PaperVersionFetcher implements VersionFetcher {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final ComponentLogger COMPONENT_LOGGER = ComponentLogger.logger(LogManager.getRootLogger().getName());

    private static final int DISTANCE_ERROR = -1;
    private static final int DISTANCE_UNKNOWN = -2;

    // Pulse API Endpoints
    private static final String API_DEV_BUILDS = "https://api.pulsemc.dev/devbuilds";
    private static final String API_RELEASES = "https://api.pulsemc.dev/releases";
    private static final String DOWNLOAD_PAGE = "https://pulsemc.dev/downloads";

    private static final ServerBuildInfo BUILD_INFO = ServerBuildInfo.buildInfo();
    private static final String USER_AGENT = BUILD_INFO.brandName() + "/" + BUILD_INFO.asString(VERSION_SIMPLE) + " (Pulse Version Checker)";
    private static final Gson GSON = new Gson();

    @Override
    public long getCacheTime() {
        return 720000;
    }

    @Override
    public Component getVersionMessage() {
        final Component updateMessage;
        if (BUILD_INFO.buildNumber().isEmpty()) {
            updateMessage = text("You are running a custom version without access to version information", color(0xFF5300));
        } else {
            updateMessage = getUpdateStatusMessage();
        }
        final @Nullable Component history = this.getHistory();

        return history != null ? Component.textOfChildren(updateMessage, Component.newline(), history) : updateMessage;
    }

    private enum BuildType {
        STABLE,
        DEV,
        UNKNOWN
    }

    private record PulseVersionResult(BuildType type, int distance, int currentBuild, int latestBuild) {}

    // Pulse start
    public static void getUpdateStatusStartupMessage() {
        PulseVersionResult result = fetchPulseVersion();

        if (result.type == BuildType.DEV) {
            COMPONENT_LOGGER.warn(text("************************************************************", NamedTextColor.RED));
            COMPONENT_LOGGER.warn(text("* WARNING: YOU ARE RUNNING A DEVELOPMENT BUILD OF PULSE!   *", NamedTextColor.RED));
            COMPONENT_LOGGER.warn(text("* Build ID: " + result.currentBuild, NamedTextColor.RED));
            COMPONENT_LOGGER.warn(text("* This version may contain bugs or unstable features.      *", NamedTextColor.RED));
            COMPONENT_LOGGER.warn(text("* PLEASE MAKE SURE YOU HAVE BACKUPS OF YOUR SERVER!        *", NamedTextColor.RED));
            COMPONENT_LOGGER.warn(text("************************************************************", NamedTextColor.RED));
        }

        switch (result.distance) {
            case DISTANCE_ERROR -> COMPONENT_LOGGER.warn(text("Error obtaining version information from Pulse API."));
            case 0 -> COMPONENT_LOGGER.info(text("You are running the latest version of Pulse (" + result.type + ")."));
            case DISTANCE_UNKNOWN -> COMPONENT_LOGGER.warn(text("Unknown version! Cannot verify build ID against the API."));
            default -> {
                COMPONENT_LOGGER.warn(text("************************************************************"));
                COMPONENT_LOGGER.warn(text("* You are running an outdated version of Pulse (" + result.type + ")!"));
                COMPONENT_LOGGER.warn(text("* You are " + result.distance + " build(s) behind."));
                COMPONENT_LOGGER.warn(text("* Latest build: " + result.latestBuild));
                COMPONENT_LOGGER.warn(text("* Download the latest build:"));
                COMPONENT_LOGGER.warn(text("* " + DOWNLOAD_PAGE));
                COMPONENT_LOGGER.warn(text("************************************************************"));
            }
        }
    }

    private static Component getUpdateStatusMessage() {
        PulseVersionResult result = fetchPulseVersion();

        Component baseMessage;
        if (result.type == BuildType.DEV) {
            baseMessage = text("You are running a DEVELOPMENT build (#" + result.currentBuild + ")", NamedTextColor.RED);
        } else {
            baseMessage = text("You are running a Stable build", NamedTextColor.GREEN);
        }

        return switch (result.distance) {
            case DISTANCE_ERROR -> text("Error obtaining version information", NamedTextColor.YELLOW);
            case 0 -> baseMessage.append(text(" (Latest)", NamedTextColor.GREEN));
            case DISTANCE_UNKNOWN -> text("Unknown version", NamedTextColor.YELLOW);
            default -> baseMessage
                .append(Component.newline())
                .append(text("You are " + result.distance + " version(s) behind", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(text("Download update: ")
                    .append(text(DOWNLOAD_PAGE, NamedTextColor.GOLD)
                        .hoverEvent(text("Click to open", NamedTextColor.WHITE))
                        .clickEvent(ClickEvent.openUrl(DOWNLOAD_PAGE))));
        };
    }

    private static PulseVersionResult fetchPulseVersion() {
        OptionalInt buildNumberOpt = BUILD_INFO.buildNumber();
        if (buildNumberOpt.isEmpty()) {
            return new PulseVersionResult(BuildType.UNKNOWN, DISTANCE_UNKNOWN, -1, -1);
        }

        int currentBuildId = buildNumberOpt.getAsInt();

        Set<Integer> devBuilds = new HashSet<>();
        Set<Integer> releaseBuilds = new HashSet<>();
        int maxDev = -1;
        int maxRelease = -1;

        // Fetch Dev Builds
        try {
            JsonArray devJson = fetchJsonArray(API_DEV_BUILDS);
            if (devJson != null) {
                for (JsonElement el : devJson) {
                    int id = extractId(el);
                    if (id != -1) {
                        devBuilds.add(id);
                        if (id > maxDev) maxDev = id;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to fetch dev builds from Pulse API", e);
            return new PulseVersionResult(BuildType.UNKNOWN, DISTANCE_ERROR, currentBuildId, -1);
        }

        // Fetch Releases
        try {
            JsonArray releaseJson = fetchJsonArray(API_RELEASES);
            if (releaseJson != null) {
                for (JsonElement el : releaseJson) {
                    int id = extractId(el);
                    if (id != -1) {
                        releaseBuilds.add(id);
                        if (id > maxRelease) maxRelease = id;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch releases from Pulse API", e);
        }

        if (releaseBuilds.contains(currentBuildId)) {
            int distance = (maxRelease > 0) ? (maxRelease - currentBuildId) : 0;
            return new PulseVersionResult(BuildType.STABLE, distance, currentBuildId, maxRelease);
        }
        else if (devBuilds.contains(currentBuildId)) {
            int distance = (maxDev > 0) ? (maxDev - currentBuildId) : 0;
            return new PulseVersionResult(BuildType.DEV, distance, currentBuildId, maxDev);
        }

        return new PulseVersionResult(BuildType.UNKNOWN, DISTANCE_UNKNOWN, currentBuildId, -1);
    }

    private static int extractId(JsonElement el) {
        try {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("build_id")) {
                    return Integer.parseInt(obj.get("build_id").getAsString());
                }
                if (obj.has("id")) return obj.get("id").getAsInt();
            } else if (el.isJsonPrimitive()) {
                return el.getAsInt();
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    private static @Nullable JsonArray fetchJsonArray(String urlString) throws IOException {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");

        if (connection.getResponseCode() != 200) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                return root.getAsJsonArray("data");
            }
            return null;
        } catch (JsonSyntaxException e) {
            LOGGER.error("Invalid JSON from " + urlString, e);
            return null;
        }
    }
    // Pulse end

    private @Nullable Component getHistory() {
        final VersionHistoryManager.@Nullable VersionData data = VersionHistoryManager.INSTANCE.getVersionData();
        if (data == null) {
            return null;
        }

        final @Nullable String oldVersion = data.getOldVersion();
        if (oldVersion == null) {
            return null;
        }

        return text("Previous version: " + oldVersion, NamedTextColor.GRAY, TextDecoration.ITALIC);
    }
}
