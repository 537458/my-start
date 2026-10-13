package com.petshop.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付方式枚举。
 * 服务端只接受这里定义的编码，避免客户端传入任意字符串污染订单数据。
 */
public final class PayMethod {

    public static final String WECHAT = "WECHAT";
    public static final String ALIPAY = "ALIPAY";
    public static final String BANK = "BANK";
    public static final String COD = "COD";

    /** 编码 -> 中文名，顺序即前端的展示顺序 */
    private static final Map<String, String> LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(WECHAT, "微信支付");
        labels.put(ALIPAY, "支付宝");
        labels.put(BANK, "银行卡");
        labels.put(COD, "货到付款");
        LABELS = Collections.unmodifiableMap(labels);
    }

    private PayMethod() {
    }

    public static boolean isSupported(String code) {
        return code != null && LABELS.containsKey(code);
    }

    /** 未指定支付方式时默认微信支付，保证历史客户端仍可正常支付 */
    public static String normalize(String code) {
        return isSupported(code) ? code : WECHAT;
    }

    public static String labelOf(String code) {
        String label = LABELS.get(code);
        return label != null ? label : "微信支付";
    }

    public static java.util.Set<String> supportedCodes() {
        return LABELS.keySet();
    }

    /** 兼容旧数据：按中文名反查编码 */
    public static String codeOfLabel(String label) {
        for (Map.Entry<String, String> entry : LABELS.entrySet()) {
            if (entry.getValue().equals(label)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static java.util.List<String> labels() {
        return Arrays.asList(LABELS.values().toArray(new String[0]));
    }
}
