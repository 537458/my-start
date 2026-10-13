package com.petshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.entity.Product;
import com.petshop.mapper.ProductMapper;
import com.petshop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品服务实现类
 */
@Service
public class ProductServiceImpl implements ProductService {
    
    @Autowired
    private ProductMapper productMapper;
    
    @Override
    public Product save(Product product) {
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);
        return product;
    }
    
    @Override
    public Product findById(Integer id) {
        return productMapper.selectById(id);
    }
    
    @Override
    public Product update(Product product) {
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }
    
    @Override
    public void delete(Integer id) {
        productMapper.deleteById(id);
    }
    
    @Override
    public List<Product> findAll() {
        return productMapper.selectList(null);
    }
    
    @Override
    public List<Product> findByCategoryId(Integer categoryId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getCategoryId, categoryId);
        return productMapper.selectList(wrapper);
    }
    
    @Override
    public List<Product> search(String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Product::getName, keyword)
               .or()
               .like(Product::getDescription, keyword);
        return productMapper.selectList(wrapper);
    }
}
