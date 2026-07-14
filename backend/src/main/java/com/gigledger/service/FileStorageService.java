package com.gigledger.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * FileStorageService — saves uploaded screenshots to the local filesystem.
 *
 * Design decisions:
 *
 * 1. No cloud storage yet (Phase 3 scope). Files go into a configurable local
 *    directory so we can iterate quickly without setting up S3/GCS.
 *
 * 2. UUID filenames — we generate a random UUID for every upload rather than
 *    using the original filename. This prevents:
 *      - Path traversal attacks (e.g. filename = "../../etc/passwd")
 *      - Collision between two users uploading the same filename
 *
 * 3. The directory is created on first use (createDirectories) so no manual
 *    setup is needed when running fresh.
 *
 * 4. screenshotUrl is stored as a relative path (e.g. "uploads/screenshots/abc.png").
 *    In production, this would be replaced by a full cloud URL.
 */
@Slf4j
@Service
public class FileStorageService {

    private final Path storageRoot;

    /**
     * @param uploadDir  from application.properties: screenshot.upload.dir
     *                   Defaults to "uploads/screenshots" (relative to the working dir).
     */
    public FileStorageService(@Value("${screenshot.upload.dir}") String uploadDir) {
        this.storageRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageRoot);
            log.info("Screenshot storage root: {}", this.storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create screenshot upload directory: " + this.storageRoot, e);
        }
    }

    /**
     * Save a multipart image file to disk.
     *
     * @param file  the uploaded MultipartFile from the HTTP request
     * @return      the relative path string to store in the database (screenshotUrl)
     * @throws IOException if the file cannot be written
     */
    public String save(MultipartFile file) throws IOException {
        // Determine file extension from the original filename
        String originalName = file.getOriginalFilename();
        String extension = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf('.'))
                : ".png";

        // Generate a unique filename — never use the client-supplied name directly
        String uniqueName = UUID.randomUUID() + extension;
        Path destination = this.storageRoot.resolve(uniqueName);

        file.transferTo(destination);
        log.debug("Saved screenshot: {} ({} KB)", destination, file.getSize() / 1024);

        // Return a relative path for storage in the DB
        // In production this would be the cloud URL (e.g. https://cdn.gigledger.com/screenshots/uuid.png)
        return "uploads/screenshots/" + uniqueName;
    }

    /**
     * Resolve a stored relative path back to an absolute filesystem Path.
     * Used by the serve-image endpoint (not built yet — Phase 3 scope).
     */
    public Path resolve(String relativePath) {
        return this.storageRoot.resolve(
                Paths.get(relativePath).getFileName()  // strip any directory prefix
        ).normalize();
    }
}
