# 关系域文档（主）

本文档为关系域唯一主文档，覆盖协议说明、测试 JSON 用例、参数说明和事件字段。

## 1. 通用协议

### 1.1 客户端请求

```json
{
  "packetType": 110,
  "data": {
    "requestId": "req-unique-id",
    "op": "OP_CODE",
    "payload": {}
  }
}
```

参数说明：

- `packetType`：固定 `110`（`CLIENT_RELATION_COMMAND`）。
- `data.requestId`：请求唯一标识，建议全局唯一。
- `data.op`：关系域操作码。
- `data.payload`：业务参数对象，可传空对象 `{}`。

### 1.2 服务端应答（统一 ACK）

```json
{
  "packetType": 210,
  "data": {
    "requestId": "req-unique-id",
    "op": "OP_CODE",
    "code": 0,
    "message": "ok",
    "data": {}
  }
}
```

`code` 说明：

- `200`：成功。
- `400`：报文格式或参数基础校验失败（如 `requestId/op` 缺失、`payload` 非对象）。
- `401`：未登录/无权限。
- `1001`：业务异常。

客户端建议：

- 以 `requestId` 做应答关联和幂等去重。
- 以 `code != 0` 判定失败，不要仅依赖超时重试。

## 2. 操作码总览

- `FRIEND_APPLY`
- `FRIEND_APPLY_DECIDE`
- `FRIEND_APPLY_LIST`
- `FRIEND_LIST`
- `FRIEND_DELETE`
- `BLACKLIST_ADD`
- `BLACKLIST_REMOVE`
- `BLACKLIST_LIST`
- `PRIVACY_SET_STRANGER_CHAT`
- `PRIVACY_GET_SETTINGS`
- `GROUP_CREATE`
- `GROUP_GET_PROFILE`
- `GROUP_DISMISS`
- `GROUP_UPDATE_PROFILE`
- `GROUP_QUIT`
- `GROUP_JOIN_APPLY`
- `GROUP_JOIN_DECIDE`
- `GROUP_JOIN_APPLY_LIST`
- `GROUP_KICK`
- `GROUP_ADMIN_SET`
- `GROUP_ADMIN_UNSET`
- `GROUP_MUTE_ALL_SET`
- `GROUP_MUTE_ALL_UNSET`
- `GROUP_MEMBER_MUTE_SET`
- `GROUP_MEMBER_MUTE_UNSET`

## 3. 接口测试 JSON 用例

### 3.1 FRIEND_APPLY

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-friend-apply-001",
    "op": "FRIEND_APPLY",
    "payload": {
      "targetUserId": 10002,
      "applyMessage": "你好，想加你好友"
    }
  }
}
```

参数说明：

- `payload.targetUserId`：目标用户ID，`long`，必填。
- `payload.applyMessage`：申请附言，`string`，可选。

### 3.2 FRIEND_APPLY_DECIDE

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-friend-apply-decide-001",
    "op": "FRIEND_APPLY_DECIDE",
    "payload": {
      "applyId": 190001,
      "approve": true
    }
  }
}
```

参数说明：

- `payload.applyId`：好友申请ID，`long`，必填。
- `payload.approve`：是否同意，`boolean`，可选，默认 `false`。

### 3.3 FRIEND_APPLY_LIST

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-friend-apply-list-001",
    "op": "FRIEND_APPLY_LIST",
    "payload": {
      "status": 0,
      "pageNo": 1,
      "pageSize": 20
    }
  }
}
```

参数说明：

- `payload.status`：申请状态，`number`，可选，默认 `0`。
- 状态值：`0=待处理,1=同意,2=拒绝,3=取消,-1=全部`。
- `payload.pageNo`：页码，`number`，可选，默认 `1`。
- `payload.pageSize`：每页条数，`number`，可选，默认 `20`，最大 `100`。

### 3.4 FRIEND_LIST

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-friend-list-001",
    "op": "FRIEND_LIST",
    "payload": {}
  }
}
```

参数说明：

- `payload`：无需业务参数，传 `{}` 即可。

### 3.5 FRIEND_DELETE

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-friend-delete-001",
    "op": "FRIEND_DELETE",
    "payload": {
      "targetUserId": 10002
    }
  }
}
```

参数说明：

- `payload.targetUserId`：要删除的好友用户ID，`long`，必填。

### 3.6 BLACKLIST_ADD

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-blacklist-add-001",
    "op": "BLACKLIST_ADD",
    "payload": {
      "targetUserId": 10003
    }
  }
}
```

参数说明：

