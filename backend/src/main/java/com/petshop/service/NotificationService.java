package com.petshop.service;

import com.petshop.entity.Notification;
import com.petshop.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内消息服务：业务动作触发一条用户可见的通知。
 * 发送失败不影响主流程，所有异常在此吞掉并记录日志。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationMapper notificationMapper;

    public void notify(Integer userId, String type, String title, String content, Integer relatedId) {
        if (userId == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setRelatedId(relatedId);
            notification.setIsRead(0);
            notification.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 通知属于附属功能，不能因为写入失败回滚订单等主流程
            log.warn("站内消息写入失败: userId={}, title={}, reason={}", userId, title, e.getMessage());
        }
    }

    /** 订单状态变化统一入口，避免各处拼标题 */
    public void notifyOrder(Integer userId, String title, String content, Integer orderId) {
        notify(userId, Notification.TYPE_ORDER, title, content, orderId);
    }
}
