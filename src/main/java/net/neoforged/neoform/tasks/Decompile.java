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
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarFile;

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
    public abstract ConfigurableFileCollection getPluginsClasspath();

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
        var logFile = outputZip.toPath().resolveSibling(outputZip.getName() + "_decompiler.txt");

        var librariesFile = new File(getTemporaryDir(), "libraries.cfg");
        try (var writer = new BufferedWriter(new FileWriter(librariesFile, StandardCharsets.UTF_8))) {
            for (var file : getInputClasspath()) {
                writer.append("--add-external=").append(file.getAbsolutePath()).append('\n');
            }
        }

        var mainJar = getToolClasspath().getSingleFile();
        String mainClass;
        try (var jarFile = new JarFile(mainJar)) {
            mainClass = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
        }

        if (mainClass == null) {
            throw new GradleException("Decompiler tool jar is not executable: " + mainJar);
        }

        try (var output = new BufferedOutputStream(Files.newOutputStream(logFile))) {
            exec.javaexec(spec -> {
                spec.classpath(getToolClasspath(), getPluginsClasspath());
                spec.getMainClass().set(mainClass);
                spec.jvmArgs(getJvmArgs().get());
                spec.args(getArgs().get());
                spec.args("--log-level=WARN");
                spec.args("-cfg=" + librariesFile.getAbsolutePath());
                spec.args(inputJar.getAbsolutePath(), outputZip.getAbsolutePath());
                spec.setStandardOutput(output);
                spec.setErrorOutput(output);

                // Dump the arguments
                var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                try {
                    writer.append("Running Decompiler using:\n");
                    writer.append(" Tool Classpath:\n");
                    for (var file : getToolClasspath()) {
                        writer.append("  - ").append(file.getAbsolutePath()).append('\n');
                    }
                    writer.append(" Plugin Classpath:\n");
                    for (var file : getPluginsClasspath()) {
                        writer.append("  - ").append(file.getAbsolutePath()).append('\n');
                    }
                    writer.append(" JVM Args:\n");
                    for (var arg : spec.getAllJvmArgs()) {
                        writer.append("  - ").append(arg).append('\n');
                    }
                    writer.append(" Args:\n");
                    for (var arg : spec.getArgs()) {
                        writer.append("  - ").append(arg).append('\n');
                    }
                    writer.flush();
                    output.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        if (!outputZip.isFile()) {
            throw new GradleException("Decompiler error");
        }
    }
}
