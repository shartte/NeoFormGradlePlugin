package net.neoforged.neoform.dsl;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class DecompilerSettings {
    public abstract Property<Object> getToolDependency();

    public abstract ListProperty<Object> getPluginDependencies();

    public abstract ListProperty<String> getArgs();

    public abstract ListProperty<String> getJvmArgs();
}
