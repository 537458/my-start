package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.entity.Address;
import com.petshop.service.AddressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/addresses")
public class AddressController {

    private static final Logger log = LoggerFactory.getLogger(AddressController.class);

    @Autowired
    private AddressService addressService;

    // 前端地址模型
    static class FrontendAddress {
        private int id;
        private String name;
        private String phone;
        private String province;
        private String city;
        private String district;
        private String detailAddress;
        private String address; // 完整地址，用于显示
        private boolean isDefault;

        public FrontendAddress() {
        }

        public FrontendAddress(int id, String name, String phone, String province, String city, String district, String detailAddress, boolean isDefault) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.province = province;
            this.city = city;
            this.district = district;
            this.detailAddress = detailAddress;
            this.address = province + city + district + detailAddress;
            this.isDefault = isDefault;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getProvince() {
            return province;
        }

        public void setProvince(String province) {
            this.province = province;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getDistrict() {
            return district;
        }

        public void setDistrict(String district) {
            this.district = district;
        }

        public String getDetailAddress() {
            return detailAddress;
        }

        public void setDetailAddress(String detailAddress) {
            this.detailAddress = detailAddress;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public void setDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }
    }

    @GetMapping
    public Response<List<FrontendAddress>> findByUserId(Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        List<Address> list = addressService.findByUserId(userId);
        List<FrontendAddress> frontendAddresses = new ArrayList<>();
        for (Address address : list) {
            frontendAddresses.add(new FrontendAddress(
                    address.getId(),
                    address.getName(),
                    address.getPhone(),
                    address.getProvince(),
                    address.getCity(),
                    address.getDistrict(),
                    address.getDetailAddress(),
                    address.getIsDefault() != null && address.getIsDefault()
            ));
        }
        return Response.success(frontendAddresses);
    }

    @GetMapping("/{id}")
    public Response<FrontendAddress> findById(@PathVariable Integer id, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        Address address = addressService.findById(id);
        if (address == null || !address.getUserId().equals(userId)) {
            return Response.error(404, "地址不存在");
        }
        FrontendAddress frontendAddress = new FrontendAddress(
                address.getId(),
                address.getName(),
                address.getPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetailAddress(),
                address.getIsDefault() != null && address.getIsDefault()
        );
        return Response.success(frontendAddress);
    }

    @PostMapping
    public Response<FrontendAddress> create(@RequestBody FrontendAddress frontendAddress, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        Address address = new Address();
        address.setUserId(userId);
        address.setName(frontendAddress.getName());
        address.setPhone(frontendAddress.getPhone());
        address.setProvince(frontendAddress.getProvince());
        address.setCity(frontendAddress.getCity());
        address.setDistrict(frontendAddress.getDistrict());
        address.setDetailAddress(frontendAddress.getDetailAddress());
        address.setIsDefault(frontendAddress.isDefault());
        
        Address created = addressService.save(address);
        
        if (frontendAddress.isDefault()) {
            addressService.setDefault(created.getId(), userId);
        }
        
        FrontendAddress result = new FrontendAddress(
                created.getId(),
                created.getName(),
                created.getPhone(),
                created.getProvince(),
                created.getCity(),
                created.getDistrict(),
                created.getDetailAddress(),
                created.getIsDefault() != null && created.getIsDefault()
        );
        
        log.info("地址创建成功: {}", created.getName());
        return Response.success("创建成功", result);
    }

    @PutMapping("/{id}")
    public Response<FrontendAddress> update(@PathVariable Integer id, @RequestBody FrontendAddress frontendAddress, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        Address existing = addressService.findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Response.error(404, "地址不存在");
        }
        
        Address address = new Address();
        address.setId(id);
        address.setUserId(userId);
        address.setName(frontendAddress.getName());
        address.setPhone(frontendAddress.getPhone());
        address.setProvince(frontendAddress.getProvince());
        address.setCity(frontendAddress.getCity());
        address.setDistrict(frontendAddress.getDistrict());
        address.setDetailAddress(frontendAddress.getDetailAddress());
        address.setIsDefault(frontendAddress.isDefault());
        
        if (frontendAddress.isDefault()) {
            addressService.setDefault(id, userId);
        }
        Address updated = addressService.update(address);
        
        FrontendAddress result = new FrontendAddress(
                updated.getId(),
                updated.getName(),
                updated.getPhone(),
                updated.getProvince(),
                updated.getCity(),
                updated.getDistrict(),
                updated.getDetailAddress(),
                updated.getIsDefault() != null && updated.getIsDefault()
        );
        
        log.info("地址更新成功: {}", id);
        return Response.success(result);
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Integer id, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        Address address = addressService.findById(id);
        if (address == null || !address.getUserId().equals(userId)) {
            return Response.error(404, "地址不存在");
        }
        addressService.delete(id);
        log.info("地址删除成功: {}", id);
        return Response.success("删除成功", null);
    }

    @PutMapping("/{id}/default")
    public Response<Void> setDefault(@PathVariable Integer id, Authentication authentication) {
        Integer userId = Integer.parseInt(authentication.getName());
        Address address = addressService.findById(id);
        if (address == null || !address.getUserId().equals(userId)) {
            return Response.error(404, "地址不存在");
        }
        addressService.setDefault(id, userId);
        log.info("地址设置为默认成功: {}", id);
        return Response.success("设置成功", null);
    }
}
