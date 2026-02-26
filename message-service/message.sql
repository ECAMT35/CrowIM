-- 会话表

create table conversation
(
    id          bigint primary key comment '会话id（雪花）',
    type        tinyint  not null comment '会话类型：0=private,1=group',
    peer_a      bigint   null comment '单聊参与者a（较小user_id，type=0有效）',
    peer_b      bigint   null comment '单聊参与者b（较大user_id，type=0有效）',
    group_id    bigint   null comment '群id（type=2有效）',

    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    deleted     tinyint  not null default 0 comment '逻辑删除:0未删,1已删',

    unique key uk_private_peers (type, peer_a, peer_b, deleted),
    unique key uk_group (type, group_id, deleted),

    key idx_peer_a (peer_a),
    key idx_peer_b (peer_b)
);

-- 会话成员状态表

create table conversation_member
(
    id              bigint primary key comment '主键（雪花）',
    conversation_id bigint   not null comment '会话id',
    user_id         bigint   not null comment '成员user_id',

    role            tinyint  not null default 1 comment '1=member,2=admin,3=owner',
    mute            tinyint  not null default 0 comment '是否免打扰',

    last_read_seq   bigint   not null default 0 comment '该成员已读到的最大seq（read游标）',

    join_time       datetime not null default current_timestamp,
    update_time     datetime not null default current_timestamp on update current_timestamp,
    deleted         tinyint  not null default 0 comment '逻辑删除:0未删,1已删',

    unique key uk_conv_user (conversation_id, user_id, deleted),
    key idx_user (user_id),
    key idx_conv (conversation_id)
);


-- 统一消息表

create table message
(
    id              bigint primary key comment '消息id（雪花）',
    client_msg_id   bigint     not null comment '客户端生成消息id（发送端幂等）',
    conversation_id bigint     not null comment '会话id',
    seq             bigint     not null comment '会话内递增序号（offset）',

    sender_id       bigint     not null comment '发送者id',
    msg_type        tinyint    not null default 1 comment '1=文本，2=文件等',
    content         mediumtext not null comment '消息内容',
    send_time       bigint comment '服务端发送时间戳',

    create_time     datetime   not null default current_timestamp,
    update_time     datetime   not null default current_timestamp on update current_timestamp,
    deleted         tinyint    not null default 0 comment '逻辑删除:0未删,1已删',

    unique key uk_sender_client_msg (sender_id, client_msg_id),
    key idx_conv_seq (conversation_id, seq),
    key idx_sender (sender_id)
);