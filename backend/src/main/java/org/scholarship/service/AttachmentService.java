package org.scholarship.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** 附件本地存储：保存到 app.storage.dir/{cycleId}/uuid__原文件名，仅本地文件系统，无对象存储。 */
@ApplicationScoped
public class AttachmentService {

    @ConfigProperty(name = "app.storage.dir", defaultValue = "data/attachments")
    String storageDir;

    private Path root;

    @PostConstruct
    void init() {
        root = Path.of(storageDir).toAbsolutePath();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public StoredAttachment store(Long cycleId, java.io.InputStream uploaded, String originalName) {
        try {
            Path dir = root.resolve(String.valueOf(cycleId));
            Files.createDirectories(dir);
            String safe = originalName == null ? "attachment"
                    : originalName.replaceAll("[\\\\/]+", "_");
            String storedName = UUID.randomUUID() + "__" + safe;
            Path target = dir.resolve(storedName);
            Files.copy(uploaded, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredAttachment(cycleId + "/" + storedName, safe);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path resolve(String relativePath) {
        Path p = root.resolve(relativePath).normalize();
        if (!p.startsWith(root)) {
            throw new BusinessException(400, "非法附件路径");
        }
        return p;
    }

    public record StoredAttachment(String relativePath, String originalName) {
    }
}
