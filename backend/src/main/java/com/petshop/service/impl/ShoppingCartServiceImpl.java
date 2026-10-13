package com.petshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.entity.ShoppingCart;
import com.petshop.mapper.ShoppingCartMapper;
import com.petshop.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Override
    public ShoppingCart add(ShoppingCart shoppingCart) {
        // 检查购物车中是否已存在该商品
        QueryWrapper<ShoppingCart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", shoppingCart.getUserId());
        wrapper.eq("product_id", shoppingCart.getProductId());
        ShoppingCart existingItem = shoppingCartMapper.selectOne(wrapper);

        if (existingItem != null) {
            // 如果已存在，增加数量
            existingItem.setQuantity(existingItem.getQuantity() + shoppingCart.getQuantity());
            existingItem.setUpdatedAt(LocalDateTime.now());
            shoppingCartMapper.updateById(existingItem);
            return existingItem;
        } else {
            // 如果不存在，添加新项
            shoppingCart.setCreatedAt(LocalDateTime.now());
            shoppingCart.setUpdatedAt(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
            return shoppingCart;
        }
    }

    @Override
    public ShoppingCart update(ShoppingCart shoppingCart) {
        shoppingCart.setUpdatedAt(LocalDateTime.now());
        shoppingCartMapper.updateById(shoppingCart);
        return shoppingCart;
    }

    @Override
    public void delete(Integer id) {
        shoppingCartMapper.deleteById(id);
    }

    @Override
    public List<ShoppingCart> findByUserId(Integer userId) {
        return shoppingCartMapper.findByUserId(userId);
    }

    @Override
    public void clear(Integer userId) {
        QueryWrapper<ShoppingCart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        shoppingCartMapper.delete(wrapper);
    }

    @Override
    public ShoppingCart findById(Integer id) {
        return shoppingCartMapper.selectById(id);
    }
}