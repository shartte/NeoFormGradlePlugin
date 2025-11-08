package net.neoforged.neoform.dsl;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class NeoFormExtension {
    private static final Logger LOG = LoggerFactory.getLogger(NeoFormExtension.class);

    private final DecompilerSettings decompiler;

    public static NeoFormExtension fromProject(Project project) {
        return (NeoFormExtension) project.getGradle().getExtensions().getByName("neoForm");
    }

    @Inject
    public NeoFormExtension(ObjectFactory objects, ProviderFactory providers) {
        // Retrieve the current branch name using the Git cli command.
        var gitBranchExec = providers.exec(execSpec -> {
            execSpec.commandLine("git", "symbolic-ref", "--short", "HEAD").setIgnoreExitValue(true);
        });
        getCurrentBranchName().set(gitBranchExec.getResult().zip(gitBranchExec.getStandardOutput().getAsText(), (result, output) -> {
            if (result.getExitValue() == 0) {
                String trimmed = output.trim();
                return trimmed.isEmpty() ? null : trimmed;
            } else {
                LOG.info("Getting current branch name using git failed with exit code {}: {}", result.getExitValue(), output);
                return null;
            }
        }));
        getCurrentBranchName().finalizeValueOnRead();

        // Derive the Minecraft version from the branch name by default.
        getMinecraftVersion().convention(getCurrentBranchName().map(NeoFormExtension::getVersionFromBranchName));

        getMinecraftDependencies().convention(getMinecraftVersion().map(minecraftVersion -> {
            return new ArrayList<>(List.of("net.neoforged:minecraft-dependencies:" + minecraftVersion));
        }));

        decompiler = objects.newInstance(DecompilerSettings.class);
    }

    public DecompilerSettings getDecompiler() {
        return decompiler;
    }

    public void decompiler(Action<? super DecompilerSettings> action) {
        action.execute(decompiler);
    }

    private static String getVersionFromBranchName(String branchName) {
        var branchSegments = branchName.split("/");
        if (branchSegments[0].equals("snapshot") && branchSegments.length == 3) {
            return branchSegments[2];
        }
        return null;
    }

    protected abstract Property<String> getCurrentBranchName();

    public abstract Property<String> getMinecraftVersion();

    public abstract ListProperty<String> getMinecraftDependencies();

    public abstract Property<String> getMinecraftVersionManifestUrl();

    public abstract ListProperty<String> getAdditionalCompileDependencies();

    public abstract ListProperty<String> getAdditionalRuntimeDependencies();
}
