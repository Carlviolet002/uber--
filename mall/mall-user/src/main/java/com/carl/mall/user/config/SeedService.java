package com.carl.mall.user.config;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.id.SnowflakeIdGenerator;
import com.carl.mall.user.entity.Address;
import com.carl.mall.user.entity.User;
import com.carl.mall.user.mapper.AddressMapper;
import com.carl.mall.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 种子数据播种服务（mock / real 两模式共用，逻辑与改造前 DataInitializer 完全一致）。
 * 幂等：selectCount==0 才插入。4 个账号 + 1 条默认地址。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedService {

    private final UserMapper userMapper;
    private final AddressMapper addressMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public void seed() {
        if (userMapper.selectCount(null) == 0) {
            // 雪花 id，顺序不固定；用返回值关联默认地址
            insertUser("admin", "admin123", "管理员", CommonConstant.ROLE_ADMIN);
            Long user1Id = insertUser("user1", "user123", "小明", CommonConstant.ROLE_USER);
            insertUser("shop1", "shop123", "张姐麻辣烫", CommonConstant.ROLE_SHOP);
            insertUser("rider1", "rider123", "骑手小王", CommonConstant.ROLE_RIDER);
            log.info("[SeedService] 用户初始化完成：admin/admin123(ADMIN)、user1/user123(USER)、"
                    + "shop1/shop123(SHOP)、rider1/rider123(RIDER)");
            if (user1Id != null) {
                Address address = new Address();
                address.setUserId(user1Id);
                address.setReceiverName("小明");
                address.setReceiverPhone("13800138000");
                address.setLng(113.66);
                address.setLat(34.76);
                address.setDetail("郑州大学主校区 3 号楼");
                address.setIsDefault(1);
                addressMapper.insert(address);
                log.info("[SeedService] 已为 user1(id={}) 初始化默认地址", user1Id);
            }
        }
    }

    private Long insertUser(String username, String rawPassword, String nickname, String role) {
        User user = new User();
        user.setId(idGenerator.nextId());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setPhone("13800138000");
        long roles = RoleBitmask.of(role);
        user.setRoles(roles);
        user.setRole(RoleBitmask.primary(roles));
        userMapper.insert(user);
        return user.getId();
    }
}
