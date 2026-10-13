package com.petshop.model;

import java.io.Serializable;
import java.util.List;

/**
 * 订单模型类
 */
public class Order implements Serializable {
    private int id;
    private String orderNo;
    private int userId;
    private float totalPrice;
    private String status;
    private int addressId;
    private String address;
    private String receiver;
    private String receiverPhone;
    private String paymentType;
    private String paymentTime;
    private String deliveryTime;
    private String receiveTime;
    private List<OrderItem> orderItems;
    private String createTime;
    private String updateTime;

    // 构造方法
    public Order() {
    }

    public Order(int id, String orderNo, int userId, float totalPrice, String status,
                 int addressId, String address, String receiver, String receiverPhone) {
        this.id = id;
        this.orderNo = orderNo;
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.status = status;
        this.addressId = addressId;
        this.address = address;
        this.receiver = receiver;
        this.receiverPhone = receiverPhone;
    }

    // getter和setter方法
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public float getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(float totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAddressId() {
        return addressId;
    }

    public void setAddressId(int addressId) {
        this.addressId = addressId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getPaymentTime() {
        return paymentTime;
    }

    public void setPaymentTime(String paymentTime) {
        this.paymentTime = paymentTime;
    }

    public String getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(String deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public String getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(String receiveTime) {
        this.receiveTime = receiveTime;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", orderNo='" + orderNo + '\'' +
                ", totalPrice=" + totalPrice +
                ", status='" + status + '\'' +
                ", receiver='" + receiver + '\'' +
                ", receiverPhone='" + receiverPhone + '\'' +
                ", orderItems=" + orderItems +
                '}';
    }

    /**
     * 订单项模型类
     */
    public static class OrderItem implements Serializable {
        private int id;
        private int orderId;
        private int productId;
        private String productName;
        private String productImage;
        private float productPrice;
        private int quantity;

        // 构造方法
        public OrderItem() {
        }

        public OrderItem(int id, int orderId, int productId, String productName,
                         String productImage, float productPrice, int quantity) {
            this.id = id;
            this.orderId = orderId;
            this.productId = productId;
            this.productName = productName;
            this.productImage = productImage;
            this.productPrice = productPrice;
            this.quantity = quantity;
        }

        // getter和setter方法
        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getOrderId() {
            return orderId;
        }

        public void setOrderId(int orderId) {
            this.orderId = orderId;
        }

        public int getProductId() {
            return productId;
        }

        public void setProductId(int productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getProductImage() {
            return productImage;
        }

        public void setProductImage(String productImage) {
            this.productImage = productImage;
        }

        public float getProductPrice() {
            return productPrice;
        }

        public void setProductPrice(float productPrice) {
            this.productPrice = productPrice;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        @Override
        public String toString() {
            return "OrderItem{" +
                    "id=" + id +
                    ", productName='" + productName + '\'' +
                    ", productPrice=" + productPrice +
                    ", quantity=" + quantity +
                    '}';
        }
    }
}