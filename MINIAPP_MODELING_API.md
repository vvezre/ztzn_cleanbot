# 小程序建模功能前端接口交接

文档日期：2026-07-22
云平台分支：`codex/modeling-path-realtime-position`
机器人 FSM 分支：`codex/fsm-realtime-position-4-lines`

## 1. 前端实际使用的地址

本次新增建模功能统一使用以下云平台接口：

| 用途 | 方法 | 地址 |
|---|---|---|
| 发送建模命令 | `POST` | `/api/t-railcar/command` |
| 查询命令执行结果 | `GET` | `/api/command-status/{commandId}` |
| 查询缓存中的指定建模路径 | `GET` | `/api/t-railcar/modeling-path/{productId}?modelId={modelId}` |
| 实时位置 WebSocket 握手 | `STOMP WebSocket` | `/ws/native` |
| 订阅指定设备实时状态 | `STOMP SUBSCRIBE` | `/topic/device/status/-T01{productId}` |

接口地址前面拼接前端当前环境使用的云平台域名。

这里的 Token 是小程序登录接口返回的 `accessToken`。

登录接口：

```http
POST /auth/login
```

登录请求体：

```json
{
  "username": "登录账号",
  "password": "登录密码"
}
```

登录成功后读取：

```text
登录响应.data.accessToken
```

前端保存这个 `accessToken`。调用本文件中的所有 HTTP 接口时，把它放入 `Authorization` 请求头：

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

Token 不放在请求体的 `params` 中。`refreshToken` 也不用于普通建模接口。

如果不携带这个请求头、Token 无效或 Token 已过期，云平台会返回 `401`，建模命令不会发送到机器人。

## 2. 命令接口的固定调用方式

所有建模按钮都调用：

```http
POST /api/t-railcar/command
```

请求体固定结构：

```json
{
  "productId": "设备产品编号",
  "command": "命令名称",
  "params": {}
}
```

字段说明：

| 字段 | 必填 | 类型 | 含义 |
|---|---:|---|---|
| `productId` | 是 | string | 设备产品编号，只传数字部分，如 `250002` |
| `command` | 是 | string | 本文第4节列出的建模命令 |
| `params` | 是 | object | 当前命令参数；无参数时传 `{}` |

云平台收到命令后返回：

```json
{
  "success": true,
  "message": "命令已发送",
  "deviceId": "-T01加productId",
  "command": "本次命令名称",
  "mqttTopic": "RAILCAR/S/完整设备编号",
  "commandId": "本次命令ID",
  "traceId": "本次链路追踪ID",
  "commandStatus": "DISPATCHED",
  "timestamp": "发送时间",
  "operationId": null
}
```

此处的 `success=true` 和 `commandStatus=DISPATCHED` 只表示命令已发往机器人，不表示点位已经记录成功。

前端取得 `commandId` 后，必须调用：

```http
GET /api/command-status/{commandId}
```

查询结果固定结构：

```json
{
  "exists": true,
  "commandId": "本次命令ID",
  "traceId": "链路追踪ID",
  "deviceId": "完整设备编号",
  "deviceType": "T_PYTHON",
  "action": "命令名称",
  "status": "命令状态",
  "message": "状态说明",
  "timeoutMs": 30000,
  "createdAt": 0,
  "updatedAt": 0,
  "terminal": false,
  "detail": {}
}
```

命令状态处理：

| `status` | `terminal` | 前端处理 |
|---|---:|---|
| `CREATED` | false | 继续查询 |
| `DISPATCHED` | false | 继续查询 |
| `ACCEPTED` | false | 继续查询 |
| `RUNNING` | false | 继续查询 |
| `SUCCEEDED` | true | 命令成功，读取 `detail.result.data` |
| `FAILED` | true | 命令失败，显示 `detail.result.message` |
| `REJECTED` | true | 命令被拒绝，显示错误 |
| `TIMEOUT` | true | 命令超时，显示错误 |

前端查询间隔使用 800 毫秒，最长查询 35 秒。

成功后的业务数据位置固定为：

```text
命令状态响应.detail.result.data
```

失败后的错误信息位置固定为：

```text
命令状态响应.detail.result.message
```

失败后的错误码位置固定为：

```text
命令状态响应.detail.result.data.code
```

## 3. 页面按钮与命令对应关系

