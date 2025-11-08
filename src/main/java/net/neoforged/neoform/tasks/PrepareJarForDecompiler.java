package net.neoforged.neoform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

public abstract class PrepareJarForDecompiler extends DefaultTask {
    private final ExecOperations exec;

    @InputFile
    public abstract RegularFileProperty getClient();

    @InputFile
    public abstract RegularFileProperty getServer();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Classpath
    public abstract ConfigurableFileCollection getToolClasspath();

    @Inject
    public PrepareJarForDecompiler(ExecOperations exec) {
        this.exec = exec;
    }

    @TaskAction
    public void execute() {
        var clientJar = getClient().getAsFile().get();
        var serverJar = getServer().getAsFile().get();
        var joinedJar = getOutput().getAsFile().get();

        exec.javaexec(spec -> {
            spec.classpath(getToolClasspath());
            spec.args("--task", "PROCESS_MINECRAFT_JAR", "--input", clientJar.getAbsolutePath(), "--input", serverJar.getAbsolutePath(), "--output", joinedJar.getAbsolutePath());
        });
    }
}
