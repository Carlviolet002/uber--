package com.carl.mall.dispatch.pending;

import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.dispatch.client.OrderClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * mock 模式待派订单来源：直接轮询订单服务 HTTP 接口
 */
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "true", matchIfMissing = true)
public class HttpPollingPendingOrderSource implements PendingOrderSource {

    private final OrderClient orderClient;

    public HttpPollingPendingOrderSource(OrderClient orderClient) {
        this.orderClient = orderClient;
    }

    @Override
    public List<OrderTask> poll() {
        return orderClient.pending();
    }
}