- `payload.targetUserId`：拉黑目标用户ID，`long`，必填。

### 3.7 BLACKLIST_REMOVE

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-blacklist-remove-001",
    "op": "BLACKLIST_REMOVE",
    "payload": {
      "targetUserId": 10003
    }
  }
}
```

参数说明：

- `payload.targetUserId`：移除黑名单目标用户ID，`long`，必填。

### 3.8 BLACKLIST_LIST

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-blacklist-list-001",
    "op": "BLACKLIST_LIST",
    "payload": {}
  }
}
```

参数说明：

- `payload`：无需业务参数，传 `{}` 即可。

### 3.9 PRIVACY_SET_STRANGER_CHAT

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-privacy-set-001",
    "op": "PRIVACY_SET_STRANGER_CHAT",
    "payload": {
      "allow": true
    }
  }
}
```

参数说明：

- `payload.allow`：是否允许陌生人发起聊天，`boolean`，可选，默认 `false`。

### 3.10 PRIVACY_GET_SETTINGS

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-privacy-get-001",
    "op": "PRIVACY_GET_SETTINGS",
    "payload": {}
  }
}
```

参数说明：

- `payload`：无需业务参数，传 `{}` 即可。

### 3.11 GROUP_CREATE

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-create-001",
    "op": "GROUP_CREATE",
    "payload": {
      "name": "项目协作群",
      "avatar": "https://cdn.example.com/g1.png",
      "notice": "请遵守群规则",
      "joinPolicy": 1
    }
  }
}
```

参数说明：

- `payload.name`：群名称，`string`，必填。
- `payload.avatar`：群头像URL，`string`，可选。
- `payload.notice`：群公告，`string`，可选。
- `payload.joinPolicy`：入群策略，`int`，可选，`0=开放入群`，`1=审批入群`，默认 `1`。

### 3.12 GROUP_DISMISS

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-dismiss-001",
    "op": "GROUP_DISMISS",
    "payload": {
      "groupId": 20001
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。

### 3.13 GROUP_UPDATE_PROFILE

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-update-profile-001",
    "op": "GROUP_UPDATE_PROFILE",
    "payload": {
      "groupId": 20001,
      "name": "项目协作群-2",
      "avatar": "https://cdn.example.com/g1-new.png",
      "notice": "公告已更新",
      "joinPolicy": 0
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。
- `payload.name`：群名称，`string`，可选。
- `payload.avatar`：群头像URL，`string`，可选。
- `payload.notice`：群公告，`string`，可选。
- `payload.joinPolicy`：入群策略，`int`，可选，`0/1`，仅群主可修改。

### 3.14 GROUP_QUIT

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-quit-001",
    "op": "GROUP_QUIT",
    "payload": {
      "groupId": 20001
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。

### 3.15 GROUP_JOIN_APPLY

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-join-apply-001",
    "op": "GROUP_JOIN_APPLY",
    "payload": {
      "groupId": 20001,
      "applyMessage": "申请入群学习交流"
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。
- `payload.applyMessage`：入群申请信息，`string`，可选。

### 3.16 GROUP_JOIN_DECIDE

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-join-decide-001",
    "op": "GROUP_JOIN_DECIDE",
    "payload": {
      "applyId": 390001,
      "approve": true
    }
  }
}
```

参数说明：

- `payload.applyId`：入群申请ID，`long`，必填。
- `payload.approve`：是否同意入群，`boolean`，可选，默认 `false`。

