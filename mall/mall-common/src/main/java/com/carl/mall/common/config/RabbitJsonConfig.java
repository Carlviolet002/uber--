package com.carl.mall.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ JSON 序列化统一配置（联调预检发现的隐藏缺陷修复）。
 *
 * <p>问题：各服务从未配置 MessageConverter，RabbitTemplate 默认走 SimpleMessageConverter，
 * 它只能序列化 Serializable 对象。而本项目 MQ 载荷大量使用普通 DTO（如 OrderTask，未实现
 * Serializable）与 Map，real 模式首次发布即会抛序列化异常；消费者端（DispatchMqConsumer、
 * RiderAssignedConsumer 等）均按 Map/JSON 约定接收，需要 JSON 载荷。</p>
 *
 * <p>方案：在 mall-common 声明唯一的 Jackson2JsonMessageConverter Bean（复用 Spring Boot
 * 自动配置的 ObjectMapper，LocalDateTime 按 ISO 字符串序列化），Spring Boot 的
 * RabbitTemplateConfigurer 与 SimpleRabbitListenerContainerFactoryConfigurer 会自动将
 * 唯一的 MessageConverter Bean 注入模板与监听容器，全项目统一生效。</p>
 *
 * <p>生效条件：类路径存在 RabbitTemplate（即引入 spring-boot-starter-amqp 的服务），
 * gateway（webflux，无 amqp 依赖）不受影响；mock 模式不发消息，同样无副作用。</p>
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
public class RabbitJsonConfig {

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
