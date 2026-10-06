package edu.scholar.domain;

import jakarta.persistence.*;

/** 用户（本地模拟认证：无密码，前端用 X-User-Id 切换身份） */
@Entity
@Table(name = "app_user")
public class User extends IdentifiedEntity {
    @Column(unique = true, nullable = false)
    public String username;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    /** 仅学生使用 */
    public String studentNo;
    public String className;

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
