package com.carl.mall.common.constant;

/**
 * 公共常量
 */
public interface CommonConstant {

    /** 网关注入的用户 ID Header */
    String HEADER_USER_ID = "X-User-Id";
    /** 网关注入的角色 Header（legacy 单值，过渡期保留） */
    String HEADER_USER_ROLE = "X-User-Role";
    /** 网关注入的角色位掩码 Header（多角色，long 十进制） */
    String HEADER_USER_ROLES = "X-User-Roles";

    String ROLE_USER = "USER";
    String ROLE_SHOP = "SHOP";
    String ROLE_RIDER = "RIDER";
    String ROLE_ADMIN = "ADMIN";

    /** 派单 GEO 索引 key */
    String GEO_RIDER_ONLINE = "geo:rider:online";
    /** 默认承诺配送时长（分钟） */
    int DEFAULT_PROMISE_MINUTES = 30;
    /** 派单候选检索半径（米） */
    int DISPATCH_RADIUS_METERS = 3000;

    /** 配送方式：外卖配送（默认，需骑手派送、收配送费） */
    String DELIVERY_TYPE_DELIVERY = "DELIVERY";
    /** 配送方式：到店自提（不进入派单池、免配送费） */
    String DELIVERY_TYPE_PICKUP = "PICKUP";
}
