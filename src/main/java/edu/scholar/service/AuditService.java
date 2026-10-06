package edu.scholar.service;

import edu.scholar.domain.AuditLog;
import edu.scholar.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuditService {

    /** 默认加入当前业务事务：业务提交后审计一起落库。 */
    @Transactional
    public void log(User actor, String action, String targetType, String targetId, String detail) {
        write(actor, action, targetType, targetId, detail);
    }

    /**
     * 独立事务审计：用于“业务被拦截、随后要抛异常回滚”的场景
     * （如自审 403）。调用方必须本身不处于事务中，SQLite 单写者下
     * 不能在外层写事务内再开独立事务，否则 SQLITE_BUSY。
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void logIndependent(User actor, String action, String targetType, String targetId, String detail) {
        write(actor, action, targetType, targetId, detail);
    }

    private void write(User actor, String action, String targetType, String targetId, String detail) {
        AuditLog log = new AuditLog();
        log.actorUserId = actor == null ? null : actor.id;
        log.actorName = actor == null ? "system" : actor.name;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.detail = detail;
        log.persist();
    }
}
