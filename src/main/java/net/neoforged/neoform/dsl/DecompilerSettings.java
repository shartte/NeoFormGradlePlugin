package net.neoforged.neoform.dsl;

import org.gradle.api.provider.ListProperty;

public abstract class DecompilerSettings {
    public abstract ListProperty<String> getClasspath();

    public abstract ListProperty<String> getArgs();

    public abstract ListProperty<String> getJvmArgs();
}
