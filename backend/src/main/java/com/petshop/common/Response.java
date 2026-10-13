package com.petshop.common;

import java.io.Serializable;

/**
 * 统一 API 响应结果封装
 * @param <T> 数据类型
 */
public class Response<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public Response() {
        this.timestamp = System.currentTimeMillis();
    }

    public Response(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code != null && this.code == 200;
    }
    
    /**
     * 判断是否失败
     */
    public boolean isError() {
        return this.code != null && this.code != 200;
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Response<T> success() {
        return new Response<>(200, "操作成功", null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Response<T> success(T data) {
        return new Response<>(200, "操作成功", data);
    }

    /**
     * 成功响应（自定义消息 + 数据）
     */
    public static <T> Response<T> success(String message, T data) {
        return new Response<>(200, message, data);
    }
    
    /**
     * 错误响应（默认 500）
     */
    public static <T> Response<T> error(String message) {
        return new Response<>(500, message, null);
    }

    /**
     * 错误响应（自定义状态码 + 消息）
     */
    public static <T> Response<T> error(Integer code, String message) {
        return new Response<>(code, message, null);
    }
    
    /**
     * 未授权响应（401）
     */
    public static <T> Response<T> unauthorized(String message) {
        return new Response<>(401, message, null);
    }
    
    /**
     * 禁止访问响应（403）
     */
    public static <T> Response<T> forbidden(String message) {
        return new Response<>(403, message, null);
    }
    
    /**
     * 资源不存在响应（404）
     */
    public static <T> Response<T> notFound(String message) {
        return new Response<>(404, message, null);
    }
    
    /**
     * 参数验证失败响应（400）
     */
    public static <T> Response<T> badRequest(String message) {
        return new Response<>(400, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
