package buddha.gradle;

import lombok.Setter;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;


public class ShellLauncherTask extends DefaultTask {
    private static final String DIST_DIR_DEFAULT = "dist";
    private static final String BIN_DIR_DEFAULT = "bin";
    private static final String LIB_DIR_DEFAULT = "lib";
    private static final String LOGGER_PREFIX = "[SHELL-LAUNCHER]: ";

    @Setter
    private String distDir = DIST_DIR_DEFAULT;
    @Setter
    private String binDir = BIN_DIR_DEFAULT;
    @Setter
    private String libDir = LIB_DIR_DEFAULT;
    @Setter
    private String mainClass;
    @Setter
    private boolean currentPlatformOnly = true;
    @Setter
    private String appName;
    @Setter
    private List<String> jvmArgs = new ArrayList<>();

    private final List<String> runtimeJarClasspath = new ArrayList<>();

    private final OSPlatfromEnum osPlatform;

    public ShellLauncherTask() {
        final String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            this.osPlatform = OSPlatfromEnum.WIN;
        } else if (osName.contains("mac")
                || osName.contains("nix")
                || osName.contains("nux")
                || osName.contains("aix")) {
            this.osPlatform = OSPlatfromEnum.UNIX_LIKE;
        } else {
            throw new GradleException("unsupported platform: " + osName);
        }
    }

    private void prepareDistSkeleton() {
        final Project project = getProject();
        final Directory distDir = project.getLayout().getBuildDirectory().dir(this.distDir).get();
        if (project.file(distDir).exists()) {
            if (!project.delete(distDir)) {
                throw new GradleException(LOGGER_PREFIX + "can't delete dir");
            }
        }
        project.mkdir(distDir.dir(this.binDir));
        project.mkdir(distDir.dir(this.libDir));
        project.getLogger().lifecycle("{}The skeleton of the publishing directory is ready", LOGGER_PREFIX);
    }

    private void copyRuntimeDependencies() {
        final Project project = getProject();
        final Set<File> runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath").resolve();
        final Directory runtimeDir = project.getLayout().getBuildDirectory().get().dir(this.distDir).dir(this.libDir);
        project.copy(copySpec -> {
            copySpec.from(runtimeClasspath.toArray())
                    .into(runtimeDir)
                    .include("*.jar");
        });
        runtimeClasspath.forEach(it -> {
            project.getLogger().lifecycle("{}Copy the {} into {}", LOGGER_PREFIX, it, runtimeDir);
        });
        runtimeClasspath.stream().map(File::getName).forEach(runtimeJarClasspath::add);
    }

    private void copyMainJar() {
        final Project project = getProject();
        final Jar javaJarTask = project.getTasks().named("jar", Jar.class).get();
        final RegularFile mainJarFile = javaJarTask.getDestinationDirectory().file(javaJarTask.getArchiveFileName()).get();
        final Directory runtimeDir =
                project.getLayout().getBuildDirectory().get().dir(this.distDir).dir(this.libDir);
        project.copy(spec -> {
            spec.from(mainJarFile).into(runtimeDir).include("*.jar");
        });
        project.getLogger().lifecycle("{}Copy the {} into {}", LOGGER_PREFIX, mainJarFile, runtimeDir);
        runtimeJarClasspath.add(javaJarTask.getArchiveFileName().get());
        // 重新确认appName和mainClass
        if (appName == null) {
            appName = javaJarTask.getArchiveBaseName().get();
            project.getLogger().lifecycle("{}use the {} as 'appName'", LOGGER_PREFIX, appName);
        }
        if (mainClass == null) {
            this.mainClass = Optional.ofNullable(javaJarTask.getManifest())
                    .map(Manifest::getAttributes)
                    .map(it -> (String)it.get("Main-Class"))
                    .orElseThrow(() -> new GradleException(LOGGER_PREFIX + "Please set the 'mainClass'"));
        }
    }

    /**
     * 生成Windows系统的bat启动脚本
     */
    private void generateWinShellLauncher() {
        Objects.requireNonNull(appName, "'appName' is null");
        Objects.requireNonNull(mainClass, "'mainClass' is null");
        final Project project = getProject();
        final StringBuilder batContent = new StringBuilder();
        // 设置脚本必要的全局变量
        batContent.append("@ECHO OFF").append("\n");
        batContent.append("SET APP_NAME=").append(appName).append("\n");
        batContent.append("SET APP_JVM_ARGS=").append(String.join(" ", jvmArgs)).append("\n");
        batContent.append("SET APP_MAIN_CLASS=").append(mainClass).append("\n");
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        // 读取模板主体内容
        try (final InputStream is = classLoader.getResourceAsStream("win-shell-template.bat")) {
            final byte[] buffer = new byte[is.available()];
            is.read(buffer);
            batContent.append(new String(buffer, StandardCharsets.UTF_8)).append("\n");
        } catch (IOException ex) {
            throw new GradleException(LOGGER_PREFIX+"can't open the file 'win-shell-template.bat'", ex);
        }
        batContent.append("::----------------------------------------- Classpath --------------------------------------------\n");
        // 设置classpath
        batContent.append("SET \"CLASS_PATH=%APP_HOME%\\")
                .append(this.libDir).append("\\").append(runtimeJarClasspath.get(0)).append("\n");
        for (int i=1, bound=runtimeJarClasspath.size(); i<bound; i++) {
            final String jar = runtimeJarClasspath.get(i);
            batContent.append("SET \"CLASS_PATH=%CLASS_PATH%;%APP_HOME%\\")
                    .append(this.libDir).append("\\").append(jar).append("\n");
        }
        batContent.append("::---------------------------------------- Start Application --------------------------------------\n");
        // 这里用来切换jvm的工作目录
        batContent.append("CD \"%APP_HOME%\"").append("\n");
        batContent.append("\"%JAVA_EXE%\"").append(" ^\n")
                .append("-cp \"%CLASS_PATH%\" ^\n")
                .append("%APP_JVM_ARGS% ^\n")
                .append("%APP_MAIN_CLASS% ^\n")
                .append("%*\n");
        project.getLogger().lifecycle("{}Generated bat file content is: ", LOGGER_PREFIX);
        project.getLogger().lifecycle(batContent.toString());
        final RegularFile batFile =
                project.getLayout().getBuildDirectory().get().dir(this.distDir).dir(this.binDir).file(appName + ".bat");
        try {
            Files.write(
                    Paths.get(batFile.toString()),
                    batContent.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new GradleException(LOGGER_PREFIX+"create bat file error", e);
        }
    }

    private void generateUnixLikeShellLauncher() {

    }

    @TaskAction
    public void actionPerformed() {
        // 准备发布目录框架: dist/bin; dist/runtime;
        prepareDistSkeleton();
        // 拷贝运行时依赖
        copyRuntimeDependencies();
        // 拷贝项目生成的jar包
        copyMainJar();
        // 生成启动的shell脚本
        if (currentPlatformOnly) {
            // 仅生成当前操作系统的启动脚本
            if (osPlatform == OSPlatfromEnum.WIN) {
                generateWinShellLauncher();
            } else if (osPlatform == OSPlatfromEnum.UNIX_LIKE) {
                generateUnixLikeShellLauncher();
            }
        } else {
            generateUnixLikeShellLauncher();
            generateWinShellLauncher();
        }

    }
}
