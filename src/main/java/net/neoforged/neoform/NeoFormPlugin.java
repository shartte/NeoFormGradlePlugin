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
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.provider.Provider;
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;

public class NeoFormPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
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
                project.getPlugins().apply(NeoFormProjectPlugin.class);
            }
        });
    }

}
