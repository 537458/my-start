package com.petshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.entity.Category;
import com.petshop.mapper.CategoryMapper;
import com.petshop.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public Category save(Category category) {
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public Category findById(Integer id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public Category update(Category category) {
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.updateById(category);
        return category;
    }

    @Override
    public void delete(Integer id) {
        categoryMapper.deleteById(id);
    }

    @Override
    public List<Category> findAll() {
        // 确保返回与首页一致的8个分类，并且顺序正确
        List<Category> categories = new ArrayList<>();
        
        // 定义首页的8个分类
        String[] categoryNames = {"宠物食品", "玩具用品", "窝垫床品", "清洁护理", "医疗健康", "服饰配件", "宠物服务", "更多分类"};
        
        for (int i = 0; i < categoryNames.length; i++) {
            Category category = new Category();
            category.setId(i + 1);
            category.setName(categoryNames[i]);
            category.setSortOrder(i + 1);
            categories.add(category);
        }
        
        return categories;
    }
}
