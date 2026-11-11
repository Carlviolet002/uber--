package com.carl.mall.user.controller;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import com.carl.mall.user.service.MessageService;
import com.carl.mall.user.vo.MessagePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * 站内消息接口（D6-C，仅 real 模式生效）。
 * mock 模式无此接口（前端自动回退 shopMock 演示数据）。
 * 网关 StripPrefix=1 剥掉 /api，网关鉴权后透传 X-User-Id。
 */
@RestController
@RequestMapping("/user/messages")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class MessageController {

    private final MessageService messageService;

    /** 分页列表 + 未读数 */
    @GetMapping
    public Result<MessagePageVO> page(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        requireUserId(userId);
        return Result.ok(messageService.page(userId, page, pageSize));
    }

    /** 未读数（商家端消息 Tab 角标） */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        requireUserId(userId);
        return Result.ok(messageService.unreadCount(userId));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                 @PathVariable Long id) {
        requireUserId(userId);
        messageService.markRead(userId, id);
        return Result.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public Result<Void> readAll(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        requireUserId(userId);
        messageService.readAll(userId);
        return Result.ok();
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
    }
}
