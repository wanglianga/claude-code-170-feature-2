# 社区独居老人一键求助 · 上门核实转介回访平台

面向社区独居老人照护场景：老人通过**智能按钮、电话、社区小程序**发起求助后，值班社工在同一条事件中完成
**求助受理 → 研判分类 → 分级转介 → 上门核实 → 后续服务 → 家属沟通 → 回访归档**，
并在归档时联动更新老人照护画像、家属响应表现、邻里协助可信度与下一次主动关怀计划，
支撑社区管理者按风险等级、响应时长、服务类型、回访结果复盘独居老人照护能力。

## 技术栈

- 前端：Vue 3 + Vite + Element Plus + Pinia + Vue Router + ECharts（SSE 实时告警）
- 后端：Spring Boot 3.3 + Spring Data JPA + Redis（登录令牌）+ PostgreSQL
- 基础设施：Docker Compose 一键启动 postgres / redis / backend / frontend

## 目录结构

```
170-b/
├── docker-compose.yml          # 一键启动全部服务
├── backend/                    # Spring Boot 后端（端口 8080）
│   ├── Dockerfile
│   └── src/main/java/com/community/eldercare/
│       ├── domain/             # 老人画像、求助事件聚合（研判/转介/上门/服务/沟通/回访/时间线）
│       ├── service/            # 事件全生命周期、风险标记、归档联动画像更新、复盘统计
│       ├── controller/         # REST API
│       ├── security/           # Redis Token 鉴权
│       └── notify/             # SSE 实时推送
└── frontend/                   # Vue3 前端（dev 5173 / 容器 Nginx 8081）
    ├── Dockerfile
    ├── nginx.conf
    └── src/views/              # 值班工作台 / 事件处置 / 老人画像 / 照护复盘
```

## 一、Docker 一键启动（推荐）

前置：安装 Docker 与 Docker Compose。

```bash
cd 170-b
docker compose up -d --build
```

启动后访问：

| 服务 | 地址 |
| --- | --- |
| 前端页面 | http://localhost:8081 |
| 后端 API | http://localhost:8080/api |

首次启动自动建表并写入 5 个测试账号和 3 位示例老人档案（周桂兰、吴德海、郑秀珍）。

停止与清理：

```bash
docker compose down          # 停止（保留数据卷）
docker compose down -v       # 停止并清空数据库/上传文件
```

## 二、本地开发启动

### 1. 准备 PostgreSQL 与 Redis

```bash
# PostgreSQL 15（创建库 eldercare，账号 care / 密码 care123）
createdb -U care eldercare
# Redis（默认 6379，无密码）
redis-server --daemonize yes
```

如数据库地址不同，可用环境变量覆盖：
`ELDERCARE_DB_URL`、`ELDERCARE_DB_USER`、`ELDERCARE_DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`。

### 2. 启动后端（:8080）

```bash
cd backend
mvn spring-boot:run
# 或：mvn package -DskipTests && java -jar target/eldercare.jar
```

### 3. 启动前端（:5173，自动代理 /api 到 8080）

```bash
cd frontend
npm install        # 如遇官方源慢：npm config set registry https://registry.npmmirror.com
npm run dev
```

浏览器打开 http://localhost:5173 。

## 测试账号（密码均为 `123456`）

| 账号 | 角色 |
| --- | --- |
| worker | 值班社工（李社工） |
| grid | 网格员（赵网格员） |
| doctor | 家庭医生（钱医生） |
| volunteer | 邻里志愿者（孙阿姨） |
| admin | 社区管理者（王主任） |

## 业务功能与验证路径

### 1. 一条事件贯通全流程（“求助—核实—上门—转介—服务—家属沟通—回访”）

值班工作台 →「模拟一键求助」选择老人与来源（智能按钮/电话/小程序），填写语音内容与定位 →
进入事件详情，依次操作：

1. **值班研判**：生活协助 / 身体不适 / 跌倒风险 / 情绪危机 / 误触，严重程度 低/中/高/紧急；
2. **转介通知**：按严重程度通知网格员 / 邻里志愿者 / 家庭医生 / 120，可登记邻居代报及信息是否清晰，并更新受理/到场状态；
3. **上门核实**：入户方式（钥匙、门禁密码、智能锁授权、邻居/家属开门、破锁、未能入户）、现场照片上传、
   老人状态、是否联系家属、是否需要送医、是否拒绝就医、是否需要临时照护、后续服务建议、家中安全隐患；
4. **家属沟通**：多次拨打接通情况与态度（连续未接通自动判定“家属长期不接电话”）；
5. **后续服务**：临时照护、送医陪诊、日常巡访、助餐、适老化改造、心理陪伴；
6. **回访归档**：好转/平稳/恶化/未联系上 + 满意度 + 下一次主动关怀计划，归档联动更新画像。

