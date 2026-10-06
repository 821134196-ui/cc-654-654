package edu.scholar.service;

import edu.scholar.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/** 排名规则管理：教务在公示前锁定，锁定后任何修改都被拒绝。 */
@ApplicationScoped
public class RuleService {

    @Inject
    AuditService audit;

    public RuleSet getOrNull(String term) {
        return RuleSet.find("term", term).firstResult();
    }

    public RuleSet require(String term) {
        RuleSet rs = getOrNull(term);
        if (rs == null) {
            throw BusinessException.notFound("学期 " + term + " 的排名规则尚未配置");
        }
        return rs;
    }

    public RuleSet requireLocked(String term) {
        RuleSet rs = require(term);
        if (rs.status != RuleStatus.LOCKED) {
            throw new BusinessException("排名规则尚未锁定，公示/重算前必须先锁定规则");
        }
        return rs;
    }

    public List<RuleItem> items(Long ruleSetId) {
        return RuleItem.find("ruleSetId = ?1 order by sortOrder, id", ruleSetId).list();
    }

    @Transactional
    public RuleSet configure(User actor, String term, TieRule tieRule, Integer count,
                             Long awardAmount, Integer appealWindowDays) {
        RuleSet rs = getOrNull(term);
        if (rs == null) {
            rs = new RuleSet();
            rs.term = term;
            rs.persist();
        }
        if (rs.status == RuleStatus.LOCKED) {
            throw new BusinessException("规则已锁定（由 " + rs.lockedBy + " 于公示前锁定），不可修改");
        }
        if (tieRule != null) rs.tieRule = tieRule;
        if (count != null) {
            if (count <= 0) throw new BusinessException("获奖名额必须大于 0");
            rs.scholarshipCount = count;
        }
        if (awardAmount != null) {
            if (awardAmount <= 0) throw new BusinessException("奖金额必须大于 0");
            rs.awardAmount = awardAmount;
        }
        if (appealWindowDays != null) {
            if (appealWindowDays < 0) throw new BusinessException("公示期天数不能为负");
            rs.appealWindowDays = appealWindowDays;
        }
        audit.log(actor, "RULE_CONFIGURE", "RuleSet", String.valueOf(rs.id),
                "配置排名规则：并列规则=" + rs.tieRule + "，名额=" + rs.scholarshipCount
                        + "，奖金额(分)=" + rs.awardAmount + "，公示期=" + rs.appealWindowDays + "天");
        return rs;
    }

    @Transactional
    public RuleItem addItem(User actor, String term, String code, String name,
                            String category, double maxPoints, int sortOrder) {
        RuleSet rs = require(term);
        if (rs.status == RuleStatus.LOCKED) {
            throw new BusinessException("规则已锁定，不能再增减分项");
        }
        if (!RuleItem.CATEGORY_GPA.equals(category) && !RuleItem.CATEGORY_MATERIAL.equals(category)) {
            throw new BusinessException("分项类别必须是 GPA 或 MATERIAL");
        }
        RuleItem existing = RuleItem.find("ruleSetId = ?1 and code = ?2", rs.id, code).firstResult();
        if (existing != null) {
            throw new BusinessException("分项代码已存在: " + code);
        }
        RuleItem item = new RuleItem();
        item.ruleSetId = rs.id;
        item.code = code;
        item.name = name;
        item.category = category;
        item.maxPoints = maxPoints;
        item.sortOrder = sortOrder;
        item.persist();
        audit.log(actor, "RULE_ITEM_ADD", "RuleItem", code, "增加分项：" + name + "（" + category + "，上限 " + maxPoints + "）");
        return item;
    }

    @Transactional
    public RuleSet lock(User actor, String term) {
        RuleSet rs = require(term);
        if (rs.status == RuleStatus.LOCKED) {
            return rs;
        }
        List<RuleItem> items = items(rs.id);
        if (items.isEmpty()) {
            throw new BusinessException("规则至少要包含一个评分分项才能锁定");
        }
        boolean hasGpa = items.stream().anyMatch(i -> RuleItem.CATEGORY_GPA.equals(i.category));
        if (!hasGpa) {
            throw new BusinessException("规则必须包含 GPA 成绩分项");
        }
        rs.status = RuleStatus.LOCKED;
        rs.lockedAt = java.time.Instant.now();
        rs.lockedBy = actor.name;
        audit.log(actor, "RULE_LOCK", "RuleSet", String.valueOf(rs.id),
                "公示前锁定排名规则：并列规则=" + rs.tieRule + "，名额=" + rs.scholarshipCount
                        + "，奖金额(分)=" + rs.awardAmount + "，公示期=" + rs.appealWindowDays + "天");
        return rs;
    }
}
