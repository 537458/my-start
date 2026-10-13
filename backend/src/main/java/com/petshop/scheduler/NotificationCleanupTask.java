package com.petshop.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.entity.Notification;
import com.petshop.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息清理任务。
 * 消息只增不减会让表无限膨胀，这里按两条策略裁剪：
 * 1. 已读且超过保留期的消息直接删除；
 * 2. 单个用户的消息条数超过上限时，删除最旧的超出部分。
 */
@Component
public class NotificationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupTask.class);

    /** 已读消息保留天数，超期即删除 */
    private static final int READ_RETENTION_DAYS = 90;

    /** 每个用户最多保留的消息条数 */
    private static final int MAX_PER_USER = 200;

    /** 每批删除条数，避免一次 IN 列表过长 */
    private static final int DELETE_BATCH = 500;

    /** 单用户单次最多删几批，防止极端数据把任务拖太久，剩余部分次日继续 */
    private static final int MAX_BATCHES_PER_USER = 20;

    @Autowired
    private NotificationMapper notificationMapper;

    /** 每天 03:20 执行，与优惠券日终任务错开 */
    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanup() {
        try {
            int expired = deleteExpiredRead();
            int trimmed = trimOverLimit();
            if (expired > 0 || trimmed > 0) {
                log.info("站内消息清理完成: 过期已读 {} 条, 超量 {} 条", expired, trimmed);
            } else {
                log.debug("站内消息清理: 无需清理");
            }
        } catch (Exception e) {
            // 定时任务不能抛异常，否则后续调度可能被中断
            log.error("站内消息清理任务失败", e);
        }
    }

    private int deleteExpiredRead() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(READ_RETENTION_DAYS);
        return notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getIsRead, 1)
                .lt(Notification::getCreatedAt, threshold));
    }

    private int trimOverLimit() {
        List<Integer> userIds = notificationMapper.selectUserIdsOverLimit(MAX_PER_USER);
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        int totalDeleted = 0;
        for (Integer userId : userIds) {
            int deletedForUser = 0;
            for (int batch = 0; batch < MAX_BATCHES_PER_USER; batch++) {
                // 每轮都保留最新 MAX_PER_USER 条，取它之后的一批直接删掉
                List<Integer> overflowIds = notificationMapper.selectIdsBeyond(userId, MAX_PER_USER, DELETE_BATCH);
                if (overflowIds == null || overflowIds.isEmpty()) {
                    break;
                }
                notificationMapper.deleteBatchIds(overflowIds);
                deletedForUser += overflowIds.size();
                if (overflowIds.size() < DELETE_BATCH) {
                    break;
                }
            }
            if (deletedForUser > 0) {
                log.debug("站内消息裁剪: userId={}, 删除 {} 条", userId, deletedForUser);
                totalDeleted += deletedForUser;
            }
        }
        return totalDeleted;
    }
}
