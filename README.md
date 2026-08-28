# 喊话 Acc2

这是 Android 12 车机使用的第一版车外喊话器测试工程。

## 功能
- 自动扫描 Android 暴露的输入/输出音频设备
- 选择指定麦克风
- 选择指定输出设备
- 长按说话，松开停止
- 实时 PCM 音频传输
- 输出音量调节
- 重新扫描设备

## 手动打包
Windows 运行 `手动打包.bat`。
成功后根目录生成 `喊话.apk`。

## JVM 修复
Java 和 Kotlin 均固定为 JVM 17，解决：
`compileDebugJavaWithJavac (1.8)` 与 `compileDebugKotlin (17)` 不兼容的问题。

## 关于“全部权限”
Manifest 已加入 Android 12 常见的音频、蓝牙、网络、存储、唤醒、前台服务等权限。
但 Android 的危险权限仍可能需要运行时授权；普通 APK 不能自动获得系统/签名级权限，也不能自动继承另一个 APK 的授权。

## Acc2 替换说明
当前 applicationId 为 `com.carpa.acc2`，versionCode 为 2。
如果要真正覆盖安装现有 Acc2，必须与原 Acc2 使用相同 applicationId 和可接受的签名证书；如果原 APK 的包名/签名不同，Android 会拒绝直接升级。
