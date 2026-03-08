# 消息发送异步分发改造（阶段A）

时间：2026-03-08 19:50:19
范围：消息发送链路（非关系域）

## 1. 目标
1. 发送方 ACK 不再等待成员投递循环完成。
2. 将同步扇出改为 MQ 异步分发，降低群聊发送 RT。
3. 保持现有协议不变（`SERVER_ACK_SENT` 字段不变）。

## 2. 关键改动

### 2.1 发送链路改造
- 文件：`MessageCommandService`
- 改动：
1. 保留原有校验、会话判定、seq 分配、消息落库逻辑。
2. 删除原先“查询成员并同步 `deliverToUserDevices`”的循环。
3. 落库后组装 `MessageDispatchBo` 并发布 MQ 分发任务。
4. 分发任务发布失败仅记录错误日志，不阻断 ACK 返回（由客户端 pull 兜底）。

### 2.2 新增异步分发组件
1. `MessageDispatchBo`：分发任务载体。
2. `MessageDispatchConstant`：分发交换机/队列/路由键常量。
3. `MessageDispatchMQConfig`：分发队列与死信队列配置。
4. `MessageDispatchListener`：消费分发任务。
5. `MessageDispatchService`：消费侧编排，负责按会话成员执行投递。

### 2.3 消费侧编排逻辑
- 文件：`MessageDispatchService`
- 逻辑：
1. 校验任务关键字段。
2. 查询会话 active 成员。
3. 跳过发送者本人。
4. 逐成员调用 `DeliveryService.deliverToUserDevices`。

## 3. 链路变化前后

### 改造前
`sendMessage -> 落库 -> 成员循环同步投递 -> 返回 ACK`

### 改造后
`sendMessage -> 落库 -> 发布分发任务MQ -> 返回 ACK`
`MessageDispatchListener -> MessageDispatchService -> 成员循环投递`

## 4. 影响评估
1. 发送接口 RT：预期下降，且与群规模相关性降低。
2. 实时性：主要由分发队列消费速度决定。
3. 一致性：仍为“落库强一致 + 推送最终一致（pull兜底）”。

## 5. 已知风险
1. 若分发任务发布失败，实时推送可能缺失，但消息已落库，客户端可通过 pull 补齐。
2. 未引入 outbox，暂不保证“落库与入队”严格原子。

## 6. 回滚方案
1. 将 `MessageCommandService` 恢复为同步成员循环投递。
2. 下线 `MessageDispatchListener` 消费。

## 7. 变更文件
1. `src/main/java/com/ecamt35/messageservice/service/MessageCommandService.java`
2. `src/main/java/com/ecamt35/messageservice/model/bo/MessageDispatchBo.java`
3. `src/main/java/com/ecamt35/messageservice/constant/MessageDispatchConstant.java`
4. `src/main/java/com/ecamt35/messageservice/config/MessageDispatchMQConfig.java`
5. `src/main/java/com/ecamt35/messageservice/listener/MessageDispatchListener.java`
6. `src/main/java/com/ecamt35/messageservice/service/MessageDispatchService.java`

## 8. 说明
- 本次按要求未执行编译与测试。
