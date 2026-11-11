package com.carl.mall.user.controller;

import com.carl.mall.common.result.Result;
import com.carl.mall.user.service.AddressService;
import com.carl.mall.user.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务内部接口（供订单服务等微服务直连调用，不经过网关）
 */
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final AddressService addressService;

    /** 按 id 查询地址（不校验归属） */
    @GetMapping("/address/{id}")
    public Result<AddressVO> address(@PathVariable Long id) {
        return Result.ok(addressService.getById(id));
    }
}
