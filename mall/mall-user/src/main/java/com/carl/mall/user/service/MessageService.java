package com.carl.mall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.user.entity.Message;
import com.carl.mall.user.mapper.MessageMapper;
import com.carl.mall.user.vo.MessagePageVO;
import com.carl.mall.user.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息服务（D6-C，仅 real 模式由 Controller 暴露；消费者 real-only 写入）
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    /** 分页查询（按当前登录账号过滤，倒序）+ 未读数 */
    public MessagePageVO page(Long userId, int page, int pageSize) {
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .orderByDesc(Message::getId);
        Page<Message> result = messageMapper.selectPage(new Page<>(page, pageSize), qw);
        MessagePageVO vo = new MessagePageVO();
        vo.setRecords(result.getRecords().stream().map(this::toVO).toList());
        vo.setTotal(result.getTotal());
        vo.setUnreadCount(unreadCount(userId));
        return vo;
    }

    /** 未读数 */
    public long unreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0));
    }

    /** 标记已读（归属校验：只能操作自己的消息） */
    public void markRead(Long userId, Long messageId) {
        Message update = new Message();
        update.setIsRead(1);
        messageMapper.update(update, new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0));
    }

    /** 全部已读 */
    public void readAll(Long userId) {
        Message update = new Message();
        update.setIsRead(1);
        messageMapper.update(update, new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0));
    }

    /** 写入一条消息（MQ 消费者调用） */
    public void insert(Long receiverId, String type, String title, String content, String bizNo) {
        Message message = new Message();
        message.setReceiverId(receiverId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setBizNo(bizNo);
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private MessageVO toVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setType(message.getType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setBizNo(message.getBizNo());
        vo.setIsRead(message.getIsRead());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
