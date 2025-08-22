package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * (MessagePrivateChat)实体类
 *
 * @author ECAMT
 * @since 2025-08-21 19:30:34
 */
@TableName("message_private_chat")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessagePrivateChat {
    /**
     * 雪花算法生成主键
     */
    private Long id;
    /**
     * 发送者ID
     */
    private Long senderId;
    /**
     * 接收者ID（单聊固定为对方用户）
     */
    private Long receiverId;
    /**
     * 消息内容
     */
    private String content;
    /**
     * 消息类型：1-文本，2-图片等
     */
    private Integer msgType;
    /**
     * 1=sent,2=delivered,3=read,4=finished
     */
    private Integer status;
    /**
     * 发送时间
     */
    private Long sendTime;
    /**
     * 推送成功时间
     */
    private Long pushTime;
}
