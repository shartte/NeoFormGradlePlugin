package net.neoforged.neoform;

import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.tasks.CreateConfig;
import net.neoforged.neoform.tasks.CreatePatchWorkspace;
import net.neoforged.neoform.tasks.CreatePatches;
import net.neoforged.neoform.tasks.Decompile;
import net.neoforged.neoform.tasks.DownloadVersionArtifact;
import net.neoforged.neoform.tasks.DownloadVersionManifest;
import net.neoforged.neoform.tasks.PrepareJarForDecompiler;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

public class NeoFormProjectPlugin implements Plugin<Project> {
    public void apply(Project project) {
        if (project.getRootProject() != project) {
            throw new InvalidUserCodeException("This plugin should only be applied to the root project.");
        }

        var neoForm = (NeoFormExtension) project.getGradle().getExtensions().getByName("neoForm");
        var tasks = project.getTasks();
        var dependencyFactory = project.getDependencyFactory();
        var configurations = project.getConfigurations();
        var buildDir = project.getLayout().getBuildDirectory();
        var inputsDir = buildDir.dir("neoform/inputs");
        var minecraftVersion = neoForm.getMinecraftVersion();

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Download the Version Manifest
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var downloadManifest = tasks.register("downloadVersionManifest", DownloadVersionManifest.class, task -> {
            task.getMinecraftVersion().set(minecraftVersion);
            task.getVersionManifestUrl().set(neoForm.getMinecraftVersionManifestUrl());
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "version.json"));
        });
        var versionManifest = downloadManifest.flatMap(DownloadVersionManifest::getOutput);

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Download Artifacts from Version Manifest
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var downloadClient = tasks.register("downloadClient", DownloadVersionArtifact.class, task -> {
            task.getVersionManifest().set(versionManifest);
            task.getArtifactName().set("client");
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "client.jar"));
        });
        var downloadServer = tasks.register("downloadServer", DownloadVersionArtifact.class, task -> {
            task.getVersionManifest().set(versionManifest);
            task.getArtifactName().set("server");
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "server.jar"));
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Merge the client and server to get the input for the decompiler
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var prepareJarToolConfiguration = configurations.dependencyScope("prepareJarTool", spec -> {
            var dependencies = project.getDependencyFactory();
            spec.getDependencies().add(dependencies.create("net.neoforged.installertools:installertools:3.0.20:fatjar"));
        });
        var prepareJarToolClasspath = configurations.resolvable("prepareJarToolClasspath", spec -> {
            spec.extendsFrom(prepareJarToolConfiguration.get());
        });
        var prepareJarForDecompiler = tasks.register("prepareJarForDecompiler", PrepareJarForDecompiler.class, task -> {
            task.getClient().set(downloadClient.flatMap(DownloadVersionArtifact::getOutput));
            task.getServer().set(downloadServer.flatMap(DownloadVersionArtifact::getOutput));
            task.getToolClasspath().from(prepareJarToolClasspath);
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "joined.jar"));
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Decompile
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var decompilerToolConfiguration = configurations.dependencyScope("decompilerTool", spec -> {
            spec.setDescription("The classpath for running the decompiler.");
            spec.getDependencies().addLater(neoForm.getDecompiler().getClasspath().map(notation -> {
                return createDependency(dependencyFactory, notation);
            }));
        });
        var decompilerToolClasspath = configurations.resolvable("decompilerToolClasspath", spec -> {
            spec.extendsFrom(decompilerToolConfiguration.get());
        });

        var minecraftLibrariesClasspath = MinecraftLibraries.createConfiguration(project);

        var decompile = tasks.register("decompile", Decompile.class, task -> {
            task.getInput().set(prepareJarForDecompiler.flatMap(PrepareJarForDecompiler::getOutput));
            task.getMainClass().set(neoForm.getDecompiler().getMainClass());
            task.getClasspath().from(decompilerToolClasspath);
            task.getInputClasspath().from(minecraftLibrariesClasspath);
            task.getJvmArgs().set(neoForm.getDecompiler().getJvmArgs());
            task.getArgs().set(neoForm.getDecompiler().getArgs());
            task.getOutput().set(prefixFilenameWithVersion(neoForm, inputsDir, "sources.zip"));
        });

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Workflow Tasks
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////
        var createPatchWorkspace = tasks.register("createPatchWorkspace", CreatePatchWorkspace.class, task -> {
            task.setGroup("neoform");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getWorkspace().set(project.getLayout().getProjectDirectory().dir("workspace"));
        });
        var createPatchWorkspaceForUpdate = tasks.register("createPatchWorkspaceForUpdate", CreatePatchWorkspace.class, task -> {
            task.setGroup("neoform");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getWorkspace().set(project.getLayout().getProjectDirectory().dir("workspace"));
            task.getUpdateMode().set(true);
        });
        var createPatches = tasks.register("createPatches", CreatePatches.class, task -> {
            task.setGroup("neoform");
            task.getPatchesDir().set(project.getLayout().getProjectDirectory().dir("src/patches"));
            task.getSourcesZip().set(decompile.flatMap(Decompile::getOutput));
            task.getModifiedSources().set(project.getLayout().getProjectDirectory().dir("workspace/src/main/java"));
        });
        var createConfig = tasks.register("createConfig", CreateConfig.class, task -> {
            task.setGroup("neoform");
            task.setDescription("Creates the NeoForm config JSON for use by tooling from the configuration in the current project");
            task.getOutput().set(project.getLayout().getBuildDirectory().file("neoform/config.json"));
            task.getVersion().set(project.provider(() -> project.getVersion().toString()));
            task.getDecompiler().set(neoForm.getDecompiler());
            task.getPreProcessJar().set(neoForm.getPreProcessJar());
            task.getEncoding().set("UTF-8");
            task.getJavaVersion().set(neoForm.getJavaVersion());
        });
    }

    private static Dependency createDependency(DependencyFactory dependencyFactory, Object notation) {
        if (notation instanceof CharSequence charSequence) {
            return dependencyFactory.create(charSequence);
        } else if (notation instanceof Project project) {
            return dependencyFactory.create(project);
        } else if (notation instanceof FileCollection files) {
            return dependencyFactory.create(files);
        } else {
            throw new InvalidUserCodeException("Invalid dependency notation: " + notation);
        }
    }

    static Provider<RegularFile> prefixFilenameWithVersion(NeoFormExtension neoForm, Provider<Directory> dirProvider, String suffix) {
        return dirProvider.zip(neoForm.getMinecraftVersion(), (dir, version) -> dir.file(version + "_" + suffix));
    }
}
