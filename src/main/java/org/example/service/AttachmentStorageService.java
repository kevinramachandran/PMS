package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");

    @Value("${app.upload.attachments.dir:./uploads/attachments}")
    private String uploadDir;

    public Map<String, String> saveImage(String module, MultipartFile file, String replaceStoredName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image is empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Invalid filename.");
        }

        String ext = getExtension(originalFilename).toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only PNG and JPG images are allowed.");
        }

        Path modulePath = modulePath(module);
        Files.createDirectories(modulePath);

        String storedName = UUID.randomUUID() + "." + ext;
        Path target = modulePath.resolve(storedName).normalize();
        if (!target.startsWith(modulePath)) {
            throw new IllegalArgumentException("Invalid storage path.");
        }

        Files.write(target, file.getBytes());
        deleteImage(module, replaceStoredName);

        return Map.of(
                "storedName", storedName,
                "originalName", sanitizeFilename(originalFilename),
                "url", "/api/attachments/" + normalizeModule(module) + "/file/" + storedName
        );
    }

    public byte[] readImage(String module, String storedName) throws IOException {
        Path path = resolveStoredPath(module, storedName);
        if (!Files.exists(path)) {
            throw new java.io.FileNotFoundException("Attachment not found.");
        }
        return Files.readAllBytes(path);
    }

    /** Existing local bytes let API sync avoid re-downloading immutable, opaque stored names. */
    public boolean hasImage(String module, String storedName) {
        try { return Files.isRegularFile(resolveStoredPath(module, storedName)); }
        catch (IllegalArgumentException ex) { return false; }
    }

    public boolean matchesImage(String module, String storedName, String checksum) {
        if (checksum == null || !checksum.matches("(?i)[a-f0-9]{64}")) return false;
        try {
            byte[] bytes = readImage(module, storedName);
            String local = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            return local.equalsIgnoreCase(checksum);
        } catch (IOException | java.security.NoSuchAlgorithmException | IllegalArgumentException ex) { return false; }
    }

    /** Store an authenticated cloud attachment under its original opaque filename. */
    public void storeSyncedImage(String module, String storedName, byte[] content) throws IOException {
        if (content == null || content.length == 0 || content.length > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Synced image must be between 1 byte and 10 MB.");
        String name = storedName == null ? "" : storedName.trim();
        String ext = getExtension(name).toLowerCase();
        if (!name.matches("[A-Za-z0-9_-]+\\.(?i:png|jpe?g)") || !ALLOWED_EXTENSIONS.contains(ext))
            throw new IllegalArgumentException("Invalid synced image filename.");
        boolean png = content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 0x50
                && content[2] == 0x4e && content[3] == 0x47 && content[4] == 0x0d
                && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a;
        boolean jpeg = content.length >= 3 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8 && content[2] == (byte) 0xff;
        if (!(ext.equals("png") ? png : jpeg)) throw new IllegalArgumentException("Synced image content does not match its PNG/JPG filename.");
        Path modulePath = modulePath(module);
        Files.createDirectories(modulePath);
        Path target = resolveStoredPath(module, name);
        Path temp = Files.createTempFile(modulePath, ".sync-", ".part");
        try {
            Files.write(temp, content);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public void deleteImage(String module, String storedName) {
        if (storedName == null || storedName.isBlank()) return;
        try {
            Files.deleteIfExists(resolveStoredPath(module, storedName));
        } catch (IOException ignored) { }
    }

    public MediaType resolveMediaType(String filename) {
        String ext = getExtension(filename).toLowerCase();
        return "png".equals(ext) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    }

    public String sanitizeFilename(String name) {
        return name == null ? "" : name.replaceAll("[^a-zA-Z0-9.\\-_ ]", "_");
    }

    public String normalizeModule(String module) {
        String value = module == null ? "" : module.trim().toLowerCase().replace('_', '-');
        return switch (value) {
            case "abnormality-reporting", "abnormality", "abnormality-tracker" -> "abnormality-reporting";
            case "gemba-walk", "gemba-schedule" -> "gemba-walk";
            case "gemba-kaizen", "leadership-gemba-tracker" -> "gemba-kaizen";
            case "process-confirmation" -> "process-confirmation";
            default -> throw new IllegalArgumentException("Unsupported attachment module.");
        };
    }

    private Path resolveStoredPath(String module, String storedName) {
        String safeName = sanitizeFilename(storedName);
        Path modulePath = modulePath(module);
        Path path = modulePath.resolve(safeName).normalize();
        if (!path.startsWith(modulePath)) {
            throw new IllegalArgumentException("Invalid attachment path.");
        }
        return path;
    }

    private Path modulePath(String module) {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(normalizeModule(module)).normalize();
    }

    private String getExtension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1) : "";
    }
}
