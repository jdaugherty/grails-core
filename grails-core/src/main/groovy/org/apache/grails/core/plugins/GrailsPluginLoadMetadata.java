package org.apache.grails.core.plugins;

import java.util.Map;
import java.util.Set;

import grails.plugins.exceptions.PluginException;
import grails.util.Environment;

/**
 * Lightweight value class holding plugin metadata extracted from a plugin
 * class.
 *
 * <p>This provides just enough information for the
 * {@link GrailsEnvironmentPostProcessor} to sort
 * and filter plugins and load their configuration, without requiring a full
 * {@link grails.plugins.GrailsPlugin} instance.</p>
 *
 * @param name the logical plugin name (e.g., "core", "myPlugin")
 * @param grailsVersion the grailsVersion this plugin supports
 * @param pluginClass the plugin's class
 * @param loadAfterNames plugin names this plugin should load after
 * @param loadBeforeNames plugin names this plugin should load before
 * @param dependsOnNames plugin names this plugin depends on (used for
 *        transitive dependency resolution during filtering, not for
 *        sort ordering)
 * @param environments the environments this plugin is enabled for, or empty if enabled for all environments
 * @param status the status of the plugin
 */
public record GrailsPluginLoadMetadata(
        String name,
        String pluginVersion,
        String grailsVersion,
        Class<?> pluginClass,
        String[] loadAfterNames,
        String[] loadBeforeNames,
        Map<String, Object> dependencies,
        String[] dependsOnNames,
        String[] evictions,
        String[] observedPluginNames,
        Map<String, Set<Object>> environments,
        boolean enabled) {

    boolean canRegisterPlugin() {
        Environment environment = Environment.getCurrent();
        return enabled && supportsEnvironment(environment);
    }

    boolean supportsEnvironment(Environment environment) {
        return GrailsPluginUtils.supportsValueInIncludeExcludeMap(environments, environment.getName());
    }

    public String getDependentVersion(String name) {
        Object dependentVersion = dependencies.get(name);
        if (dependentVersion == null) {
            throw new PluginException("Plugin [" + name() + "] referenced dependency [" + name + "] with no version!");
        }
        return dependentVersion.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GrailsPluginLoadMetadata other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "GrailsPluginClassMetadata[" + name + "]";
    }
}
