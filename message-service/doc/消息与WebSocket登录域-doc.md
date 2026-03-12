# 消息与 WebSocket 登录域文档（主）

更新时间：2026-03-12 15:08

## 1. 范围

- WebSocket 登录/连接管理
- 消息发送、异步分发、在线投递
- 消息拉取、已读游标、会话摘要

## 2. 当前链路总览（以代码实现为准）

### 2.1 WebSocket 登录链路

1. 客户端建立连接并完成 WebSocket 握手后，`WebSocketFrameHandler.userEventTriggered` 立即返回当前节点名文本（`nodeName`）。
2. 未注册状态下，`handleRegistration` 解析 `data.userId/deviceId/requestId`。
3. 调用 `UserChannelRegistry.registerUserAsync`：
    - eventLoop 写入 channel attrs
    - 虚拟线程获取分布式锁并写 Redis 路由 `ws:online:{userId}:{deviceId}`
    - 本地缓存 `deviceChannels` 建立映射
    - 必要时通知旧节点踢线
4. 注册成功返回 `packetType=205`（`SERVER_REGISTER_ACK`）；注册失败返回 `packetType=206`（`SERVER_REGISTER_NACK`）并主动关闭连接。

### 2.2 消息发送与 ACK 链路

1. 客户端发送 `packetType=100`（`CLIENT_REQUEST_SENT`）。
2. `ClientSendMessageHandler` 做基础校验（含空 `packet/data` 防御）并调用 `MessageCommandService.sendMessage`。
3. `MessageCommandService` 执行：
    - 幂等检查（`senderId + clientMsgId`）
    - 单聊会话校验/创建，或群成员与禁言校验
    - 分配会话内 `seq`
    - 消息落库
    - 发布 `MessageDispatchBo` 到异步分发队列
4. 发送端收到 `packetType=201`（`SERVER_ACK_SENT`）。

说明：`SERVER_ACK_SENT` 语义是“消息已持久化并已尝试发布分发任务”，不等待全员在线投递完成。

### 2.3 异步分发与在线投递链路

1. `MessageDispatchListener` 消费分发队列。
2. `MessageDispatchService.handleDispatch` 查询会话 active 成员，跳过发送者。
3. 按成员调用 `DeliveryService.deliverToUserDevices`：
    - 每个成员投递独立 `try-catch`，单成员失败不影响其他成员。
4. `DeliveryService`：
    - 读取 `user:devices:{userId}` 设备集合
    - 读取 `ws:online:{userId}:{deviceId}` 的 `node/sessionId`
    - 本节点直接写 channel
    - 跨节点转发到 `websocket-message-{node}`，由 `MessagePushListener` 消费后继续投递

### 2.4 拉取/已读/摘要链路

- `CLIENT_PULL_MESSAGES(103)` -> `MessageService.pullMessages`
    - 按 `seq` 分页拉取
    - `upperBoundSeq` 支持 Redis 上界 + DB 纠偏
- `CLIENT_ACK_READ(101)` -> `CursorService.advanceRead`
    - Redis 优先推进 read 游标，DB 兜底
- `CLIENT_PULL_SUMMARY(102)` -> `SummaryService.buildSummary`
    - 调用 `CursorService.batchGetLastSeq/batchGetRead`
    - Redis 批量读取 + DB 批量兜底，避免 N+1

## 3. null

## 4. 一致性与失败语义

- 写入一致性：消息落库是强一致前提，ACK 基于落库成功返回。
- 推送一致性：在线推送是最终一致，客户端需依赖 pull 做补齐。
- 分发失败处理：单成员投递失败仅影响该成员当次实时推送，不影响其他成员。
- 已知保留项：尚未引入 outbox，仍存在“落库成功但分发任务发布失败”的窗口（可由 pull 补偿）。

## 5. 接口 JSON 用例

### 5.1 登录注册（WebSocket 首包）

请求示例：

```json
{
  "data": {
    "requestId": "reg-20260312-001",
    "userId": 2022732862670901248,
    "deviceId": "111"
  }
}
```

连接建立成功后的首条服务端响应（文本帧，握手完成即返回）：

```json
"node2"
```

注册成功响应 `SERVER_REGISTER_ACK(205)`：

```json
{
  "packetType": 205,
  "data": {
    "requestId": "reg-20260312-001",
    "userId": 2022732862670901248,
    "deviceId": "111",
    "nodeName": "node2",
    "serverTimestamp": 1773000000000,
    "code": 200,
    "message": "register success"
  }
}
```

注册失败响应 `SERVER_REGISTER_NACK(206)`：

```json
{
  "packetType": 206,
  "data": {
    "requestId": "reg-20260312-001",
    "userId": 2022732862670901248,
    "deviceId": "111",
    "nodeName": "node2",
    "serverTimestamp": 1773000000001,
    "code": 1001,
    "message": "register failed",
    "errorDetail": "Device is being registered by another node, please retry later"
  }
}
```

参数说明：

