package com.carl.mall.common.id;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器（去中心化：worker-id 通过 {@code mall.snowflake.worker-id} 注入，默认 1）。
 * <p>
 * 用于账号/业务实体主键，避免多实例 AUTO_INCREMENT 冲突，同时保持与历史
 * AUTO_INCREMENT 旧 id（值较小）不冲突——雪花值数量级远大于旧值。
 */
@Component
public class SnowflakeIdGenerator {

    /** 自定义 epoch（2024-01-01），仅用于本项目，不与 Twitter 默认对齐 */
    private static final long EPOCH = 1_704_067_200_000L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    @Value("${mall.snowflake.worker-id:1}")
    private long workerId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 生成下一个全局唯一 ID（线程安全）。
     */
    public synchronized long nextId() {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("mall.snowflake.worker-id 超出范围 0~" + MAX_WORKER_ID);
        }
        long timestamp = currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒序列耗尽，自旋等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long ts = currentTimeMillis();
        while (ts <= lastTimestamp) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
