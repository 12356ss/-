# School-Wifi 自动认证（Android 项目源码）

目标：Android 16；校园认证入口 `http://10.254.253.250/`。

## 当前版本功能
- WebView 打开学校现有认证页。
- 学号和密码由用户在本机输入。
- 凭据以 Android Keystore 生成的 AES-GCM 密钥加密后保存到本机应用私有存储。
- 尝试根据表单字段名称自动填写。
- 可选开启自动提交；默认关闭，建议先确认页面字段匹配。
- 不包含账号密码、Cookie 或令牌。

## 构建 APK（推荐 Android Studio）
1. 在电脑安装 Android Studio（若没有电脑，可考虑 GitHub Actions 云构建；需要先把项目上传到自己的仓库）。
2. 解压此 ZIP，在 Android Studio 中打开 `SchoolWifiAutoLogin` 文件夹。
3. 等待 Gradle 同步完成。
4. 选择 `Build > Build APK(s)`。
5. APK 通常在 `app/build/outputs/apk/debug/app-debug.apk`。

本 ZIP 未附 Gradle Wrapper，也未在此环境实际编译，因此不能声称已经生成或测试 APK。

## 重要限制与安全说明
- 认证页面使用 HTTP，校园网内流量不具备 HTTPS 的传输保护；请只在可信的学校网络使用。
- 自动填写使用通用字段名启发式匹配。学校页面若用自定义脚本、验证码、跨域跳转或非标准字段，可能无法填写/提交。
- 自动提交默认关闭；首次先手动确认学号、密码是否落入正确字段，再开启。
- WebView 的 JavaScript bridge 只应加载可信学校认证页面。代码限制了页面导航到认证主机，但子资源与页面脚本仍由学校服务器控制。
- 本版本不包含 Wi-Fi 重连后台监听、通知自动弹出或“真正无人值守自动登录”。它是可构建的第一版验证原型。
- Android 16 对后台活动有限制；需要进一步自动触发时，应单独加入连接状态监听与通知流程，并在真实设备测试。
- 卸载应用会删除应用私有凭据数据；如更换设备，需要重新输入。
