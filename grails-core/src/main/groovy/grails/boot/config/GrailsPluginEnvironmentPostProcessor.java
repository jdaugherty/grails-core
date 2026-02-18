/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.boot.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import grails.plugins.GrailsPluginSorter;
import org.grails.config.PrefixedMapPropertySource;
import org.grails.config.yaml.YamlPropertySourceLoader;
import org.grails.core.cfg.GroovyConfigPropertySourceLoader;
import org.grails.plugins.GrailsPluginDiscovery;

/**
 * A Spring Boot {@link EnvironmentPostProcessor} that loads {@code plugin.yml} and
 * {@code plugin.groovy} configuration files from Grails plugins early in the application
 * lifecycle, before autoconfiguration conditions (such as {@code @ConditionalOnProperty})
 * are evaluated.
 *
 * <p>This solves the problem where plugin configuration files were previously loaded too
 * late (during {@code BeanDefinitionRegistryPostProcessor} execution) for their properties
 * to be available to Spring Boot's {@code @ConditionalOnProperty} evaluation on
 * {@code @Configuration} and {@code @AutoConfiguration} classes.</p>
 *
 * <h3>Supported Configuration Formats</h3>
 * <p>Plugins may define configuration in either {@code plugin.yml} (YAML format) or
 * {@code plugin.groovy} (Groovy ConfigSlurper format), but not both. This mirrors the
 * approach originally used by {@code AbstractGrailsPlugin}.</p>
 *
 * <h3>Shared Infrastructure</h3>
 * <p>All plugin discovery, metadata extraction, and configuration resource resolution
 * is delegated to {@link GrailsPluginDiscovery}, which provides the canonical
 * implementations shared with {@link org.grails.plugins.CorePluginFinder} and
 * {@link org.grails.plugins.AbstractGrailsPlugin}. This ensures consistent behavior
 * across all plugin lifecycle stages.</p>
 *
 * <h3>Plugin Ordering</h3>
 * <p>Plugin ordering is respected by:</p>
 * <ol>
 *   <li>Discovering plugin classes from {@code META-INF/grails-plugin.xml} descriptors
 *       via {@link GrailsPluginDiscovery#scanPluginDescriptors}</li>
 *   <li>Filtering the plugin list like ${@link GrailsApplicationPostProcessor} does</li>
 *   <li>Extracting ordering metadata ({@code loadAfter}, {@code loadBefore}) via
 *       {@link GrailsPluginDiscovery#extractPluginMetadata}</li>
 *   <li>Delegating to {@link GrailsPluginSorter} for a topological sort</li>
 *   <li>Loading configuration files in the sorted order with {@code addLast}
 *       semantics, so earlier plugins' properties have higher precedence</li>
 * </ol>
 *
 * <p>Property sources are added with the same names and types that
 * {@link org.grails.plugins.AbstractGrailsPlugin} would produce, ensuring consistency
 * with the rest of the Grails plugin configuration system.</p>
 *
 * @since 7.1
 * @see GrailsApplicationPostProcessor
 * @see GrailsPluginDiscovery
 */
