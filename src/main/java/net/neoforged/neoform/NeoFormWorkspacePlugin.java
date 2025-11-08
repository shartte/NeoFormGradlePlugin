package net.neoforged.neoform;

import net.neoforged.minecraftdependencies.MinecraftDependenciesPlugin;
import net.neoforged.neoform.dsl.NeoFormExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.ArrayList;
import java.util.Collections;

public class NeoFormWorkspacePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        project.getPlugins().apply(MinecraftDependenciesPlugin.class);

        var tasks = project.getTasks();
        tasks.withType(JavaCompile.class).configureEach(task -> {
            Collections.addAll(task.getOptions().getCompilerArgs(), "-Xmaxerrs", "9999");
        });

        var neoForm = NeoFormExtension.fromProject(project);

        var dependencyFactory = project.getDependencyFactory();
        var configurations = project.getConfigurations();
        configurations.named("implementation").configure(spec -> {
            var classpath = neoForm.getMinecraftDependencies();
            spec.getDependencies().addAllLater(classpath.map(notations -> {
                var result = new ArrayList<Dependency>(notations.size());
                for (var notation : notations) {
                    result.add(dependencyFactory.create(notation));
                }
                return result;
            }));
        });
        configurations.named("compileOnly").configure(spec -> {
            var classpath = neoForm.getAdditionalCompileDependencies();
            spec.getDependencies().addAllLater(classpath.map(notations -> {
                var result = new ArrayList<Dependency>(notations.size());
                for (var notation : notations) {
                    result.add(dependencyFactory.create(notation));
                }
                return result;
            }));
        });
        configurations.named("runtimeOnly").configure(spec -> {
            var classpath = neoForm.getAdditionalRuntimeDependencies();
            spec.getDependencies().addAllLater(classpath.map(notations -> {
                var result = new ArrayList<Dependency>(notations.size());
                for (var notation : notations) {
                    result.add(dependencyFactory.create(notation));
                }
                return result;
            }));
        });
    }
}
