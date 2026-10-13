package com.petshop.service;

import com.petshop.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    ShoppingCart add(ShoppingCart shoppingCart);
    ShoppingCart update(ShoppingCart shoppingCart);
    void delete(Integer id);
    List<ShoppingCart> findByUserId(Integer userId);
    void clear(Integer userId);
    ShoppingCart findById(Integer id);
}