package org.example.controller;

import jakarta.servlet.http.HttpSession;
import org.example.service.AttachmentStorageService;
import org.example.util.RoleAccess;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentStorageService storageService;

    public AttachmentController(AttachmentStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/{module}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@PathVariable String module,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "replace", required = false) String replace,
                                                      HttpSession session) {
        if (!canEdit(session, module)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
        }
        try {
            return ResponseEntity.ok(storageService.saveImage(module, file, replace));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to save image."));
        }
    }

    @GetMapping("/{module}/file/{storedName}")
    public ResponseEntity<byte[]> file(@PathVariable String module,
                                       @PathVariable String storedName,
                                       HttpSession session) {
        if (!canView(session, module)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            byte[] data = storageService.readImage(module, storedName);
            String safeName = storageService.sanitizeFilename(storedName);
            return ResponseEntity.ok()
                    .contentType(storageService.resolveMediaType(storedName))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeName + "\"")
                    .body(data);
        } catch (FileNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean canView(HttpSession session, String module) {
        if ("gemba-kaizen".equals(storageService.normalizeModule(module))) {
            return session != null && session.getAttribute("username") != null;
        }
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canViewPage(role, permissions(session, "viewPermissions"), pageKey(module));
    }

    private boolean canEdit(HttpSession session, String module) {
        String normalizedModule = storageService.normalizeModule(module);
        if ("abnormality-reporting".equals(normalizedModule) || "gemba-walk".equals(normalizedModule)
                || "gemba-kaizen".equals(normalizedModule)) {
            if ("gemba-kaizen".equals(normalizedModule)) {
                return session != null && session.getAttribute("username") != null;
            }
            return canView(session, module);
        }
        String role = session == null ? null : (String) session.getAttribute("role");
        return RoleAccess.canEditPage(role, permissions(session, "editPermissions"), pageKey(module));
    }

    private String pageKey(String module) {
        return switch (storageService.normalizeModule(module)) {
            case "abnormality-reporting" -> RoleAccess.PAGE_ABNORMALITY_TRACKER_CONFIGURATION;
            case "gemba-walk" -> RoleAccess.PAGE_GEMBA_WALK_CONFIGURATION;
            case "gemba-kaizen" -> RoleAccess.PAGE_LEADERSHIP_GEMBA_TRACKER_CONFIGURATION;
            case "process-confirmation" -> RoleAccess.PAGE_PROCESS_CONFIRMATION_CONFIGURATION;
            default -> "";
        };
    }

    @SuppressWarnings("unchecked")
    private Set<String> permissions(HttpSession session, String key) {
        Object raw = session == null ? null : session.getAttribute(key);
        return raw instanceof Set<?> ? (Set<String>) raw : Set.of();
    }
}