| 前端操作 | `command` | `params` |
|---|---|---|
| 开始建模 | `start_modeling` | `{"name":"路线名称"}` |
| 放弃当前建模并重新开始 | `start_modeling` | `{"name":"路线名称","restart":true}` |
| 记录区域点 | `sample_modeling_point` | `{}` |
| 记录连接点 | `sample_modeling_link_point` | `{}` |
| 撤销区域点 | `undo_modeling_point` | `{"pointType":"area"}` |
| 撤销连接点 | `undo_modeling_point` | `{"pointType":"link"}` |
| 清空当前区域点 | `clear_modeling_points` | `{"pointType":"area"}` |
| 清空当前连接点 | `clear_modeling_points` | `{"pointType":"link"}` |
| 查询当前建模状态 | `get_modeling_state` | `{}` |
| 完成建模并生成路径 | `finish_modeling` | `{}` |
| 获取当前已生成路径 | `get_modeling_path` | `{}` |

小程序不传 `modelId`、`groupId`、`linkId`。这些 ID 由机器人内部维护。

## 4. 各命令的请求和返回

### 4.1 开始建模

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "start_modeling",
  "params": {
    "name": "路线图1"
  }
}
```

取得 `commandId` 后查询命令状态。

成功时 `detail.result.data` 的结构：

```json
{
  "status": "recording",
  "modelId": "内部模型ID",
  "name": "路线图1",
  "currentGroupId": "内部区域ID",
  "currentLinkId": null,
  "currentAreaNumber": 1,
  "currentPointType": "area",
  "areaPointCount": 0,
  "linkPointCount": 0,
  "totalAreaPointCount": 0,
  "totalLinkPointCount": 0,
  "groupCount": 1,
  "linkCount": 0,
  "createdAt": 0,
  "updatedAt": 0
}
```

前端使用以下字段：

| 字段 | 含义 |
|---|---|
| `status` | 当前会话状态，开始后为 `recording` |
| `name` | 路线名称 |
| `currentAreaNumber` | 当前区域编号，从 1 开始 |
| `currentPointType` | 最近记录类型，`area` 或 `link` |
| `areaPointCount` | 当前区域点数 |
| `linkPointCount` | 当前连接桥点数 |
| `totalAreaPointCount` | 所有区域点总数 |
| `totalLinkPointCount` | 所有连接点总数 |
| `groupCount` | 区域总数 |
| `linkCount` | 连接桥总数 |

前端忽略 `modelId/currentGroupId/currentLinkId`，不使用这些字段发起后续调用。

如果当前已有未完成会话，再次调用 `start_modeling` 会返回原会话并继续记录。

如果用户明确选择放弃原会话，调用：

```json
{
  "productId": "250002",
  "command": "start_modeling",
  "params": {
    "name": "新路线",
    "restart": true
  }
}
```

`restart=true` 必须由用户二次确认后调用。

### 4.2 查询当前建模状态

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "get_modeling_state",
  "params": {}
}
```

没有建模会话时，`detail.result.data`：

```json
{
  "status": "idle",
  "areaPointCount": 0,
  "linkPointCount": 0,
  "totalAreaPointCount": 0,
  "totalLinkPointCount": 0,
  "groupCount": 0,
  "linkCount": 0
}
```

有建模会话时，返回字段与“开始建模”相同。

前端在进入建模页面时先调用此命令：

- `status=idle`：显示“开始建模”。
- `status=recording`：恢复点数并继续记录。
- `status=ready`：路径已经生成，调用 `get_modeling_path` 获取路径。

当前命令只返回状态和数量，不返回全部历史点位数组。

### 4.3 记录区域点

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "sample_modeling_point",
  "params": {}
}
```

成功时 `detail.result.data`：

```json
{
  "modelId": "内部模型ID",
  "groupId": "内部区域ID",
  "pointType": "area",
  "pointNo": 1,
  "point": {
    "id": "内部点位ID",
    "sequence": 1,
    "lat": 32.0364,
    "lon": 118.1234,
    "heading": 90.0,
    "x": 0.0,
    "y": 0.0,
    "source": "rtk_mean",
    "createdAt": 0,
    "updatedAt": 0
  },
  "session": {
    "status": "recording",
    "currentAreaNumber": 1,
    "areaPointCount": 1,
    "totalAreaPointCount": 1,
    "groupCount": 1
  }
}
```

前端点位列表读取：

```text
点位号：data.pointNo
纬度：  data.point.lat
经度：  data.point.lon
```

前端在命令进入 `SUCCEEDED` 前必须禁用“记录区域点”按钮。只有成功后才能把点加入列表。

每个区域至少需要记录 4 个区域点。

### 4.4 记录连接点

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "sample_modeling_link_point",
  "params": {}
}
```

