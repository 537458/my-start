package com.petshop.model;

import com.google.gson.annotations.SerializedName;

public class ShoppingCart {
    private Integer id;
    private Integer userId;
    private Integer productId;
    private Integer quantity;
    private String createdAt;
    private String updatedAt;
    private Product product;
    
    @SerializedName("productName")
    private String productName;
    
    @SerializedName("price")
    private Double price;
    
    @SerializedName("imageUrl")
    private String imageUrl;
    
    private boolean selected;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getProductName() {
        if (productName != null) return productName;
        if (product != null) return product.getName();
        return "商品";
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Double getPrice() {
        if (price != null) return price;
        if (product != null) return (double) product.getPrice();
        return 0.0;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
