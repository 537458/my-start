package com.petshop.service;

import com.petshop.entity.Product;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService {
    
    /**
     * 保存商品图片
     */
    void saveProductImage(Integer productId, MultipartFile imageFile, boolean isMainImage);
    
    /**
     * 获取商品主图
     */
    byte[] getProductMainImage(Integer productId);
    
    /**
     * 获取商品备用图
     */
    byte[] getProductBackupImage(Integer productId);
    
    /**
     * 删除商品图片
     */
    void deleteProductImage(Integer productId, boolean isMainImage);
    
    /**
     * 将网络图片下载并保存到数据库
     */
    void saveImageFromUrl(Integer productId, String imageUrl, boolean isMainImage);
}
