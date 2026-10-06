package org.scholarship.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 本地模拟通知：真实环境应由消息/邮件接口外发，这里只落库并在前端“通知中心”展示。
 * 材料审核结果、公示发布、异议裁定结果、发放与补退都会写入通知。
 */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Lob
    private String content;

    /** INFO / APPEAL_DECISION / REVIEW / PAYMENT */
    @Column(nullable = false, length = 24)
    private String category;

    @Column(nullable = false)
    private boolean readFlag = false;

    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(String userId, String title, String content, String category) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
