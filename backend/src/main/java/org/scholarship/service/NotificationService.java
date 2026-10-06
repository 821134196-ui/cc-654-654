package org.scholarship.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.scholarship.domain.Notification;

import java.util.List;

/** 本地模拟通知：不做真实外发，仅落库，前端通知中心可查。 */
@ApplicationScoped
public class NotificationService {

    @Inject
    EntityManager em;

    @Transactional
    public void push(String userId, String title, String content, String category) {
        em.persist(new Notification(userId, title, content, category));
    }

    public List<Notification> listMine(String userId) {
        return em.createQuery(
                        "select n from Notification n where n.userId = :u order by n.createdAt desc",
                        Notification.class)
                .setParameter("u", userId)
                .getResultList();
    }

    @Transactional
    public void markRead(Long id, String userId) {
        Notification n = em.find(Notification.class, id);
        if (n != null && n.getUserId().equals(userId)) {
            n.setReadFlag(true);
        }
    }
}
