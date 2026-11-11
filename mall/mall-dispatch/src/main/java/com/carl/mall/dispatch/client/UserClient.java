package com.carl.mall.dispatch.client;

import com.carl.mall.common.dto.LoginResult;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.discovery.ServiceEndpointResolver;
import com.carl.mall.common.resilience.ResilienceTemplate;
import com.carl.mall.common.result.Result;
import com.carl.mall.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 用户服务客户端（RestClient 直连 mall.services.user-url，不经网关）。
 * 调用经共享 {@link ResilienceTemplate} 包裹：超时 + 熔断 + 退避重试；
 * 用户服务不可用时降级为抛出"请稍后重试"，避免把基础设施故障误判为密码错误。
 */
@Slf4j
@Component
public class UserClient {

    private final SimpleClientHttpRequestFactory factory;
    private final String userUrl;
    private final ServiceEndpointResolver resolver;
    private final ResilienceTemplate resilience;

    public UserClient(@Value("${mall.services.user-url:http://localhost:8081}") String userUrl,
                      ServiceEndpointResolver resolver,
                      ResilienceTemplate resilience) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1000);
        f.setReadTimeout(2000);
        this.factory = f;
        this.userUrl = userUrl;
        this.resolver = resolver;
        this.resilience = resilience;
    }

    /** OP-1：每次调用按服务名从 Nacos 解析实例，解析失败回退直连 userUrl。 */
    private RestClient rc() {
        return RestClient.builder()
                .baseUrl(resolver.resolve("mall-user", userUrl))
                .requestFactory(factory)
                .build();
    }

    /**
     * 骑手登录校验
     *
     * @return 登录结果；返回 null 表示账号不存在/密码错误（业务失败，不重试）
     */
    public LoginResult login(String username, String password) {
        return resilience.run("dispatchUser.login", () -> {
            Result<LoginResult> result = rc().post()
                    .uri("/user/login")
                    .body(Map.of("username", username, "password", password))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<LoginResult>>() {
                    });
            if (result == null || result.getData() == null) {
                return null;
            }
            if (result.getCode() != ResultCode.SUCCESS) {
                throw new BizException(401, result.getMsg() == null ? "用户名或密码错误" : result.getMsg());
            }
            return result.getData();
        }, () -> {
            throw new BizException("用户服务不可用，请稍后重试");
        });
    }
}
