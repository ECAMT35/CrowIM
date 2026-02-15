-- 用户表
create table user
(
    id          bigint unsigned  not null primary key comment '雪花算法生成主键',
    user_name   varchar(32)      not null comment '用户名',
    email       varchar(128)     not null comment '邮箱',
    password    varchar(96)      not null comment '密码',
    role_id     tinyint unsigned not null default 1 comment '角色id',
    create_time datetime         not null default current_timestamp,
    update_time datetime         not null default current_timestamp on update current_timestamp,
    deleted     tinyint unsigned not null default 0 comment '逻辑删除: 0-未删除，1-已删除',

    unique key uk_user_name (user_name),
    unique key uk_email (email)
);

-- 角色表
create table role
(
    id          tinyint unsigned not null auto_increment primary key,
    role_name   varchar(32)      not null comment '角色名称',
    create_time datetime         not null default current_timestamp,
    update_time datetime         not null default current_timestamp on update current_timestamp,
    deleted     tinyint unsigned not null default 0 comment '逻辑删除: 0-未删除，1-已删除'
);

-- 身份公钥表（IK）
create table user_identity_key
(
    id          int unsigned not null auto_increment primary key,
    user_id     int unsigned unique,
    public_key  varchar(256) not null,
    create_time datetime,
    update_time datetime,
    index dix_user_id (user_id)
);

-- 预共享公钥表（SPK）
create table user_signed_pre_key
(
    id          int unsigned not null auto_increment primary key,
    user_id     int unsigned not null,
    public_key  varchar(256) not null,
    create_time datetime,
    update_time datetime,
    index dix_user_id (user_id)
);

-- 一次性预共享公钥表（OPK）
create table user_one_time_pre_key
(
    id          int unsigned not null auto_increment primary key,
    user_id     int unsigned not null,
    public_key  varchar(256) not null,
    is_used     tinyint default 0 comment '1=已使用，0=未使用',
    create_time datetime,
    update_time datetime,
    index idx_user_unused (user_id, is_used)
);


