package net.neoforged.neoform.dsl;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class ToolSettings {
    public abstract Property<String> getMainClass();

    public abstract ListProperty<Object> getClasspath();

    public abstract ListProperty<String> getArgs();

    public abstract ListProperty<String> getJvmArgs();

    public abstract Property<String> getRepositoryUrl();

    public abstract Property<String> getJavaVersion();
}