右侧固定展示**老人照护画像**（长期独居、燃气报警器、最近家属探访、医生随访结论、历史误触、
基础病、门禁信息、紧急联系人、邻里响应人），底部为全过程时间线留痕。

### 2. 七类特殊风险自动/手动标记，全程沉淀在同一事件

| 风险 | 触发方式 |
| --- | --- |
| 无法开门 | 上门记录“未能入户”或入户方式为未能入户 |
| 定位偏移 | 求助坐标与档案住址距离 > 500 米自动判定（阈值可配） |
| 家属长期不接电话 | 档案联系人已标记，或同一事件累计 ≥2 次未接通 / 联系人累计未接通率超 2/3 |
| 邻居代报信息不清 | 转介时勾选“邻居代报·信息不清”，同时下调该邻里可信度 |
| 老人拒绝就医 | 上门记录勾选 |
| 家中存在安全隐患 | 上门填写隐患描述（自动建议适老化改造） |
| 频繁夜间求助 | 22:00–06:00 求助，近 30 天夜间求助 ≥3 次自动标记（阈值可配） |

### 3. 事件不停在处置结果——归档联动更新

回访归档后系统自动：

- 重算老人**综合风险等级**；
- 更新**家属响应表现评分**（本事件接通率）与联系人接通统计；
- 更新**邻里协助可信度**（有效响应、代报信息质量）；
- 累加**误触记录**；
- 依据跌倒/隐患/情绪危机/临时照护等勾选**增加日常巡访、助餐、适老化改造、心理陪伴**建议；
- 生成**下一次主动关怀计划**（可手动指定，否则按严重程度 1/3/7/14 天自动排期）。

### 4. 社区管理者复盘

「照护能力复盘」页（admin 账号可看全部数据）支持按时间区间统计：
事件总数/夜间/送医、按风险等级分布、按研判类型分布、按服务类型分布、按回访结果分布、
每日求助趋势、平均/最快/最慢响应时长与 15 分钟到场率、七类风险场景出现频次。

### 5. 拒绝就医后的风险随访闭环（家庭医生判定异常、老人拒医）

当**家庭医生判断老人血压等体征异常、但老人拒绝去医院**时，在事件详情中「登记拒绝就医」，
平台强制留痕并启动随访闭环：

1. **记录四要素**：医生建议（医生姓名/就诊建议/风险告知）、老人拒绝原因、家属是否知情（含家属姓名与态度）、替代照护安排（用药、监测、陪护）；
2. **事件不能直接关闭**：登记后事件进入「拒医随访中」，系统自动生成一条 **24 小时内到期**的复访任务；此时点击「回访归档」会被后端拒绝；
3. **复访任务区分三种方式**：`电话复访 PHONE` / `上门复访 ONSITE` / `医生复诊 DOCTOR`，可指派责任人与截止时间；
   值班工作台顶部展示「拒医后 24 小时复访任务」看板，到期未复访由定时任务标记**逾期**并实时告警；
4. **复访结果分流**：
   - **症状缓解 RELIEVED** → 解除随访闭环，事件恢复正常回访归档；
   - **症状加重 WORSENED** → 老人风险等级提升至**极高**、**重新通知家属与医疗人员（家庭医生 + 120 + 网格员）**，并生成一条新事件；
   - **症状未缓解 UNRELIEVED** → 风险至少升至**高**、重新通知家属/家庭医生/网格员（不呼叫 120），并生成新事件；
5. **历史带入新事件，防止被当成普通求助**：升级事件时间线自动写入
   「⚠ 由拒医事件复访升级，不是一次新的普通求助」以及**原拒医记录、家属态度、医生建议、本次复访结论**，
   新事件带 `REFUSAL_ESCALATED` 风险标记、状态直达「已研判」，并与原拒医事件双向关联（页面顶部红色横幅可互相跳转）。

相关配置（`application.yml` / 环境变量）：
`app.refusal-revisit-hours`（复访时限，默认 24 小时）、`app.refusal-scan-ms`（逾期扫描间隔，默认 60000ms）。

