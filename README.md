# Gradle Shell Launcher
完成Java应用功能的开发并不是项目的结束，你需要为你的应用制作一个分发发布的形式。
Gradle Shell Launcher是一个Gradle插件，它可以为你的Java项目制作一个shell启动器：
1. windows环境下为bat脚本
2. unix-like环境下为shell脚本

## 插件特性
### 💕自动生成固定形式的分发目录
1. bin：启动器shell脚本所在目录
2. lib：项目依赖的jar包所在目录，在启动时会自动设置该目录下的jar包为classpath
### 💕生成exe可执行文件壳子
在windows环境下处理生成启动的bat脚本外，还会生成一个`.exe`文件壳子用于启动应用程序