第一次记录成功时 `detail.result.data`：

```json
{
  "pointType": "link",
  "pointNo": 1,
  "point": {
    "sequence": 1,
    "lat": 32.0365,
    "lon": 118.1235,
    "role": "group_link_start"
  },
  "session": {
    "linkPointCount": 1,
    "totalLinkPointCount": 1,
    "linkCount": 1
  }
}
```

第二次记录成功时 `detail.result.data`：

```json
{
  "pointType": "link",
  "pointNo": 2,
  "point": {
    "sequence": 2,
    "lat": 32.0366,
    "lon": 118.1236,
    "role": "group_link_end"
  },
  "session": {
    "linkPointCount": 2,
    "totalLinkPointCount": 2,
    "linkCount": 1
  }
}
```

前端显示规则：

| `point.role` | 显示含义 |
|---|---|
| `group_link_start` | 连接桥起点 |
| `group_link_end` | 连接桥终点 |

固定操作顺序：

1. 当前区域先记录至少 4 个区域点。
2. 第一次点击“记录连接点”，记录连接桥起点。
3. 移动机器人到连接桥另一端。
4. 第二次点击“记录连接点”，记录连接桥终点。
5. 下一次点击“记录区域点”，开始记录下一个区域。

连接桥已经有 2 个点后，不得继续点击“记录连接点”。

### 4.5 撤销区域点

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "undo_modeling_point",
  "params": {
    "pointType": "area"
  }
}
```

成功时 `detail.result.data`：

```json
{
  "pointType": "area",
  "session": {
    "areaPointCount": 3,
    "totalAreaPointCount": 3
  }
}
```

成功后，前端删除当前区域点位列表最后一项，并按照 `session` 更新数量。

### 4.6 撤销连接点

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "undo_modeling_point",
  "params": {
    "pointType": "link"
  }
}
```

成功后，前端删除当前连接点列表最后一项，并按照返回的 `session.linkPointCount` 更新数量。

### 4.7 清空当前区域点

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "clear_modeling_points",
  "params": {
    "pointType": "area"
  }
}
```

成功后，前端清空当前区域点位列表，并按照返回的 `session` 更新数量。

### 4.8 清空当前连接点

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "clear_modeling_points",
  "params": {
    "pointType": "link"
  }
}
```

成功后，前端清空当前连接点列表。

此命令清空的是连接桥的点，连接桥本身仍然存在。用户可以重新记录起点和终点。

