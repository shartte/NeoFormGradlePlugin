package net.neoforged.neoform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class Decompile extends DefaultTask {
    private final ExecOperations exec;

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Input
    public abstract ListProperty<String> getArgs();

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Classpath
    public abstract ConfigurableFileCollection getToolClasspath();

    @Classpath
    public abstract ConfigurableFileCollection getInputClasspath();

    @Inject
    public Decompile(ExecOperations exec) {
        this.exec = exec;
    }

    @TaskAction
    public void execute() throws IOException {
        var inputJar = getInput().getAsFile().get();
        var outputZip = getOutput().getAsFile().get();

        var librariesFile = new File(getTemporaryDir(), "libraries.cfg");
        try (var writer = new BufferedWriter(new FileWriter(librariesFile, StandardCharsets.UTF_8))) {
            for (var file : getInputClasspath()) {
                writer.append("--add-external=").append(file.getAbsolutePath()).append('\n');
            }
        }

        exec.javaexec(spec -> {
            spec.classpath(getToolClasspath());
            spec.jvmArgs(getJvmArgs().get());
            spec.args(getArgs().get());
            spec.args("--log-level=WARN");
            spec.args("--cfg=" + librariesFile.getAbsolutePath());
            spec.args(inputJar.getAbsolutePath(), outputZip.getAbsolutePath());
        });

        if (!outputZip.isFile()) {
            throw new GradleException("Decompiler error");
        }
    }
}