public class GrailsPluginEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPluginEnvironmentPostProcessor.class);

    /**
     * The filename for YAML-based plugin configuration.
     */
    public static final String PLUGIN_YML = GrailsPluginDiscovery.PLUGIN_YML;

    /**
     * The filename for Groovy ConfigSlurper-based plugin configuration.
     */
    public static final String PLUGIN_GROOVY = GrailsPluginDiscovery.PLUGIN_GROOVY;

    @Override
    public int getOrder() {
        // Run after Spring Boot property source loading but before autoconfiguration evaluation.
        // We use a value that ensures we run after standard property sources are loaded but before
        // autoconfiguration conditions are evaluated.
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            List<PluginInfo> pluginInfos = discoverPlugins();
            if (pluginInfos.isEmpty()) {
                LOG.debug("No Grails plugin classes found in META-INF/grails-plugin.xml descriptors");
                return;
            }

            pluginInfos = filterPlugins(pluginInfos, environment);
            if (pluginInfos.isEmpty()) {
                LOG.debug("All plugins were excluded by plugin filtering");
                return;
            }

            List<PluginInfo> sorted = sortPlugins(pluginInfos);
            loadPluginConfigurations(sorted, environment);
        } catch (Exception e) {
            LOG.warn("Error loading Grails plugin configurations early: {}. " +
                    "Plugin configurations may not be available for @ConditionalOnProperty evaluation.",
                    e.getMessage());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Full stack trace:", e);
            }
        }
    }

    /**
     * Discovers all plugin classes by scanning {@code META-INF/grails-plugin.xml}
     * descriptors on the classpath via {@link GrailsPluginDiscovery#scanPluginDescriptors},
     * then reads ordering metadata and configuration resource location from each plugin
     * class via {@link GrailsPluginDiscovery#extractPluginMetadata} and
     * {@link GrailsPluginDiscovery#readPluginConfiguration}.
     */
    List<PluginInfo> discoverPlugins() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<String> pluginClassNames = GrailsPluginDiscovery.scanPluginDescriptors(classLoader);
        if (pluginClassNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<PluginInfo> pluginInfos = new ArrayList<>();

        for (String className : pluginClassNames) {
            try {
                Class<?> pluginClass = classLoader.loadClass(className);
                GrailsPluginDiscovery.PluginMetadata metadata =
                        GrailsPluginDiscovery.extractPluginMetadata(pluginClass);
                if (metadata != null) {
                    Resource configResource = GrailsPluginDiscovery.readPluginConfiguration(pluginClass);
                    pluginInfos.add(new PluginInfo(metadata, configResource));
                }
            } catch (ClassNotFoundException e) {
                LOG.debug("Plugin class [{}] not found, skipping", className);
            } catch (Exception e) {
                LOG.debug("Error loading plugin class [{}]: {}", className, e.getMessage());
            }
        }

        return pluginInfos;
    }

    List<PluginInfo> filterPlugins(List<PluginInfo> plugins, ConfigurableEnvironment environment) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //TODO: filtering not implemented yet; can't have a bean be the filter
        // the real solution is to change the plugin manager away from a bean since
        // like spring's autoconfiguration, the manager needs used prior to the context being ready
        return plugins;
    }

    /**
     * Sorts plugins using the shared {@link GrailsPluginSorter} algorithm, which
     * performs a topological sort respecting {@code loadAfter} and {@code loadBefore}
     * declarations.
     *
     * <p>{@code dependsOn} is intentionally not used for sort ordering. It is only
     * used for dependency resolution (checking that required plugins are present).
     * This matches the original behavior of
     * {@link grails.plugins.DefaultGrailsPluginManager}.</p>
     */
    List<PluginInfo> sortPlugins(List<PluginInfo> plugins) {
        return GrailsPluginSorter.sort(
                plugins,
                PluginInfo::name,
                PluginInfo::loadAfterNames,
                PluginInfo::loadBeforeNames
        );
    }

    /**
     * Loads plugin configuration files ({@code plugin.yml} or {@code plugin.groovy})
     * in the topologically sorted order and adds them to the environment's property sources.
     *
     * <p>The reverse iteration with {@code addLast} replicates the ordering behavior
     * in the original {@code GrailsApplicationPostProcessor.loadApplicationConfig()},
     * where plugins are iterated in reverse sorted order and added with {@code addLast}.
     * This means earlier plugins (in sort order) get higher property precedence.</p>
     *
     * <p>Property sources are added with the same names and types that
     * {@link org.grails.plugins.AbstractGrailsPlugin} would produce:
     * a {@link org.grails.config.NavigableMapPropertySource} with name
     * {@code "<pluginName>-plugin.yml"} or {@code "<pluginName>-plugin.groovy"},
     * and a {@link PrefixedMapPropertySource} for the {@code grails.plugins.<name>} prefix.</p>
     */
    private void loadPluginConfigurations(List<PluginInfo> sortedPlugins, ConfigurableEnvironment environment) {
        MutablePropertySources propertySources = environment.getPropertySources();
        YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();
        GroovyConfigPropertySourceLoader groovyLoader = new GroovyConfigPropertySourceLoader();
        int loadedCount = 0;

        // Iterate in reverse order and use addLast, matching
        // GrailsApplicationPostProcessor.loadApplicationConfig() behavior
        List<PluginInfo> reversed = new ArrayList<>(sortedPlugins);
        Collections.reverse(reversed);

        for (PluginInfo plugin : reversed) {
            Resource resource = plugin.configResource();
            if (resource == null || !resource.exists()) {
                continue;
            }

            try {
                String filename = resource.getFilename();
                String sourceName = plugin.name() + "-" + filename;

                List<PropertySource<?>> loaded;
                if (PLUGIN_YML.equals(filename)) {
                    loaded = yamlLoader.load(sourceName, resource, GrailsPluginDiscovery.DEFAULT_CONFIG_IGNORE_LIST);
                } else if (PLUGIN_GROOVY.equals(filename)) {
                    loaded = groovyLoader.load(sourceName, resource, GrailsPluginDiscovery.DEFAULT_CONFIG_IGNORE_LIST);
                } else {
                    LOG.debug("Unknown config file format [{}] for plugin [{}], skipping",
                            filename, plugin.name());
                    continue;
                }

                for (PropertySource<?> ps : loaded) {
                    if (ps != null) {
                        // Add the prefixed property source (grails.plugins.<name>.<prop>),
                        // matching what AbstractGrailsPlugin / loadApplicationConfig() does
                        if (ps instanceof EnumerablePropertySource<?> eps) {
                            propertySources.addLast(new PrefixedMapPropertySource(
                                    "grails.plugins." + plugin.name(), eps));
                        }

                        // Add the property source directly (NavigableMapPropertySource)
                        // with the same name that AbstractGrailsPlugin would produce
                        propertySources.addLast(ps);
                        loadedCount++;
                    }
                }

                LOG.debug("Loaded {} for plugin [{}] early in lifecycle", filename, plugin.name());
            } catch (IOException e) {
                LOG.debug("Error loading configuration for plugin [{}]: {}", plugin.name(), e.getMessage());
            }
        }

        if (loadedCount > 0) {
            LOG.info("Loaded {} plugin configuration(s) early via GrailsPluginEnvironmentPostProcessor", loadedCount);
        }
    }

    /**
     * Lightweight value class holding plugin metadata needed for ordering and
     * configuration loading. Wraps {@link GrailsPluginDiscovery.PluginMetadata}
     * with an additional configuration resource reference.
     */
    static final class PluginInfo {

        private final GrailsPluginDiscovery.PluginMetadata metadata;
        private final Resource configResource;

        /**
         * Creates a new {@code PluginInfo}.
         *
         * @param metadata the plugin metadata from {@link GrailsPluginDiscovery}
         * @param configResource the plugin's configuration resource ({@code plugin.yml} or
         *        {@code plugin.groovy}), or {@code null} if no config file exists
         */
        PluginInfo(GrailsPluginDiscovery.PluginMetadata metadata, Resource configResource) {
            this.metadata = metadata;
            this.configResource = configResource;
        }

        /**
         * Test-friendly constructor that creates a PluginInfo with explicit values,
         * bypassing {@link GrailsPluginDiscovery.PluginMetadata} construction.
         *
         * @param name the logical plugin name
         * @param pluginClass the plugin class
         * @param configResource the configuration resource
         * @param loadAfterNames loadAfter names
         * @param loadBeforeNames loadBefore names
         */
        PluginInfo(String name, Class<?> pluginClass, Resource configResource,
                String[] loadAfterNames, String[] loadBeforeNames) {
            this(name, pluginClass, configResource, loadAfterNames, loadBeforeNames, new String[0]);
        }

        /**
         * Test-friendly constructor that creates a PluginInfo with explicit values
         * including dependency names.
         *
         * @param name the logical plugin name
         * @param pluginClass the plugin class
         * @param configResource the configuration resource
         * @param loadAfterNames loadAfter names
         * @param loadBeforeNames loadBefore names
         * @param dependsOnNames dependency plugin names
         */
        PluginInfo(String name, Class<?> pluginClass, Resource configResource,
                String[] loadAfterNames, String[] loadBeforeNames, String[] dependsOnNames) {
            this.metadata = new GrailsPluginDiscovery.PluginMetadata(
                    name, pluginClass, loadAfterNames, loadBeforeNames, dependsOnNames);
            this.configResource = configResource;
        }

        String name() { return metadata.name(); }

        Class<?> pluginClass() { return metadata.pluginClass(); }

        Resource configResource() { return configResource; }

        String[] loadAfterNames() { return metadata.loadAfterNames(); }

        String[] loadBeforeNames() { return metadata.loadBeforeNames(); }

        String[] dependsOnNames() { return metadata.dependsOnNames(); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PluginInfo other)) return false;
            return metadata.equals(other.metadata);
        }

        @Override
        public int hashCode() {
            return metadata.hashCode();
        }

        @Override
        public String toString() {
            return "PluginInfo[" + metadata.name() + "]";
        }
    }
}
