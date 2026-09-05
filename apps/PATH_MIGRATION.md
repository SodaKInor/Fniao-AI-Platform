# 第 8 批阶段 A 应用路径迁移清单

本清单记录从规划提交 `a7d38b9dedf0e9d5e4a1fb2189619830bc62feb5` 开始的应用根迁移。阶段 A 只改变顶层位置，不改变 Java 包名、前端功能模块、Maven/npm 工程内容或业务行为。

## 路径映射

| 旧路径 | 新路径 | 跟踪文件数 | 本阶段处置 |
|---|---|---:|---|
| `backend-github/` | `apps/backend/` | 2255 | 整体 `git mv`，内部 Maven 模块和相对路径保持不变 |
| `frontend-vue/` | `apps/frontend/` | 611 | 整体 `git mv`，内部 Vue 工程和相对路径保持不变 |

执行路径修复前，Git 对两个应用根下的 2866 个跟踪文件均识别为 100% 内容重命名；阶段 A 的内容修改仅限迁移后的忽略规则、测试夹具定位和本清单。

## 稳定入口与不变量

- 后端构建根：`apps/backend/pom.xml`，Java 目标版本仍为 1.8。
- 前端构建根：`apps/frontend/package.json`，包名仍为 `vue-antd-jeecg`。
- 后端 AI Java 包根仍为 `org.jeecg.modules.ai`，功能目录仍为 `asset`、`capability`、`image`、`job`、`legacy`、`operations`、`provider`、`result`、`stream`、`video`。
- 前端 AI 功能目录仍为 `asset`、`capability`、`image`、`job`、`legacy`、`result`、`stream`、`video`。
- 后端许可证 SHA-256 仍为 `1eb85fc97224598dad1852b5d6483bbcf0aa8608790dcc657a5a2a761ae9c8c6`。
- 前端许可证 SHA-256 仍为 `96c444858d39b5604412c32ef35e60c29c001daf893c497d71a0810ae2d7511c`。

直接构建入口：

```sh
cd apps/backend
mvn -Pdocker-core -DskipTests=true package

cd apps/frontend
nvm use 16.20.2
npm ci --legacy-peer-deps
npm run build
```

父 POM 仍默认跳过测试以保持原构建行为，但 Surefire 开关现可由验证流程用 `-DskipTests=false` 覆盖。前端沿用现有部署基线的 Node `16.20.2`；Node 24 与本项目旧 webpack/Terser 的 OpenSSL 实现不兼容，不属于受支持的构建运行时。

AI 测试夹具不依赖旧的构建绝对路径；默认从当前 Maven 模块向上定位仓库根的 `remote-inference/fixtures`，也可通过 `-Dai.test.examples=<path>` 明确指定。

## 留给后续阶段

- 阶段 A 不建立或迁移最终的 `database/`、`remote-inference/`、`docs/remote-inference/`、`deploy/remote-inference/`。
- 根 Compose、Docker 构建上下文、README、Graphify/Serena 管理脚本和其余活动路径已在阶段 D 统一修复。
- 01—07 历史验收证据中的旧路径保持原样，作为当时执行环境的可追溯记录。

## 阶段 A 验证结果

- Java 8：`docker` 中使用 Temurin 8，从 `apps/backend` 完成 `docker-core` profile 的 9 项 Maven reactor 包构建，结果 `BUILD SUCCESS`。
- Java 8 测试：provider、协议、TLS、成果读取及开发 stub 边界测试 29/29 通过；开发 stub 的在线用例按设计在未提供环境变量时跳过外呼。
- Vue 构建：使用 Node `16.20.2` 从 `apps/frontend` 完成生产构建；仅保留已有的 CSS 顺序、Browserslist 和资源体积警告。
- Vue 静态检查：`src/modules/ai` 下 JavaScript/Vue 文件的 ESLint 检查通过。
- Vue 行为测试：图片、上传视频、实时流、轮询、权限资源和退役入口共 27/27 通过。
- 内容对照：后端 AI 主源码、前端 AI 模块和 Java 包声明与移动前逐文件一致；两个许可证哈希不变。
- 依赖安装：`npm ci --legacy-peer-deps` 通过；旧依赖树的审计告警未在纯路径迁移中升级或改写。
