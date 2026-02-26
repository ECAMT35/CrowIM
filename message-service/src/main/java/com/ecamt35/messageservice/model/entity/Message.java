package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * (Message)实体类
 *
 * @author ECAMT35
 * @since 2026-02-24 01:19:10
 */
@TableName("message")
@Data
public class Message {
    /**
     * 消息id（雪花）
     */
    private Long id;
    /**
     * 客户端生成消息id（发送端幂等）
     */
    private Long clientMsgId;
    /**
     * 会话id
     */
    private Long conversationId;
    /**
     * 会话内递增序号（offset）
     */
    private Long seq;
    /**
     * 发送者id
     */
    private Long senderId;
    /**
     * 1=文本，2=文件等
     */
    private Integer msgType;
    /**
     * 消息内容
     */
    private String content;
    /**
     * 服务端发送时间戳
     */
    private Long sendTime;

    private Date createTime;

    private Date updateTime;
    /**
     * 逻辑删除:0未删,1已删
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
