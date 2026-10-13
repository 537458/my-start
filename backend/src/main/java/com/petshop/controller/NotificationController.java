package com.petshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.petshop.common.Response;
import com.petshop.entity.Notification;
import com.petshop.mapper.NotificationMapper;
import com.petshop.util.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 站内消息中心
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    private NotificationMapper notificationMapper;

    /** 消息列表，按页返回，客户端滑到底部再取下一页 */
    @GetMapping
    public Response<List<NotificationItem>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        // 同一秒内多条消息按 id 兜底排序，保证翻页不重不漏
        PageHelper.startPage(safePage, safeSize);
        List<Notification> list = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId));

        List<NotificationItem> result = new ArrayList<>();
        for (Notification notification : list) {
            NotificationItem item = new NotificationItem();
            item.setId(notification.getId());
            item.setType(notification.getType());
            item.setTitle(notification.getTitle());
            item.setContent(notification.getContent());
            item.setRelatedId(notification.getRelatedId());
            item.setRead(notification.getIsRead() != null && notification.getIsRead() == 1);
            item.setCreatedAt(notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : "");
            result.add(item);
        }
        return Response.success(result);
    }

    /** 未读数，用于"我的"页面红点 */
    @GetMapping("/unread-count")
    public Response<Integer> unreadCount(Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.success(0);
        }
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return Response.success(count == null ? 0 : count.intValue());
    }

    /** 单条已读 */
    @PutMapping("/{id}/read")
    public Response<Void> read(@PathVariable Integer id, Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
        return Response.success("已读", null);
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Response<Void> readAll(Authentication authentication) {
        Integer userId = AuthUtils.resolveUserId(authentication);
        if (userId == null) {
            return Response.error(401, "请先登录");
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        log.info("消息全部已读: userId={}", userId);
        return Response.success("全部已读", null);
    }

    public static class NotificationItem {
        private Integer id;
        private String type;
        private String title;
        private String content;
        private Integer relatedId;
        private Boolean read;
        private String createdAt;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Integer getRelatedId() {
            return relatedId;
        }

        public void setRelatedId(Integer relatedId) {
            this.relatedId = relatedId;
        }

        public Boolean getRead() {
            return read;
        }

        public void setRead(Boolean read) {
            this.read = read;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
