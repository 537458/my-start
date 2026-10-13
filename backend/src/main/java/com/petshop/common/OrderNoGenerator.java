package com.petshop.common;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成器。
 * 订单号在库里有唯一约束，原来的「order + 秒级时间 + 金额」在
 * 同一秒内金额相同的两笔订单会直接撞唯一键，下单单失败。
 * 这里改为毫秒时间 + 6 位随机数，兼顾可读性与唯一性。
 */
public final class OrderNoGenerator {

    /** 毫秒级时间戳，保证整体单调递增，便于按时间排序 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 随机后缀长度 */
    private static final int RANDOM_SUFFIX_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderNoGenerator() {
    }

    /**
     * 生成订单号，格式：order + yyyyMMddHHmmssSSS + 6位随机数字
     */
    public static String generate() {
        StringBuilder builder = new StringBuilder("order")
                .append(LocalDateTime.now().format(FORMATTER));
        for (int i = 0; i < RANDOM_SUFFIX_LENGTH; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
