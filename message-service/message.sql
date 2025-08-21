-- 1. 消息表--单聊
create table message_private_chat
(
    id          bigint primary key comment '雪花算法生成主键',
    sender_id   int     not null comment '发送者ID',
    receiver_id int     not null comment '接收者ID（单聊固定为对方用户）',
    content     text    not null comment '消息内容',
    msg_type    tinyint not null default 1 comment '消息类型：1-文本，2-图片等',
    status      tinyint not null default 1 comment '1=sent,2=delivered,3=read,4=finished',
    send_time   datetime comment '发送时间',
    push_time   datetime comment '推送成功时间',
    index idx_receiver_pushed (sender_id, receiver_id, status)
);
