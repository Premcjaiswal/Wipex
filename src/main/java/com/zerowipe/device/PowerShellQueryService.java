package com.zerowipe.device;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs read-only PowerShell queries against the Windows storage cmdlets
 * and parses their JSON output. Used for the data the native IOCTL layer
 * cannot provide directly: SMART/media-type cross-check, health status,
 * wear percentage and power-on hours.
 *
 * <p>Uses its own dedicated, leniently-configured {@link ObjectMapper}
 * (case-insensitive property matching, since PowerShell's {@code
 * ConvertTo-Json} emits PascalCase field names against our camelCase Java
 * records) rather than the application's shared Jackson bean, so this
 * class's parsing needs never affect REST JSON handling.
 *
 * <p>{@code ConvertTo-Json} emits a single JSON object (not a one-element
 * array) when a pipeline produces exactly one result - all parsing here
 * accounts for that.
 */
@Service
public class PowerShellQueryService {

    private static final Logger log = LoggerFactory.getLogger(PowerShellQueryService.class);
    private static final long TIMEOUT_SECONDS = 30;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public List<PhysicalDiskInfo> queryPhysicalDisks() {
        String output = runPowerShell(
                "Get-PhysicalDisk | Select-Object DeviceId, MediaType, HealthStatus | ConvertTo-Json");
        return parseJsonArray(output, PhysicalDiskInfo.class);
    }

    public List<StorageReliabilityCounterInfo> queryReliabilityCounters() {
        String output = runPowerShell(
                "Get-StorageReliabilityCounter | Select-Object DeviceId, Wear, PowerOnHours | ConvertTo-Json");
        return parseJsonArray(output, StorageReliabilityCounterInfo.class);
    }

    public List<Integer> queryDiskNumbersForDriveLetter(String driveLetter) {
        String script = "Get-Partition | Where-Object { $_.DriveLetter -eq '" + driveLetter + "' } "
                + "| Select-Object DiskNumber | ConvertTo-Json";
        String output = runPowerShell(script);
        return parseJsonArray(output, PartitionInfo.class).stream()
                .map(PartitionInfo::diskNumber)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Parses PowerShell {@code ConvertTo-Json} output into a list,
     * transparently handling the single-object-vs-array quirk. Package
     * visible so its trickiest behaviour can be unit tested without
     * spawning a real PowerShell process.
     */
    <T> List<T> parseJsonArray(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<T> results = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode element : node) {
                    results.add(objectMapper.treeToValue(element, type));
                }
            } else if (node.isObject()) {
                results.add(objectMapper.treeToValue(node, type));
            }
            return List.copyOf(results);
        } catch (IOException e) {
            log.warn("Failed to parse PowerShell JSON output as {}: {}", type.getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    private String runPowerShell(String script) {
        try {
            ProcessBuilder processBuilder =
                    new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("PowerShell query timed out after {}s: {}", TIMEOUT_SECONDS, script);
                return null;
            }
            if (process.exitValue() != 0) {
                log.warn("PowerShell query exited {}: {}", process.exitValue(), script);
            }
            return output;
        } catch (IOException e) {
            log.warn("Failed to launch PowerShell for query [{}]: {}", script, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for PowerShell query [{}]", script, e);
            return null;
        }
    }
}