## 主要 API（服务端实现）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/auth/login | 登录获取 Redis 令牌 |
| GET | /api/events/stream | SSE 实时推送（新求助/状态变更/归档/拒医升级/复访逾期） |
| POST | /api/events | 发起求助（自动定位偏移/夜间/频繁夜间/家属失联标记） |
| GET | /api/events?status=&all=&elderId= | 事件队列 |
| GET | /api/events/{id} | 事件聚合详情（含画像、各环节记录、拒医/复访、关联事件、时间线） |
| POST | /api/events/{id}/triage | 研判 |
| POST | /api/events/{id}/dispatches | 转介通知 |
| PUT | /api/events/dispatches/{id}/status | 受理/到场/无法响应 |
| POST | /api/events/{id}/onsite | 上门核实 |
| POST | /api/events/{id}/services | 后续服务 |
| POST | /api/events/{id}/family-communications | 家属沟通 |
| POST | /api/events/{id}/medical-refusals | **登记拒绝就医，自动生成 24 小时内复访任务（三方式）** |
| GET | /api/events/revisits/pending | **工作台待复访/已逾期任务** |
| POST | /api/events/revisits/{taskId}/complete | **完成复访：缓解解除随访；加重/未缓解则升级、重新通知并生成携带历史的新事件** |
| POST | /api/events/{id}/follow-up | 回访并归档（**拒医复访未完成时被拒绝**） |
| POST | /api/events/{id}/flags | 人工补充风险标记 |
| POST | /api/files/upload | 现场照片/语音上传 |
| GET/POST/PUT | /api/elders... | 老人档案、紧急联系人、邻里响应人管理 |
| GET | /api/stats/overview?from=&to= | 复盘统计 |

## 已执行的验证（本地实际验证结论）

验证环境：JDK 17 + Maven 3.9 + Node 22 + 本地 PostgreSQL 15（端口 5433，库 eldercare / care / care123）+ Redis 7（6379），
验证日期 2026-10-06。本沙箱无 Docker，故未执行 `docker compose`，改为本地直连数据库 + `java -jar` 启动后端做接口冒烟。

1. **后端编译打包**：`mvn package -DskipTests` 成功，产出 `target/eldercare.jar`；启动后自动新建
   `event_medical_refusal`、`event_revisit_task` 表并为 `help_event` 增加关联字段，种子账号/档案正常。
2. **前端构建**：`npm run build` 成功（Vite，含改造后的值班工作台与事件详情页）。
3. **拒医随访全链路 API 冒烟**（curl，worker 账号）：
   - 周桂兰电话求助（头晕、血压高）→ 研判身体不适/高 → 登记拒绝就医
     （钱医生记录血压 185/110、就诊建议、老人“怕花钱”、家属周建国“尊重老人意愿”、替代照护与监测、**电话复访**）；
     事件状态变为 `REFUSAL_TRACK`、`refusalBlocked=true`、自动生成 24 小时后到期任务；
   - **复访未完成时点回访归档被后端 400 拒绝**：“存在老人拒绝就医记录且复访尚未完成…不能直接关闭”；
   - 工作台 `GET /events/revisits/pending` 返回该待复访任务（老人、事件、方式、责任人、异常体征）；
   - 复访登记 **WORSENED 症状加重（上门复测 198/118、肢体麻木）**：
     生成升级事件 `ESCALATED_FROM_REFUSAL`、严重度直达 **CRITICAL**、带 `REFUSE_HOSPITAL,REFUSAL_ESCALATED` 标记；
     新事件时间线完整带入**原拒医记录、家属态度、医生建议、本次复访结论**及“不是新的普通求助”横幅；
     **重新通知**家属（沟通记录）、家庭医生、**120**、网格员；老人风险等级提升为 **CRITICAL**；原事件与新事件双向关联；
   - 第二条事件（吴德海）走 **DOCTOR 医生复诊 → RELIEVED 症状缓解**：`refusalBlocked` 变 false，随后可正常回访归档（CLOSED）；
   - 第三条事件（郑秀珍）复访截止时间设为过去，定时扫描（测试间隔 5s）自动把任务标记 **OVERDUE** 并在待办看板置顶；
     随后登记 **UNRELIEVED 症状未缓解**：生成 HIGH 升级事件，通知家属/医生/网格员（**不呼叫 120**），
     且本已 CRITICAL 的老人风险等级**只升不降**；
   - 待复访列表在任务全部完成后清空；已完成任务重复提交被拒（“该复访任务已完成”）；
     缺 `doctorAdvice` 触发 Bean Validation（must not be blank）；非法复访结果返回 400。
4. **鉴权回归**：新接口均走登录令牌，未携带令牌访问会被拦截（沿用既有 `AuthInterceptor`）。

> 说明：SSE 新增事件 `refusal-escalated`（拒医升级）、`revisit-overdue`（24 小时复访逾期）由后端在对应节点广播，
> 前端 MainLayout 已订阅并弹窗提醒；后端逻辑与推送点已随上述冒烟触发，浏览器端视觉效果未在无界面沙箱中截图。

### Docker 启动（交付要求，提供但本沙箱未执行）

仓库提供可一键启动的 `docker-compose.yml`（postgres / redis / backend / frontend）与各服务 Dockerfile，
在具备 Docker 的环境执行 `docker compose up -d --build` 后访问 http://localhost:8081 即可，
首次启动自动建表并写入测试账号与示例老人；拒医随访闭环的操作路径见上文第 5 节。
