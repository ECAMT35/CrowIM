create table user
(
    id          int                not null auto_increment primary key,
    user_name   varchar(24) unique not null,
    email       varchar(64) unique not null,
    password    varchar(96)        not null,
    role_id     tinyint default 1,
    create_time datetime,
    update_time datetime,
    is_deleted  tinyint default 0 comment '逻辑删除, 1为是，0为否'
);

create table user_role
(
    id          tinyint     not null auto_increment primary key,
    role_name   varchar(16) not null,
    create_time datetime,
    update_time datetime
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


