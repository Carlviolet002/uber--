package com.carl.mall.common.geo;

/**
 * 地理查询结果
 */
public record GeoMember(String member, double lng, double lat, double distanceMeters) {
}
