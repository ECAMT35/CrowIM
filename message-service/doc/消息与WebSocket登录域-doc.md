# 消息与 WebSocket 登录域 - 链路检查与接口文档

更新时间：2026-03-08

## 1. 范围

- WebSocket 登录/连接管理
- 消息发送、投递、拉取、已读、摘要

## 2. 链路总览

### 2.1 WebSocket 登录链路

1. 客户端连接后，服务端在 `handlerAdded` 返回欢迎文本。
2. 未注册状态下，`WebSocketFrameHandler.handleRegistration` 解析 `CommonPacketDto.data` 中 `userId/deviceId`。
3. 调用 `UserChannelRegistry.registerUserAsync`：
    - eventLoop 写入 channel attrs
    - 虚拟线程获取分布式锁 + 写 Redis `ws:online:{userId}:{deviceId}`
    - 本地映射 `deviceChannels`
    - 必要时 MQ 通知旧节点踢线
4. 注册成功返回 `REGISTER_SUCCESS` 文本。

### 2.2 消息发送链路

1. 客户端发 `packetType=100`（`CLIENT_REQUEST_SENT`）。
2. `ClientSendMessageHandler` 解析并调用 `MessageCommandService.sendMessage`。
3. `MessageCommandService`：
    - 幂等检查（`clientMsgId + senderId`）
    - 单聊会话校验/创建，或群成员与禁言校验
    - 分配 `seq`，写 `message`
    - 拉取会话成员并调用 `DeliveryService.deliverToUserDevices`
4. 给发送端回 `packetType=201`（`SERVER_ACK_SENT`）。

### 2.3 投递链路

1. `DeliveryService` 查 `user:devices:{userId}` 设备集合。
2. 每台设备查 `ws:online:{userId}:{deviceId}` 的 `node/sessionId`。
3. 本节点直接写 channel；跨节点转 MQ（`websocket-message-{node}`）。
4. 对端节点由 `MessagePushListener` 消费后再次 `deliverToUserDevices`。

### 2.4 拉取/已读链路

- `CLIENT_PULL_MESSAGES(103)` -> `MessageService.pullMessages` 按 seq 区间拉取。
- `CLIENT_ACK_READ(101)` -> `CursorService.advanceRead` 推进读游标。
- `CLIENT_PULL_SUMMARY(102)` -> `SummaryService.buildSummary` 构建会话摘要。

## 5. 消息域与登录域接口示例

说明：以下是当前协议使用方式与建议字段，均为 JSON 示例。

### 5.1 登录注册（WebSocket 首包）

请求示例：

```json
{
  "packetType": 0,
  "data": {
    "userId": 10001,
    "deviceId": "android-9f3a"
  }
}
```

成功响应（文本帧）：

```json
"REGISTER_SUCCESS"
```

参数说明：

- `packetType`：当前实现未强校验该值，建议固定约定。
- `data.userId`：当前登录用户 ID。
- `data.deviceId`：设备唯一标识。

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

- 返回 `data` 为 `convId -> {lastSeq, readSeq, unread}` 的映射。

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

示例：

```json
{
  "packetType": 400,
  "data": {
    "reason": "invalid message format"
  }
}
```

参数说明：

- `packetType=400`：请求格式不合法。
- `packetType=401`：权限不足或未登录。

