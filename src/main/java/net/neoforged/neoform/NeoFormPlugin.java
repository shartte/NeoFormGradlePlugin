package net.neoforged.neoform;

import net.neoforged.neoform.dsl.NeoFormExtension;
import net.neoforged.neoform.tasks.CreatePatchWorkspace;
import net.neoforged.neoform.tasks.CreatePatches;
import net.neoforged.neoform.tasks.Decompile;
import net.neoforged.neoform.tasks.DownloadVersionArtifact;
import net.neoforged.neoform.tasks.DownloadVersionManifest;
import net.neoforged.neoform.tasks.PrepareJarForDecompiler;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.provider.Provider;
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;

public class NeoFormPlugin implements Plugin<Object> {
    @Override
    public void apply(Object target) {
        if (target instanceof Settings settings) {
            apply(settings);
        } else if (target instanceof Project project) {
            apply(project);
        } else {
            throw new InvalidUserDataException("Cannot apply plugin " + this + " to " + target);
        }
    }

    private void apply(Settings settings) {
        // We require access to various versions of JREs that are *not* the JRE hosting the build
        settings.getPlugins().apply(FoojayToolchainsConventionPlugin.class);

        settings.getRootProject().setName("neoform");

        var neoform = settings.getExtensions().create("neoForm", NeoFormExtension.class);
        settings.getGradle().getExtensions().add("neoForm", neoform);

        settings.dependencyResolutionManagement(spec -> {
            spec.getRepositoriesMode().set(RepositoriesMode.FAIL_ON_PROJECT_REPOS);
            spec.repositories(repositories -> {
                repositories.maven(repo -> {
                    repo.setName("Neoforge");
                    repo.setUrl("https://maven.neoforged.net/releases/");
                    repo.metadataSources(sources -> sources.gradleMetadata());
                    repo.content(content -> {
                        content.includeGroupAndSubgroups("net.neoforged");
                    });
                });
                repositories.maven(repo -> {
                    repo.setName("Mojang Meta");
                    repo.setUrl("https://maven.neoforged.net/mojang-meta/");
                    repo.metadataSources(sources -> sources.gradleMetadata());
                    repo.content(content -> {
                        content.includeModule("net.neoforged", "minecraft-dependencies");
                    });
                });
                repositories.maven(repo -> {
                    repo.setName("Maven for PR #27"); // https://github.com/neoforged/InstallerTools/pull/27
                    repo.setUrl(URI.create("https://prmaven.neoforged.net/InstallerTools/pr27"));
                    repo.content(content -> {
                        content.includeModule("net.neoforged.installertools", "binarypatcher");
                        content.includeModule("net.neoforged.installertools", "cli-utils");
                        content.includeModule("net.neoforged.installertools", "installertools");
                        content.includeModule("net.neoforged.installertools", "jarsplitter");
                        content.includeModule("net.neoforged.installertools", "problems-api");
                        content.includeModule("net.neoforged.installertools", "zipinject");
                    });
                });
                repositories.mavenCentral();
                repositories.maven(repo -> {
                    repo.setName("Mojang Minecraft Libraries");
                    repo.setUrl(URI.create("https://libraries.minecraft.net/"));
                    repo.metadataSources(sources -> sources.mavenPom());
                });
            });
        });

        var workspaceDir = new File(settings.getRootDir(), "workspace");
        if (workspaceDir.isDirectory()) {
            settings.include("workspace");
        }

        settings.getGradle().getLifecycle().beforeProject(project -> {
            if (project.getPath().equals(":workspace")) {
                project.getPlugins().apply(NeoFormWorkspacePlugin.class);
            } else {
                project.getPlugins().apply(NeoFormPlugin.class);
            }
        });
    }

    private void apply(Project project) {
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
            spec.setDescription("The executable jar containing the decompiler. May only resolve to a single file.");
            spec.getDependencies().addLater(neoForm.getDecompiler().getToolDependency().map(notation -> {
                return createDependency(dependencyFactory, notation);
            }));
        });
        var decompilerToolClasspath = configurations.resolvable("decompilerToolClasspath", spec -> {
            spec.extendsFrom(decompilerToolConfiguration.get());
        });
        var decompilerPluginsConfiguration = configurations.dependencyScope("decompilerPlugins", spec -> {
            spec.setDescription("Additional jars containing plugins for the decompiler.");
            spec.getDependencies().addAllLater(neoForm.getDecompiler().getPluginDependencies().map(notations -> {
                return new ArrayList<>(
                        notations.stream().map(notation -> createDependency(dependencyFactory, notation)).toList()
                );
            }));
        });
        var decompilerPluginsClasspath = configurations.resolvable("decompilerPluginsClasspath", spec -> {
            spec.extendsFrom(decompilerPluginsConfiguration.get());
        });

        var minecraftLibrariesClasspath = MinecraftLibraries.createConfiguration(project);

        var decompile = tasks.register("decompile", Decompile.class, task -> {
            task.getInput().set(prepareJarForDecompiler.flatMap(PrepareJarForDecompiler::getOutput));
            task.getToolClasspath().from(decompilerToolClasspath);
            task.getPluginsClasspath().from(decompilerPluginsClasspath);
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

    private static Provider<RegularFile> prefixFilenameWithVersion(NeoFormExtension neoForm, Provider<Directory> dirProvider, String suffix) {
        return dirProvider.zip(neoForm.getMinecraftVersion(), (dir, version) -> dir.file(version + "_" + suffix));
    }
}
