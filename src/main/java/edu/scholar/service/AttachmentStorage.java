package edu.scholar.service;

import edu.scholar.domain.Material;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** 附件本地保存：存到 app.storage.dir，返回相对路径入库。 */
@ApplicationScoped
public class AttachmentStorage {

    @ConfigProperty(name = "app.storage.dir", defaultValue = "data/attachments")
    String storageDir;

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(Paths.get(storageDir));
    }

    /** 保存附件，返回 [存储相对路径, 原始文件名] */
    public String[] save(byte[] content, String originalName) {
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Path target = Paths.get(storageDir, stored).toAbsolutePath().normalize();
            if (!target.startsWith(Paths.get(storageDir).toAbsolutePath().normalize())) {
                throw new BusinessException("非法文件名");
            }
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return new String[]{stored, originalName};
        } catch (IOException e) {
            throw new BusinessException("附件保存失败: " + e.getMessage());
        }
    }

    public Path resolve(Material m) {
        return Paths.get(storageDir, m.attachmentPath).toAbsolutePath().normalize();
    }
}
