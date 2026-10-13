package com.petshop.service;

import com.petshop.entity.Address;

import java.util.List;

public interface AddressService {
    Address save(Address address);
    Address findById(Integer id);
    Address update(Address address);
    void delete(Integer id);
    List<Address> findByUserId(Integer userId);
    void setDefault(Integer id, Integer userId);
}
