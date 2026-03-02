package org.apache.grails.core.plugins;

import org.springframework.core.io.Resource;

/**
 * Lightweight value class holding plugin metadata needed for ordering and
 * configuration loading. Wraps {@link GrailsPluginLoadMetadata}
 * with an additional configuration resource reference.
 */
public final class GrailsPluginInfo {

    private final GrailsPluginLoadMetadata metadata;
    private final GrailsPluginDescriptor pluginDescriptor;
    private final Resource configResource;
    private final boolean dynamic;

    /**
     * Creates a new {@code PluginInfo}.
     *
     * @param metadata the plugin metadata from {@link GrailsPluginDiscovery}
     * @param configResource the plugin's configuration resource ({@code plugin.yml} or
     *        {@code plugin.groovy}), or {@code null} if no config file exists
     */
    GrailsPluginInfo(GrailsPluginDescriptor pluginDescriptor, GrailsPluginLoadMetadata metadata, Resource configResource, boolean dynamic) {
        this.metadata = metadata;
        this.pluginDescriptor = pluginDescriptor;
        this.configResource = configResource;
        this.dynamic = dynamic;
    }

    public String name() {
        return metadata.name();
    }

    public String pluginVersion() {
        return metadata.pluginVersion();
    }

    public Class<?> pluginClass() {
        return metadata.pluginClass();
    }

    public Resource configResource() {
        return configResource;
    }

    public GrailsPluginLoadMetadata metadata() {
        return metadata;
    }

    public String[] loadAfterNames() {
        return metadata.loadAfterNames();
    }

    public String[] loadBeforeNames() {
        return metadata.loadBeforeNames();
    }

    public String[] dependsOnNames() {
        return metadata.dependsOnNames();
    }

    public String[] observedPluginNames() {
        return metadata.observedPluginNames();
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public String grailsVersion() {
        return metadata().grailsVersion();
    }

    public String[] evictions() {
        return metadata.evictions();
    }

    public GrailsPluginDescriptor pluginDescriptor() {
        return pluginDescriptor;
    }

    public boolean isGrailsVersionCompatible(String grailsVersion) {
        return GrailsPluginUtils.isPluginVersionCompatible(pluginVersion(), grailsVersion(), grailsVersion, name());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GrailsPluginInfo other)) return false;
        return metadata.equals(other.metadata);
    }

    @Override
    public int hashCode() {
        return metadata.hashCode();
    }

    @Override
    public String toString() {
        return "PluginInfoAndConfig[" + metadata.name() + "]";
    }
}
