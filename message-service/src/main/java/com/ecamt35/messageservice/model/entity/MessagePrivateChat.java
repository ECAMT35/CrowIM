package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

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
     * 客户端生成的消息ID
     */
    private Long clientMsgId;
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
     * 消息类型：1-文本，2-文件等
     */
    private Integer msgType;
    /**
     * 1=sent,2=read
     */
    private Integer status;
    /**
     * 发送时间
     */
    private Long sendTime;
    /**
     *
     */
    private Date createTime;
    /**
     *
     */
    private Date updateTime;
}
