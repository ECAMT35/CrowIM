-- 会话表
create table conversation
(
    id          bigint primary key comment '会话id（雪花）',
    type        tinyint  not null comment '会话类型：0=private,1=group',
    peer_a      bigint   null comment '单聊参与者a（较小user_id，type=0有效）',
    peer_b      bigint   null comment '单聊参与者b（较大user_id，type=0有效）',
    group_id    bigint   null comment '群id（type=1有效）',

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
    id                 bigint primary key comment '主键（雪花）',
    conversation_id    bigint   not null comment '会话id',
    user_id            bigint   not null comment '成员user_id',

    role               tinyint  not null default 1 comment '1=member,2=admin,3=owner',
    mute               tinyint  not null default 0 comment '是否免打扰',

    last_read_seq      bigint   not null default 0 comment '该成员已读到的最大seq（read游标）',
    speak_banned_until bigint   not null default 0 comment '个人禁言截止时间戳（毫秒），0表示未禁言',

    join_time          datetime not null default current_timestamp,
    update_time        datetime not null default current_timestamp on update current_timestamp,
    deleted            tinyint  not null default 0 comment '逻辑删除:0未删,1已删',

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
    unique key uk_conv_seq (conversation_id, seq),
    key idx_sender (sender_id)
);

-- 好友申请表
create table friend_apply
(
    id               bigint primary key comment '申请ID（雪花）',
    applicant_id     bigint       not null comment '申请发起人',
    target_id        bigint       not null comment '申请目标用户',
    status           tinyint      not null default 0 comment '0=pending,1=accepted,2=rejected,3=canceled',
    apply_message    varchar(256) null comment '申请附言',
    decision_user_id bigint       null comment '审批人',
    decision_time    datetime     null comment '审批时间',
    create_time      datetime     not null default current_timestamp,
    update_time      datetime     not null default current_timestamp on update current_timestamp,
    deleted          tinyint      not null default 0 comment '逻辑删除:0未删,1已删',
    key idx_apply_target_status (target_id, status, deleted),
    key idx_apply_applicant_status (applicant_id, status, deleted)
);

-- 好友关系边（单向）
create table friend_link
(
    id          bigint primary key comment '关系边ID（雪花）',
    user_id     bigint   not null comment '关系发起用户',
    target_id   bigint   not null comment '关系目标用户',
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    deleted     tinyint  not null default 0 comment '逻辑删除:0未删,1已删',
    unique key uk_friend_edge (user_id, target_id, deleted),
    key idx_friend_user (user_id, deleted)
);

-- 黑名单边（单向）
create table blacklist_edge
(
    id          bigint primary key comment '黑名单边ID（雪花）',
    user_id     bigint   not null comment '拉黑用户',
    target_id   bigint   not null comment '被拉黑用户',
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    deleted     tinyint  not null default 0 comment '逻辑删除:0未删,1已删',
    unique key uk_black_edge (user_id, target_id, deleted),
    key idx_black_user (user_id, deleted)
);

-- 用户隐私设置（默认不允许陌生人私聊）
create table user_privacy_setting
(
    user_id             bigint primary key comment '用户ID',
    allow_stranger_chat tinyint  not null default 0 comment '是否允许陌生人私聊:0否,1是',
    create_time         datetime not null default current_timestamp,
    update_time         datetime not null default current_timestamp on update current_timestamp,
    deleted             tinyint  not null default 0 comment '逻辑删除:0未删,1已删'
);

-- 群资料表
create table im_group
(
    id          bigint primary key comment '群ID（雪花）',
    owner_id    bigint       not null comment '群主用户ID',
    name        varchar(64)  not null comment '群名称',
    avatar      varchar(256) null comment '群头像',
    notice      varchar(512) null comment '群公告',
    join_policy tinyint      not null default 1 comment '入群策略:0开放,1需审批',
    mute_all    tinyint      not null default 0 comment '全员禁言:0否,1是',
    create_time datetime     not null default current_timestamp,
    update_time datetime     not null default current_timestamp on update current_timestamp,
    deleted     tinyint      not null default 0 comment '逻辑删除:0未删,1已删'
);

-- 入群申请表
create table group_join_apply
(
    id               bigint primary key comment '申请ID（雪花）',
    group_id         bigint       not null comment '群ID',
    applicant_id     bigint       not null comment '申请人ID',
    status           tinyint      not null default 0 comment '0=pending,1=accepted,2=rejected,3=canceled',
    apply_message    varchar(256) null comment '申请附言',
    decision_user_id bigint       null comment '审批人',
    decision_time    datetime     null comment '审批时间',
    create_time      datetime     not null default current_timestamp,
    update_time      datetime     not null default current_timestamp on update current_timestamp,
    deleted          tinyint      not null default 0 comment '逻辑删除:0未删,1已删',
    key idx_group_apply_status (group_id, status, deleted),
    key idx_group_apply_user_status (applicant_id, status, deleted)
);
