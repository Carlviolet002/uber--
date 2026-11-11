package com.carl.mall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 注册请求
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    /** 可空，默认取 username */
    private String nickname;

    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 注册角色列表：USER 用户 / SHOP 商家 / RIDER 骑手，可多选。
     * 不允许 ADMIN 自助注册；缺省/空列表按 USER 处理。
     * 每个角色仅允许 USER/SHOP/RIDER，其余（含 ADMIN）由后端校验拦截。
     */
    private List<@Pattern(regexp = "^(USER|SHOP|RIDER)$", message = "注册角色不合法") String> roles;
}
