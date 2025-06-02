package buddha.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ShellLauncherPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        final ShellLauncherTask task = project.getTasks().register("shellLauncher", ShellLauncherTask.class).get();
        task.dependsOn("build", "jar");
    }
}
