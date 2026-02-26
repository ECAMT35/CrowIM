package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * (ConversationMember)实体类
 *
 * @author ECAMT35
 * @since 2026-02-24 01:18:57
 */
@TableName("conversation_member")
@Data
public class ConversationMember {
    /**
     * 主键（雪花）
     */
    private Long id;
    /**
     * 会话id
     */
    private Long conversationId;
    /**
     * 成员user_id
     */
    private Long userId;
    /**
     * 1=member,2=admin,3=owner
     */
    private Integer role;
    /**
     * 是否免打扰
     */
    private Integer mute;
    /**
     * 该成员已读到的最大seq（read游标）
     */
    private Long lastReadSeq;

    private Date joinTime;

    private Date updateTime;
    /**
     * 逻辑删除:0未删,1已删
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
