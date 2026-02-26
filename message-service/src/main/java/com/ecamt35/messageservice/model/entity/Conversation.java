package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * (Conversation)实体类
 *
 * @author ECAMT35
 * @since 2026-02-24 01:18:45
 */
@TableName("conversation")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Conversation {
    /**
     * 会话id（雪花）
     */
    private Long id;
    /**
     * 会话类型：1=private,2=group
     */
    private Integer type;
    /**
     * 单聊参与者a（较小user_id，type=1有效）
     */
    private Long peerA;
    /**
     * 单聊参与者b（较大user_id，type=1有效）
     */
    private Long peerB;
    /**
     * 群id（type=2有效）
     */
    private Long groupId;

    private Date createTime;

    private Date updateTime;
    /**
     * 逻辑删除:0未删,1已删
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
