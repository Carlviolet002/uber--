package com.carl.mall.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.dto.LoginResult;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import com.carl.mall.user.dto.AddressDTO;
import com.carl.mall.user.dto.LoginDTO;
import com.carl.mall.user.dto.RegisterDTO;
import com.carl.mall.user.dto.UpdateMeDTO;
import com.carl.mall.user.service.AddressService;
import com.carl.mall.user.service.UserService;
import com.carl.mall.user.vo.AddressVO;
import com.carl.mall.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户服务对外接口（网关 StripPrefix=1 剥掉 /api，网关鉴权）
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    /** 注册（无鉴权） */
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    /** 登录（无鉴权） */
    @PostMapping("/login")
    public Result<LoginResult> login(@Validated @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    /** 个人信息 */
    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader(value = CommonConstant.HEADER_USER_ID, required = false) Long userId) {
        return Result.ok(userService.me(requireUserId(userId)));
    }

    /** 修改个人信息 */
    @PutMapping("/me")
    public Result<UserVO> updateMe(@RequestHeader(value = CommonConstant.HEADER_USER_ID, required = false) Long userId,
                                   @RequestBody UpdateMeDTO dto) {
        return Result.ok(userService.updateMe(requireUserId(userId), dto));
    }

    /** 用户分页列表（仅 ADMIN） */
    @GetMapping("/list")
    public Result<Page<UserVO>> list(@RequestHeader(value = CommonConstant.HEADER_USER_ROLES, required = false) long roles,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        if (!RoleBitmask.has(roles, RoleBitmask.ADMIN)) {
            throw new BizException(403, "无权限");
        }
        return Result.ok(userService.pageUsers(page, pageSize));
    }

    /** 新增地址 */
    @PostMapping("/address")
    public Result<AddressVO> addAddress(@RequestHeader(value = CommonConstant.HEADER_USER_ID, required = false) Long userId,
                                        @Validated @RequestBody AddressDTO dto) {
        return Result.ok(addressService.add(requireUserId(userId), dto));
    }

    /** 地址列表 */
    @GetMapping("/address")
    public Result<List<AddressVO>> listAddress(@RequestHeader(value = CommonConstant.HEADER_USER_ID, required = false) Long userId) {
        return Result.ok(addressService.list(requireUserId(userId)));
    }

    /** 修改地址 */
    @PutMapping("/address/{id}")
    public Result<AddressVO> updateAddress(@RequestHeader(value = CommonConstant.HEADER_USER_ID, required = false) Long userId,
                                           @PathVariable Long id,
                                           @Validated @RequestBody AddressDTO dto) {
        return Result.ok(addressService.update(requireUserId(userId), id, dto));
    }

    /** 删除地址 */
    @DeleteMapping("/address/{id}")
    public Result<Void> deleteAddress(@RequestHeader(value = CommonConstant.HEADER_USER_ID, required = false) Long userId,
                                      @PathVariable Long id) {
        addressService.delete(requireUserId(userId), id);
        return Result.ok();
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
