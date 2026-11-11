package com.carl.mall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.user.dto.AddressDTO;
import com.carl.mall.user.entity.Address;
import com.carl.mall.user.mapper.AddressMapper;
import com.carl.mall.user.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收货地址服务
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressMapper addressMapper;

    /**
     * 我的地址列表（默认地址排前面）
     */
    public List<AddressVO> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getId))
                .stream().map(this::toVO).toList();
    }

    /**
     * 新增地址
     */
    public AddressVO add(Long userId, AddressDTO dto) {
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(userId);
        }
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setLng(dto.getLng());
        address.setLat(dto.getLat());
        address.setDetail(dto.getDetail());
        address.setIsDefault(dto.getIsDefault() == null ? 0 : dto.getIsDefault());
        addressMapper.insert(address);
        return toVO(address);
    }

    /**
     * 修改地址（校验归属）
     */
    public AddressVO update(Long userId, Long id, AddressDTO dto) {
        Address exist = addressMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new BizException(404, "地址不存在");
        }
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(userId);
        }
        Address update = new Address();
        update.setId(id);
        update.setReceiverName(dto.getReceiverName());
        update.setReceiverPhone(dto.getReceiverPhone());
        update.setLng(dto.getLng());
        update.setLat(dto.getLat());
        update.setDetail(dto.getDetail());
        update.setIsDefault(dto.getIsDefault());
        update.setUpdateTime(LocalDateTime.now());
        addressMapper.updateById(update);
        return toVO(addressMapper.selectById(id));
    }

    /**
     * 删除地址（校验归属，逻辑删除）
     */
    public void delete(Long userId, Long id) {
        Address exist = addressMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new BizException(404, "地址不存在");
        }
        addressMapper.deleteById(id);
    }

    /**
     * 按 id 查询（内部接口使用，不校验归属）
     */
    public AddressVO getById(Long id) {
        Address address = addressMapper.selectById(id);
        if (address == null) {
            throw new BizException(404, "地址不存在");
        }
        return toVO(address);
    }

    /**
     * 清除该用户下其它默认地址
     */
    private void clearDefault(Long userId) {
        Address update = new Address();
        update.setIsDefault(0);
        addressMapper.update(update, new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1));
    }

    private AddressVO toVO(Address address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setUserId(address.getUserId());
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverPhone(address.getReceiverPhone());
        vo.setLng(address.getLng());
        vo.setLat(address.getLat());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
