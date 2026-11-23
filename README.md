# 墨语 · Moyu

一个 DeepSeek 风格的 Android AI 小说创作客户端。支持 **OpenAI 兼容 API** 与 **本地 GGUF 模型离线推理**（llama.cpp），面向中文长篇小说创作与续写。

## 功能

- **DeepSeek 风格界面**：深色主题、左侧会话抽屉、历史对话窗口、流式输出、停止生成、消息复制 / 重新生成。
- **三种模式**：日常聊天、小说创作、本地智能体；小说创作只是其中一个模式。
- **上下文记忆**：每次生成可选“全部对话”或“只看重要对话”；重要消息自动识别、可手动收藏、可删除。
- **联网搜索**：输入框上方“联网”开关，内置 Bing RSS 搜索，结果会作为工具上下文交给模型。
- **本地智能体**：本地执行计算器、时间、电量、记忆检索，联网开启时还能搜索。
- **图片识别**：最多 4 张图，自动压缩 + OCR 提取文字；有视觉模型时走视觉 API，本地模型用 OCR 文字兜底。
- **权限**：首次进入申请相机、图片权限，已授权不重复申请；模型文件通过系统文件选择器导入，不需要整个存储权限。
- **本地模型管理**：小窗口直接删除 / 导入 / 查看占用，提示推荐骁龙 8 Gen 2 及以上。
- **两种模型来源**
  - 在线：DeepSeek、通义千问（DashScope）、智谱 GLM、Kimi、OpenAI、任意 OpenAI 兼容接口。
  - 本地：多模型源（HF 镜像 / Hugging Face / 魔搭 ModelScope / 自定义 HF 兼容源），搜索 0.5B–235B 的 GGUF，下载到手机后用 llama.cpp 在 arm64 CPU 上离线推理；支持删除、切换模型。
- **识图**：拍照或从相册选图，配合视觉模型（如 `qwen3-vl-plus`、`glm-4.5v`、`gpt-4o`）识图写作。
- **深度思考**：解析 `reasoning_content` 与 `思考...` 标签，思考过程可折叠查看并显示用时。
- **文件与相机**：支持 txt / md / json / csv / 代码 / PDF / DOCX 文本提取，图片自动压缩后以 data URL 发送。
- **导入历史聊天**：支持 ChatGPT `conversations.json`、通用 JSON、`用户：/助手：` 文本、Markdown、PDF、DOCX。
- **小说创作预设**：内置网文写作系统提示词、续写 / 开篇 / 设定建议；日常聊天与小说提示词可分别编辑。
- **上下文管理**：本地模型自动按 token 预算截断较早对话；可调上下文 2048 / 4096 / 8192（2048 约 1500 字、4096 约 3000 字、8192 约 6000 字）、线程数、温度、最大生成长度。

## 下载与安装

在仓库的 **Actions** 页面打开最近一次成功构建，下载 `moyu-apk` 工件并安装 `app-release.apk`。
要求：Android 8.0+（minSdk 26），arm64-v8a 手机。

## 自己构建

GitHub Actions 会在 push 到 `main` 或手动 `workflow_dispatch` 时：

1. 安装 JDK 17、Android SDK 35、NDK 27.2、CMake 3.22.1；
2. 克隆 `llama.cpp` v1.0.0 到 `app/src/main/cpp/llama.cpp`；
3. `gradle :app:assembleRelease` 生成签名 APK 并上传工件。

本地构建需在项目根目录执行同样的 llama.cpp 克隆，然后 `gradle :app:assembleRelease`。

## 使用

1. 打开 App → 左上角菜单 → **设置** → **API 服务**，选服务商并填入自己的 API Key（或直接使用本地模型）。
2. 想离线用：**模型库** → 选择模型源（默认 HF 镜像，国内可切魔搭）→ 搜 `Qwen3` / 选大小筛选 → 选 `Q4_K_M` → 下载 → 点“使用”。自定义源在 **设置 → 模型源** 里添加。
3. 回到对话，直接输入或点建议卡片；写作时可上传设定文档、人物表、参考章节。

## 隐私

- API Key 仅保存在应用私有目录（`files/moyu/settings.json`），不会上传。
- 本地模型与对话记录同样只存在本机；卸载 App 会删除。
- 本地模型下载走系统 DownloadManager，下载源为 hf-mirror.com。

## 说明

- 本地推理为纯 CPU（arm64），4B Q4 在主流手机上可达到可用的生成速度；8B 建议 12GB 内存。
- 本地模型不支持图片输入（图片会走在线视觉模型）；文本、文件、思考、停止生成均可用。
- 内置服务商的模型名会随时间变化，可在设置中直接改写。


## 参考的开源项目

本项目的交互与本地模型管理思路参考了这些完全基于手机运行的开源项目（未直接复制代码）：

- [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai)：手机本地 GGUF、模型下载与管理。
- [ChatterUI](https://github.com/Vali-98/ChatterUI)：本地 / 远程双模式、会话与模型管理。
- [Maid](https://github.com/Mobile-Artificial-Intelligence/maid)：Flutter + llama.cpp 的本地推理与角色聊天。
- [LLM-Hub](https://github.com/timmyy123/LLM-Hub)：本地聊天、记忆、联网搜索、OCR、智能体工具的整合方式。