### 3.17 GROUP_JOIN_APPLY_LIST

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-join-apply-list-001",
    "op": "GROUP_JOIN_APPLY_LIST",
    "payload": {
      "groupId": 20001,
      "status": 0,
      "pageNo": 1,
      "pageSize": 20
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，可选；不传表示查询当前用户可管理的全部群。
- `payload.status`：申请状态，`number`，可选，默认 `0`。
- 状态值：`0=待处理,1=同意,2=拒绝,3=取消,-1=全部`。
- `payload.pageNo`：页码，`number`，可选，默认 `1`。
- `payload.pageSize`：每页条数，`number`，可选，默认 `20`，最大 `100`。

### 3.18 GROUP_KICK

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-kick-001",
    "op": "GROUP_KICK",
    "payload": {
      "groupId": 20001,
      "targetUserId": 10010
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。
- `payload.targetUserId`：被踢成员用户ID，`long`，必填。

### 3.19 GROUP_ADMIN_SET

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-admin-set-001",
    "op": "GROUP_ADMIN_SET",
    "payload": {
      "groupId": 20001,
      "targetUserId": 10011
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。
- `payload.targetUserId`：要设置为管理员的用户ID，`long`，必填。

### 3.20 GROUP_ADMIN_UNSET

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-admin-unset-001",
    "op": "GROUP_ADMIN_UNSET",
    "payload": {
      "groupId": 20001,
      "targetUserId": 10011
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。
- `payload.targetUserId`：要取消管理员的用户ID，`long`，必填。

### 3.21 GROUP_MUTE_ALL_SET

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-mute-all-set-001",
    "op": "GROUP_MUTE_ALL_SET",
    "payload": {
      "groupId": 20001
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。

### 3.22 GROUP_MUTE_ALL_UNSET

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-mute-all-unset-001",
    "op": "GROUP_MUTE_ALL_UNSET",
    "payload": {
      "groupId": 20001
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。

### 3.23 GROUP_MEMBER_MUTE_SET

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-member-mute-set-001",
    "op": "GROUP_MEMBER_MUTE_SET",
    "payload": {
      "groupId": 20001,
      "targetUserId": 10012,
      "durationSeconds": 600
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。
- `payload.targetUserId`：被禁言用户ID，`long`，必填。
- `payload.durationSeconds`：禁言秒数，`int`，可选，默认 `600`，必须大于 `0`。

### 3.24 GROUP_MEMBER_MUTE_UNSET

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-member-mute-unset-001",
    "op": "GROUP_MEMBER_MUTE_UNSET",
    "payload": {
      "groupId": 20001,
      "targetUserId": 10012
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。
- `payload.targetUserId`：解除禁言用户ID，`long`，必填。

### 3.25 GROUP_GET_PROFILE

```json
{
  "packetType": 110,
  "data": {
    "requestId": "tc-group-get-profile-001",
    "op": "GROUP_GET_PROFILE",
    "payload": {
      "groupId": 20001
    }
  }
}
```

参数说明：

- `payload.groupId`：群ID，`long`，必填。

成功返回字段：

- `groupId`：群ID。
- `conversationId`：群会话ID（与 `groupId` 解耦）。
- `name`：群名称。
- `avatar`：群头像，可为 `null`。
- `notice`：群公告，可为 `null`。
- `ownerId`：群主用户ID。
- `joinPolicy`：入群策略，`0=open,1=approval`。
- `muteAll`：是否全员禁言，`boolean`。
- `myRole`：当前用户在群内角色，`1=member,2=admin,3=owner`。
- `isMember`：当前用户是否为群成员（本接口成功返回时恒为 `true`）。

## 4. 服务端事件字段

服务端关系域事件包：`packetType=211`（`SERVER_RELATION_EVENT`）。

- `FRIEND_APPLY_CREATED`：`applyId`, `applicantId`, `targetId`
- `FRIEND_APPLY_DECIDED`：`applyId`, `applicantId`, `targetId`, `approve`
- `FRIEND_DELETED`：`userId`, `targetUserId`
- `BLACKLIST_ADDED`：`userId`, `targetUserId`
- `GROUP_DISMISSED`：`groupId`, `conversationId`
- `GROUP_PROFILE_UPDATED`：`groupId`, `name`, `avatar`, `notice`, `joinPolicy`
- `GROUP_MEMBER_QUIT`：`groupId`, `userId`
- `GROUP_MEMBER_JOINED`：`groupId`, `userId`, `auto`
- `GROUP_JOIN_APPLY_CREATED`：`applyId`, `groupId`, `applicantId`
- `GROUP_JOIN_APPLY_DECIDED`：`applyId`, `groupId`, `applicantId`, `decisionUserId`, `approve`
- `GROUP_MEMBER_KICKED`：`groupId`, `operatorId`, `targetUserId`
- `GROUP_ADMIN_SET`：`groupId`, `targetUserId`
- `GROUP_ADMIN_UNSET`：`groupId`, `targetUserId`
- `GROUP_MUTE_ALL_SET`：`groupId`, `muteAll`
- `GROUP_MUTE_ALL_UNSET`：`groupId`, `muteAll`
- `GROUP_MEMBER_MUTED`：`groupId`, `targetUserId`, `bannedUntil`
- `GROUP_MEMBER_MUTE_REMOVED`：`groupId`, `targetUserId`, `bannedUntil`

## 5. 约定

- `groupId` 与 `conversationId` 不是同一个字段：群域操作使用 `groupId`，会话层使用 `conversationId`。
- 群角色：`1=member`, `2=admin`, `3=owner`。
- 入群策略：`0=open`, `1=approval`。
