package com.petshop.service.impl;

import com.petshop.entity.Product;
import com.petshop.mapper.ProductMapper;
import com.petshop.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class ImageServiceImpl implements ImageService {

    @Autowired
    private ProductMapper productMapper;

    @Override
    public void saveProductImage(Integer productId, MultipartFile imageFile, boolean isMainImage) {
        try {
            byte[] imageData = imageFile.getBytes();
            Product product = productMapper.selectById(productId);
            if (product != null) {
                product.setImageData(imageData);
                productMapper.updateById(product);
            }
        } catch (IOException e) {
            // 保存失败不抛出异常
        }
    }

    @Override
    public byte[] getProductMainImage(Integer productId) {
        try {
            return productMapper.getImageDataById(productId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public byte[] getProductBackupImage(Integer productId) {
        return null;
    }

    @Override
    public void deleteProductImage(Integer productId, boolean isMainImage) {
        Product product = productMapper.selectById(productId);
        if (product != null) {
            product.setImageData(null);
            productMapper.updateById(product);
        }
    }

    @Override
    public void saveImageFromUrl(Integer productId, String imageUrl, boolean isMainImage) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] imageData = inputStream.readAllBytes();
                Product product = productMapper.selectById(productId);
                if (product != null) {
                    product.setImageData(imageData);
                    productMapper.updateById(product);
                }
            }
        } catch (IOException e) {
            // 如果下载失败，不抛出异常，保持原有行为
        }
    }

    private String getMimeType(String fileName) {
        if (fileName == null) return "image/jpeg";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}