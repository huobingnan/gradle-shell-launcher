package buddha.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;

/**
 * ShellLauncher插件入口类
 */
public class ShellLauncherPlugin implements Plugin<Project>, Constants {

    @Override
    public void apply(Project project) {
        Logger logger = project.getLogger();
        logger.lifecycle("{}current version is {}", LOGGER_PREFIX, VERSION);
        // 确保导入java plugin
        PluginContainer plugins = project.getPlugins();
        plugins.apply(JavaPlugin.class);
        TaskContainer taskContainer = project.getTasks();
        ShellLauncherTask launcherTask =
                taskContainer.register(SHELL_LAUNCHER_TASK_NAME, ShellLauncherTask.class).get();
        launcherTask.dependsOn("build", "jar");
    }
}
