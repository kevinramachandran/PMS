package org.example.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class HostBiosIdProvider {

    public Optional<String> getBiosId() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String value = osName.contains("win") ? readWindowsBiosId()
                : osName.contains("linux") ? readLinuxBiosId() : "";
        return normalize(value);
    }

    private String readWindowsBiosId() {
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; (Get-CimInstance -ClassName Win32_BIOS -ErrorAction Stop).SerialNumber")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 ? output : "";
        } catch (IOException ex) {
            return "";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String readLinuxBiosId() {
        try {
            return Files.readString(Path.of("/sys/class/dmi/id/bios_serial"), StandardCharsets.UTF_8).trim();
        } catch (IOException | SecurityException ex) {
            return "";
        }
    }

    private Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        String compact = normalized.replaceAll("[^A-Z0-9]", "");
        if (normalized.isBlank() || compact.isBlank()
                || compact.matches("0+")
                || compact.matches("F+")
                || compact.contains("TOBEFILLEDBYOEM")
                || compact.contains("DEFAULTSTRING")
                || compact.contains("SYSTEMSERIALNUMBER")
                || compact.equals("UNKNOWN")
                || compact.equals("NONE")) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }
}