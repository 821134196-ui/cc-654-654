package edu.scholar.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * 所有实体的主键基类。
 * 使用 SQLite 原生 INTEGER PRIMARY KEY AUTOINCREMENT（IDENTITY），
 * 避免 Hibernate 默认 TABLE 序列生成器在独立事务中申请第二个连接，
 * 与 SQLite 单写者连接池发生死锁（acquisition timeout / SQLITE_BUSY）。
 */
@MappedSuperclass
public abstract class IdentifiedEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
}
