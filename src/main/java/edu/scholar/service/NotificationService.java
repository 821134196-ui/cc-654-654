package edu.scholar.service;

import edu.scholar.domain.Notification;
import edu.scholar.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/** 本地模拟通知：只入库，不调用外部消息渠道。 */
@ApplicationScoped
public class NotificationService {

    public void notify(User user, String type, String title, String content) {
        notify(user.id, type, title, content);
    }

    public void notify(Long userId, String type, String title, String content) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.title = title;
        n.content = content;
        n.persist();
    }

    public List<Notification> list(Long userId, boolean unreadOnly) {
        return unreadOnly
                ? Notification.find("userId = ?1 and readFlag = false order by id desc", userId).list()
                : Notification.find("userId = ?1 order by id desc", userId).list();
    }
}
