package com.petshop.controller;

import com.petshop.entity.Product;
import com.petshop.mapper.ProductMapper;
import com.petshop.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Blob;

@RestController
@RequestMapping("/images")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @Autowired
    private ProductMapper productMapper;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取商品主图
     * 优先从 products 表的 image_data 字段读取BLOB数据
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<byte[]> getProductImage(@PathVariable Integer productId) {
        System.out.println("[ImageController] 收到图片请求，商品ID: " + productId);
        
        Product product = productMapper.selectById(productId);
        
        if (product == null) {
            System.out.println("[ImageController] 商品不存在，ID: " + productId);
            return getDefaultImage();
        }

        // 优先从 products 表的 image_data 字段获取BLOB数据
        byte[] imageData = getBlobDataFromDatabase(productId, "image_data");
        
        if (imageData != null && imageData.length > 0) {
            System.out.println("[ImageController] 从 products 表 image_data 字段获取图片成功，大小: " + imageData.length + " bytes");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setContentLength(imageData.length);
            headers.setCacheControl("public, max-age=86400");
            return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
        }
        
        System.out.println("[ImageController] 数据库中没有图片数据，返回默认图片");
        return getDefaultImage();
    }

    /**
     * 获取商品备用图
     */
    @GetMapping("/product/{productId}/backup")
    public ResponseEntity<byte[]> getProductBackupImage(@PathVariable Integer productId) {
        byte[] imageData = imageService.getProductBackupImage(productId);
        
        if (imageData != null && imageData.length > 0) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setContentLength(imageData.length);
            headers.setCacheControl("public, max-age=86400");
            return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
        }
        
        return getDefaultImage();
    }

    /**
     * 返回默认图片
     */
    private ResponseEntity<byte[]> getDefaultImage() {
        try {
            ClassPathResource resource = new ClassPathResource("images/default-product.jpg");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    byte[] imageData = is.readAllBytes();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.IMAGE_JPEG);
                    headers.setContentLength(imageData.length);
                    return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
                }
            }
        } catch (IOException e) {
            System.out.println("[ImageController] 加载默认图片失败: " + e.getMessage());
        }
        
        // 如果没有默认图片，返回一个简单的空白图片（1x1像素的透明GIF）
        byte[] blankImage = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x21, (byte) 0xf9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3b};
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_GIF);
        headers.setContentLength(blankImage.length);
        return new ResponseEntity<>(blankImage, headers, HttpStatus.OK);
    }

    /**
     * 从URL下载图片
     */
    private byte[] downloadImage(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        
        try (InputStream inputStream = connection.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }
    
    /**
     * 使用JdbcTemplate直接从数据库读取BLOB数据
     */
    private byte[] getBlobDataFromDatabase(Integer productId, String columnName) {
        try {
            String sql = "SELECT " + columnName + " FROM products WHERE id = ?";
            System.out.println("[ImageController] 执行BLOB查询: " + sql + ", productId: " + productId);
            byte[] result = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Blob blob = rs.getBlob(columnName);
                if (blob != null) {
                    long length = blob.length();
                    System.out.println("[ImageController] BLOB不为空，长度: " + length + " bytes");
                    if (length > 0) {
                        byte[] data = blob.getBytes(1, (int) length);
                        System.out.println("[ImageController] 成功读取BLOB数据，长度: " + data.length + " bytes");
                        return data;
                    } else {
                        System.out.println("[ImageController] BLOB为空（长度为0）");
                        return null;
                    }
                } else {
                    System.out.println("[ImageController] BLOB为null");
                    return null;
                }
            }, productId);
            if (result == null) {
                System.out.println("[ImageController] getBlobDataFromDatabase 返回 null");
            } else {
                System.out.println("[ImageController] getBlobDataFromDatabase 返回数据，长度: " + result.length + " bytes");
            }
            return result;
        } catch (Exception e) {
            System.out.println("[ImageController] 从数据库读取BLOB失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 使用JdbcTemplate将图片数据保存到数据库
     */
    private void saveImageToDatabase(Integer productId, byte[] imageData, boolean isMainImage) {
        try {
            String columnName = isMainImage ? "image_data" : "img_data";
            String sql = "UPDATE products SET " + columnName + " = ? WHERE id = ?";
            jdbcTemplate.update(sql, imageData, productId);
            System.out.println("[ImageController] 图片已保存到数据库，商品ID: " + productId + ", 字段: " + columnName);
        } catch (Exception e) {
            System.out.println("[ImageController] 保存图片到数据库失败: " + e.getMessage());
        }
    }
}



