package com.carl.mall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.dto.LoginResult;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.id.SnowflakeIdGenerator;
import com.carl.mall.common.jwt.JwtProperties;
import com.carl.mall.common.jwt.JwtUtil;
import com.carl.mall.user.dto.LoginDTO;
import com.carl.mall.user.dto.RegisterDTO;
import com.carl.mall.user.dto.UpdateMeDTO;
import com.carl.mall.user.entity.User;
import com.carl.mall.user.mapper.UserMapper;
import com.carl.mall.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;
    private final SnowflakeIdGenerator idGenerator;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 注册：支持多角色（USER/SHOP/RIDER 组合）。
     * 角色位掩码写入 t_user.roles；legacy 单值 t_user.role 取位掩码主角色。
     */
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        // 角色列表 → 位掩码；缺省/空按 USER；拒绝任何 ADMIN；只允许 USER/SHOP/RIDER
        List<String> rawRoles = dto.getRoles();
        if (rawRoles == null || rawRoles.isEmpty()) {
            rawRoles = List.of(CommonConstant.ROLE_USER);
        }
        long roles = 0L;
        for (String r : rawRoles) {
            String name = r.toUpperCase();
            if (CommonConstant.ROLE_ADMIN.equals(name)
                    || (!CommonConstant.ROLE_USER.equals(name)
                        && !CommonConstant.ROLE_SHOP.equals(name)
                        && !CommonConstant.ROLE_RIDER.equals(name))) {
                throw new BizException(400, "不支持的注册角色");
            }
            roles |= RoleBitmask.of(name);
        }
        if (roles == 0L) {
            roles = RoleBitmask.USER;
        }
        User user = new User();
        user.setId(idGenerator.nextId());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setRoles(roles);
        user.setRole(RoleBitmask.primary(roles));
        userMapper.insert(user);
        // 骑手：运营档案（t_rider）由派单模块在其首次上线/操作时自动建档，无需在此跨库写入
    }

    /**
     * 登录：校验密码并签发 JWT（位掩码 + legacy 双写）
     */
    public LoginResult login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        long roles = user.getRoles() == null ? RoleBitmask.of(user.getRole()) : user.getRoles();
        String token = JwtUtil.create(user.getId(), roles, user.getRole(),
                jwtProperties.getSecret(), jwtProperties.getTtlMinutes());
        return LoginResult.builder()
                .token(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .role(user.getRole())
                .roles(roles)
                .roleNames(RoleBitmask.toNames(roles))
                .build();
    }

    /**
     * 查询个人信息
     */
    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return toVO(user);
    }

    /**
     * 修改个人信息
     */
    public UserVO updateMe(Long userId, UpdateMeDTO dto) {
        User exist = userMapper.selectById(userId);
        if (exist == null) {
            throw new BizException(404, "用户不存在");
        }
        User update = new User();
        update.setId(userId);
        update.setNickname(dto.getNickname());
        update.setPhone(dto.getPhone());
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        return me(userId);
    }

    /**
     * 分页查询用户（排除 password，返回 UserVO）
     */
    public Page<UserVO> pageUsers(int page, int pageSize) {
        Page<User> userPage = userMapper.selectPage(new Page<>(page, pageSize), null);
        List<UserVO> records = userPage.getRecords().stream().map(this::toVO).toList();
        Page<UserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setRoles(user.getRoles());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
