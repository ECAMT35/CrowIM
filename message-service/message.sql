-- 1. 消息表--单聊
create table message_private_chat
(
    id            bigint primary key comment '雪花算法生成主键',
    client_msg_id bigint     not null comment '客户端生成的消息ID',
    sender_id     bigint     not null comment '发送者ID',
    receiver_id   bigint     not null comment '接收者ID',
    content       mediumtext not null comment '消息内容',
    msg_type      tinyint    not null default 1 comment '消息类型：1-文本，2-文件等',
    status        tinyint    not null default 1 comment '1=sent,2=read',
    send_time     bigint comment '服务端生成的发送时间戳',
    create_time   datetime   not null default current_timestamp,
    update_time   datetime   not null default current_timestamp on update current_timestamp,

    unique key uk_client_msg_id (client_msg_id),
    key idx_receiver_status (receiver_id, sender_id, status)
);

-- 2. 群聊
-- 群组基本信息表
create table chat_group
(
    id           bigint primary key comment '群组ID（雪花算法生成）',
    creator_id   bigint      not null comment '创建者ID',
    group_name   varchar(64) not null comment '群组名称',
    create_time  bigint      not null comment '创建时间',
    member_count int         not null default 0 comment '当前成员数',
    index idx_creator (creator_id)
);
-- 群聊消息主表
create table message_group_chat
(
    id           bigint primary key comment '雪花算法生成主键',
    sender_id    bigint  not null comment '发送者ID',
    group_id     bigint  not null comment '群组ID（多聊的目标群组）',
    content      text    not null comment '消息内容',
    msg_type     tinyint not null default 1 comment '消息类型：1-文本，2-图片等',
    send_time    bigint comment '发送时间',
    push_time    bigint comment '推送成功时间',
    quote_msg_id bigint  null comment '引用的消息ID，用于消息回复功能',
    is_at        tinyint not null default 1 comment '是否@：1-否，2-部分用户，3-全体成员',
    index idx_group_send (group_id, send_time) comment '查询群聊历史消息',
    index idx_sender_group (sender_id, group_id) comment '查询用户在群内发送的消息'
);
-- 群消息@部分用户关联表
create table message_group_chat_at
(
    id         bigint primary key comment '雪花算法主键',
    msg_id     bigint not null comment '消息ID',
    at_user_id bigint not null comment '被@的用户ID',
    unique key uk_msg_user (msg_id, at_user_id) comment '避免重复@同一用户'
) comment '群消息@用户关联表';