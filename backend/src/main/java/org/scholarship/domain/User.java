package org.scholarship.domain;

import jakarta.persistence.*;

/** 系统用户：学生 / 辅导员 / 教务。角色鉴权在本地以 X-User-Id 头模拟。 */
@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    /** STUDENT / COUNSELOR / ADMIN */
    @Column(nullable = false)
    private String role;

    /** 学生所属班级；辅导员负责的班级；教务可空 */
    private String classRef;

    protected User() {
    }

    public User(String id, String name, String role, String classRef) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.classRef = classRef;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getClassRef() { return classRef; }
}