- `data.requestId`：客户端请求 ID，可用于幂等去重与回包关联，建议全局唯一。
- `data.userId`：当前登录用户 ID。
- `data.deviceId`：设备唯一标识。
- `serverTimestamp`：服务端生成时间戳（毫秒）。
- `code`：状态码，`200` 成功；`400` 参数问题；`1001` 业务处理失败。

### 5.2 发送消息 `CLIENT_REQUEST_SENT(100)`

请求示例（单聊）：

```json
{
  "packetType": 100,
  "data": {
    "clientMsgId": 91234567001,
    "chatType": 0,
    "msgType": 1,
    "receiverId": 10002,
    "content": "hello"
  }
}
```

请求示例（群聊）：

```json
{
  "packetType": 100,
  "data": {
    "clientMsgId": 91234567002,
    "chatType": 1,
    "msgType": 1,
    "conversationId": 30001,
    "content": "hi group"
  }
}
```

ACK 响应 `SERVER_ACK_SENT(201)`：

```json
{
  "packetType": 201,
  "data": {
    "messageId": 193384848484848,
    "sendTime": 1772930000000,
    "clientMsgId": 91234567001
  }
}
```

参数说明：

- `clientMsgId`：客户端生成，发送幂等键。
- `chatType`：`0` 单聊，`1` 群聊。
- `msgType`：消息类型（示例 `1` 文本）。
- `receiverId`：单聊必填。
- `conversationId`：群聊必填。
- `content`：消息内容。

### 5.3 服务端下发消息 `SERVER_REQUEST_SENT(200)`

推送示例：

```json
{
  "packetType": 200,
  "data": {
    "targetUserId": 10002,
    "message": "hello",
    "chatType": 0,
    "messageType": 1,
    "senderId": 10001,
    "messageId": 193384848484848,
    "sendTime": 1772930000000,
    "receiverDeviceId": null,
    "conversationId": 30001,
    "groupId": null,
    "seq": 128
  }
}
```

参数说明：

- `targetUserId`：接收人。
- `message`：消息体。
- `chatType`：`0` 单聊，`1` 群聊。
- `messageType`：消息类型。
- `senderId`：发送人。
- `messageId`：服务端消息 ID。
- `sendTime`：服务端发送时间戳。
- `receiverDeviceId`：单设备定向投递时携带。
- `conversationId`：会话 ID。
- `groupId`：群 ID（群消息时有值）。
- `seq`：会话内顺序号。

### 5.4 已读回执 `CLIENT_ACK_READ(101)`

请求示例：

```json
{
  "packetType": 101,
  "data": {
    "conversationId": 30001,
    "readSeq": 128
  }
}
```

响应示例 `SERVER_ACK_READ(202)`：

```json
{
  "packetType": 202,
  "data": {
    "conversationId": 30001,
    "readSeq": 128
  }
}
```

参数说明：

- `conversationId`：会话 ID。
- `readSeq`：客户端确认已读到的序号。

### 5.5 拉取摘要 `CLIENT_PULL_SUMMARY(102)`

请求示例：

```json
{
  "packetType": 102,
  "data": {}
}
```

响应示例 `SERVER_SUMMARY(203)`：

```json
{
  "packetType": 203,
  "data": {
    "30001": {
      "lastSeq": 128,
      "readSeq": 120,
      "unread": 8
    }
  }
}
```

参数说明：

- 返回 `data` 为 `convId -> {lastSeq, readSeq, unread}` 映射。

### 5.6 拉取消息 `CLIENT_PULL_MESSAGES(103)`

请求示例：

```json
{
  "packetType": 103,
  "data": {
    "conversationId": 30001,
    "afterSeq": 120,
    "upperBoundSeq": 128,
    "limit": 50
  }
}
```

响应示例 `SERVER_MESSAGES(204)`：

```json
{
  "packetType": 204,
  "data": {
    "conversationId": 30001,
    "upperBoundSeq": 128,
    "nextAfterSeq": 128,
    "hasMore": false,
    "retryAfterMs": -1,
    "messages": [
      {
        "id": 193384848484848,
        "clientMsgId": 91234567001,
        "conversationId": 30001,
        "seq": 121,
        "senderId": 10001,
        "msgType": 1,
        "content": "hello",
        "sendTime": 1772930000000
      }
    ]
  }
}
```

参数说明：

- `afterSeq`：已同步到的游标（不含）。
- `upperBoundSeq`：分页冻结上界（含），可用上次返回值。
- `limit`：分页大小（服务端有上限）。
- `retryAfterMs`：建议重试等待（`-1` 表示无需延迟）。

### 5.7 错误响应

示例 1（参数错误）：

```json
{
  "packetType": 400,
  "data": "receiverId required for private"
}
```

示例 2（权限不足）：

```json
{
  "packetType": 401,
  "data": "not a group member"
}
```

参数说明：

- `packetType=400`：请求格式或参数不合法。
- `packetType=401`：权限不足或未登录。
- 登录注册阶段错误统一使用 `packetType=206`，并在 `data.code` 中给出具体错误码。