### 4.9 完成建模并生成路径

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "finish_modeling",
  "params": {}
}
```

成功时 `detail.result.data`：

```json
{
  "modelId": "内部模型ID",
  "taskName": "路线图1",
  "updatedAt": 0,
  "taskPlan": {
    "status": "ready",
    "generatedAt": 0,
    "taskName": "路线图1",
    "summary": {
      "taskCount": 10,
      "cleanTaskCount": 7,
      "transferTaskCount": 3,
      "totalLengthCm": 3200
    },
    "tasks": [
      {
        "id": 1,
        "mode": 1,
        "areaNumber": 1,
        "startX": 0,
        "startY": 0,
        "endX": 226,
        "endY": 0,
        "startLat": 32.0364,
        "startLon": 118.1234,
        "endLat": 32.0364,
        "endLon": 118.1235,
        "heading": 90.0,
        "angle": 90.0,
        "length": 226,
        "source": "modeling_clean"
      }
    ]
  },
  "session": {
    "status": "ready",
    "groupCount": 1,
    "linkCount": 0
  }
}
```

前端获取线段数组：

```text
命令状态响应.detail.result.data.taskPlan.tasks
```

前端不计算清扫线，不改变线段顺序，直接按照服务端返回顺序绘制。

路径线段字段：

| 字段 | 含义 |
|---|---|
| `id` | 线段顺序编号 |
| `mode=1` | 清扫线 |
| `mode=2` | 转场线或区域连接线 |
| `areaNumber` | 所属区域编号 |
| `startX/startY` | 局部坐标起点，单位厘米 |
| `endX/endY` | 局部坐标终点，单位厘米 |
| `startLat/startLon` | 起点经纬度 |
| `endLat/endLon` | 终点经纬度 |
| `heading` | 行驶航向角 |
| `length` | 线段长度，单位厘米 |

完成建模的服务端条件：

- 每个区域至少 4 个区域点。
- 每条连接桥都有起点和终点。
- 区域可以由现有算法自动识别。
- 现有路径算法能生成清扫线。

### 4.10 获取当前已生成路径

调用：

```http
POST /api/t-railcar/command
```

请求体：

```json
{
  "productId": "250002",
  "command": "get_modeling_path",
  "params": {}
}
```

成功时 `detail.result.data`：

```json
{
  "modelId": "内部模型ID",
  "taskName": "路线图1",
  "updatedAt": 0,
  "taskPlan": {
    "status": "ready",
    "summary": {},
    "tasks": []
  }
}
```

此命令只能在 `finish_modeling` 成功后调用。

## 5. 实时位置怎么接收

实时位置不通过 HTTP 轮询，使用现有 STOMP WebSocket。

握手地址：

```text
/ws/native
```

前端根据当前云平台域名建立连接：

```text
开发环境使用 ws://云平台域名/ws/native
生产环境使用 wss://云平台域名/ws/native
```

订阅地址：

```text
/topic/device/status/-T01{productId}
```

`productId=250002` 时订阅：

```text
/topic/device/status/-T01250002
```

这是 STOMP 主题，不是 HTTP 地址。前端需要使用项目现有的 STOMP 客户端订阅。

收到的消息体为 JSON。实时位置字段：

```json
{
  "deviceId": "-T01250002",
  "localX": 123,
  "localY": 456,
  "timestamp": 0
}
```

字段说明：

| 字段 | 含义 |
|---|---|
| `localX` | 机器人当前局部 X 坐标，单位厘米 |
| `localY` | 机器人当前局部 Y 坐标，单位厘米 |
| `timestamp` | 本次状态时间戳，毫秒 |

机器人默认每 1 秒上报一次 `localX/localY`。

`localX/localY` 与路径中的 `startX/startY/endX/endY` 是同一坐标系。前端画机器人位置时，必须使用画路径时相同的缩放、偏移和 Y 轴翻转方式。

WebSocket 收到没有 `localX/localY` 的普通状态消息时，不更新机器人位置。

## 6. 指定 modelId 查询云平台缓存路径

接口：

```http
GET /api/t-railcar/modeling-path/{productId}?modelId={modelId}
```

请求参数：

| 参数 | 位置 | 必填 | 含义 |
|---|---|---:|---|
| `productId` | path | 是 | 设备产品编号 |
| `modelId` | query | 是 | `finish_modeling` 返回的内部模型 ID |

返回结构：

```json
{
  "success": true,
  "message": "modeling path fetched",
  "data": {
    "deviceId": "-T01250002",
    "modelId": "内部模型ID",
    "taskName": "路线图1",
    "updatedAt": 0,
    "taskPlan": {
      "status": "ready",
      "summary": {},
      "tasks": []
    }
  }
}
```

小程序主流程不使用此接口。小程序主流程使用无内部 ID 的 `get_modeling_path` 命令。

## 7. 前端必须按照此顺序调用

### 7.1 单区域

```text
1. get_modeling_state
2. 如果 status=idle：start_modeling
3. sample_modeling_point，至少调用 4 次
4. finish_modeling
5. 读取 taskPlan.tasks 画路径
6. 订阅 WebSocket，使用 localX/localY 显示机器人
```

### 7.2 多区域

```text
1. start_modeling
2. 第一区域：sample_modeling_point，至少 4 次
3. sample_modeling_link_point，记录连接起点
4. 移动机器人
5. sample_modeling_link_point，记录连接终点
6. sample_modeling_point，开始第二区域
7. 第二区域继续记录，保证至少 4 个区域点
8. 如有更多区域，重复第3～7步
9. finish_modeling
10. 读取 taskPlan.tasks 画路径
```

## 8. 按钮处理要求

| 按钮 | 调用命令 | 启用条件 |
|---|---|---|
| 开始建模 | `start_modeling` | 当前没有建模会话 |
| 记录区域点 | `sample_modeling_point` | `status=recording`，且没有命令正在等待 |
| 记录连接点 | `sample_modeling_link_point` | 当前区域至少 4 点，当前连接点数小于 2 |
| 撤销区域点 | `undo_modeling_point + area` | 当前区域点数大于 0 |
| 撤销连接点 | `undo_modeling_point + link` | 当前连接点数大于 0 |
| 清空区域点 | `clear_modeling_points + area` | 当前区域点数大于 0 |
| 清空连接点 | `clear_modeling_points + link` | 当前连接点数大于 0 |
| 完成 | `finish_modeling` | 当前区域至少 4 点，连接桥已完成 |

记录点命令不是幂等接口。前端必须遵守：

1. 点击记录按钮后立即禁用按钮。
2. 当前命令没有进入终态前，不得再次发送相同命令。
3. 已取得 `commandId` 后如果网络波动，不得重新发送记录命令，继续查询原 `commandId`。
4. 只有命令状态为 `SUCCEEDED` 后，才在界面增加点位。
5. `FAILED/REJECTED/TIMEOUT` 时不增加点位。

## 9. 错误码处理

错误码读取位置：

```text
命令状态响应.detail.result.data.code
```

错误信息读取位置：

```text
命令状态响应.detail.result.message
```

| 错误码 | 含义 | 前端处理 |
|---|---|---|
| `MODELING_SESSION_NOT_STARTED` | 未开始建模 | 提示先开始建模 |
| `MODELING_SESSION_NOT_RECORDING` | 当前会话不能继续记录 | 重新查询建模状态 |
| `MODELING_GROUP_MISSING` | 当前区域不存在 | 提示重新开始建模 |
| `MODELING_AREA_INCOMPLETE` | 当前区域不足 4 点 | 提示继续记录区域点 |
| `MODELING_LINK_INCOMPLETE` | 连接桥未记录完整 | 提示补录连接终点 |
| `MODELING_LINK_MISSING` | 当前没有连接桥 | 禁用连接点撤销/清空按钮 |
| `MODELING_POINT_MISSING` | 没有可撤销的点 | 提示没有可撤销点位 |
| `MODELING_POINT_TYPE_INVALID` | `pointType` 错误 | 只能传 `area` 或 `link` |
| `MODELING_PATH_NOT_READY` | 路径尚未生成 | 先调用 `finish_modeling` |
| `MODELING_RECOGNITION_NEEDS_CONFIRMATION` | 复杂区域需要人工确认 | 当前小程序流程暂不能自动完成 |
| `RTK_SAMPLE_PROVIDER_MISSING` | 机器人采样来源未接入 | 联系机器人维护人员 |
| `RTK_SAMPLE_EMPTY` | 没有读到 RTK 状态 | 检查定位设备 |
| `RTK_LOCATION_MISSING` | 没有有效经纬度 | 检查定位状态 |
| `RTK_NOT_FIXED` | RTK 未固定 | 等待 RTK 固定后重试 |
| `RTK_SAMPLE_UNSTABLE` | 采样不稳定 | 停稳机器人后重试 |

前端显示错误时优先显示服务端返回的 `detail.result.message`。

## 10. 当前接口边界

1. 当前没有“查询全部历史建模点位”的接口。`get_modeling_state` 只返回当前数量和会话状态。
2. 页面不刷新时，前端可以使用每次记录点返回的 `point` 维护点位列表。
3. 页面刷新后，只能恢复点数，不能从现有接口恢复全部点位明细。
4. 一条连接桥固定由起点和终点两个点组成，这是现有算法规则。
5. 复杂凹形区域如果需要人工确认，当前小程序流程会返回错误。
6. 前端不计算清扫路径，只展示 `taskPlan.tasks`。
7. 实时机器人位置只使用 `localX/localY`，不要使用经纬度重新计算画布位置。

## 11. 前端联调验收

- [ ] `POST /api/t-railcar/command` 能返回 `commandId`。
- [ ] 前端能用 `commandId` 查询到 `SUCCEEDED/FAILED`。
- [ ] 前端从 `detail.result.data` 读取业务数据。
- [ ] 开始建模后 `status=recording`。
- [ ] 记录区域点后能取得 `pointNo/lat/lon`。
- [ ] 连接点第一次返回 `group_link_start`。
- [ ] 连接点第二次返回 `group_link_end`。
- [ ] 撤销和清空后数量正确。
- [ ] 完成建模后 `taskPlan.status=ready`。
- [ ] `taskPlan.tasks` 能画出清扫线和转场线。
- [ ] WebSocket 能收到 `localX/localY`。
- [ ] 机器人图标和路径使用同一个坐标变换。
- [ ] 快速重复点击不会重复记录点。
- [ ] RTK 未固定、区域点不足、连接未完成时能显示服务端错误。

## 12. 最终结论

前端建模页面只需要实现以下三类调用：

```text
1. POST /api/t-railcar/command
   通过 command 区分开始、记录点、撤销、清空、完成和获取路径。

2. GET /api/command-status/{commandId}
   查询机器人真正的执行结果，读取 detail.result.data。

3. STOMP /ws/native
   订阅 /topic/device/status/-T01{productId}，读取 localX/localY。
```

前端不创建模型 ID、区域 ID 或连接 ID，也不计算清扫路径。
