package com.carl.mall.common.util;

/**
 * 距离计算工具（Haversine 球面距离，毕设明确声明用直线距离代替路网距离）
 */
public final class DistanceUtil {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private DistanceUtil() {
    }

    public static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /** 公里转米 */
    public static double kmToMeters(double km) {
        return km * 1000;
    }
}
