package com.petshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.entity.Address;
import com.petshop.mapper.AddressMapper;
import com.petshop.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;

    @Override
    public Address save(Address address) {
        address.setCreatedAt(LocalDateTime.now());
        address.setUpdatedAt(LocalDateTime.now());
        // 设置isDefault字段的默认值为false
        if (address.getIsDefault() == null) {
            address.setIsDefault(false);
        }
        addressMapper.insert(address);
        return address;
    }

    @Override
    public Address findById(Integer id) {
        return addressMapper.selectById(id);
    }

    @Override
    public Address update(Address address) {
        address.setUpdatedAt(LocalDateTime.now());
        // 设置isDefault字段的默认值为false
        if (address.getIsDefault() == null) {
            address.setIsDefault(false);
        }
        addressMapper.updateById(address);
        return address;
    }

    @Override
    public void delete(Integer id) {
        addressMapper.deleteById(id);
    }

    @Override
    public List<Address> findByUserId(Integer userId) {
        QueryWrapper<Address> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("is_default").orderByDesc("updated_at");
        return addressMapper.selectList(wrapper);
    }

    @Override
    public void setDefault(Integer id, Integer userId) {
        // 先将所有地址设置为非默认
        Address nonDefault = new Address();
        nonDefault.setIsDefault(false);
        QueryWrapper<Address> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        addressMapper.update(nonDefault, wrapper);
        
        // 再将指定地址设置为默认
        Address defaultAddress = new Address();
        defaultAddress.setId(id);
        defaultAddress.setIsDefault(true);
        defaultAddress.setUpdatedAt(LocalDateTime.now());
        addressMapper.updateById(defaultAddress);
    }
}
