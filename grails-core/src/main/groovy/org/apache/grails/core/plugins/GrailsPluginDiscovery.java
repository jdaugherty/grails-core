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
package org.apache.grails.core.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.IOGroovyMethods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import grails.plugins.GrailsPluginSorter;
import grails.plugins.GrailsVersionUtils;
import grails.plugins.PluginFilter;
import grails.plugins.exceptions.PluginException;
import grails.util.Metadata;
import org.apache.grails.core.plugins.filters.PluginFilterRetriever;
import org.grails.core.io.CachingPathMatchingResourcePatternResolver;
import org.grails.io.support.GrailsResourceUtils;

/**
 * This class provides the canonical implementations of Grails Plugin Discovery.
 *
 * @since 7.1
 */
public class GrailsPluginDiscovery {

    public static final String BEAN_NAME = "grailsPluginDiscovery";
    private static final Logger LOG = LoggerFactory.getLogger(GrailsPluginDiscovery.class);

    protected Metadata applicationMeta = Metadata.getCurrent();
    protected Resource[] pluginResources = new Resource[0];
    protected Class<?>[] pluginClasses = new Class[0];
    /**
     * plugins ordered by the load order
     */
    protected LinkedHashMap<String, GrailsPluginInfo> plugins;
    /**
     * plugins sorted by the topographical sort
     */
    protected List<GrailsPluginInfo> orderedPlugins;
    /**
     * plugins sorted by the load order
     */
    protected List<GrailsPluginInfo> loadOrderedPlugins;
    protected List<GrailsPluginInfo> dynamicPlugins;
    protected Map<String, Set<GrailsPluginInfo>> pluginToObserverMap;
    protected List<GrailsPluginInfo> delayedLoadPlugins;
    protected Map<String, GrailsPluginInfo> failedPlugins;
    protected Map<GrailsPluginInfo, String[]> delayedEvictions;
    protected PluginFilter pluginFilter;
    protected boolean loadClasspathPlugins = true;
    protected boolean requireClasspathPlugin = true;
    protected final PluginFilterRetriever filterRetriever;

    public GrailsPluginDiscovery() {
        this(new PluginFilterRetriever());
    }

    public GrailsPluginDiscovery(String resourcePath) {
        this();

        PathMatchingResourcePatternResolver resolver = CachingPathMatchingResourcePatternResolver.INSTANCE;
        try {
            pluginResources = resolver.getResources(resourcePath);
        } catch (IOException ioe) {
            LOG.debug("Unable to load plugins for resource path {}", resourcePath, ioe);
        }
    }

    public GrailsPluginDiscovery(Class<?>[] pluginClasses) {
        this();
        this.pluginClasses = pluginClasses;
    }

    public GrailsPluginDiscovery(String[] pluginResources) {
        this();

        PathMatchingResourcePatternResolver resolver = CachingPathMatchingResourcePatternResolver.INSTANCE;

        List<Resource> resourceList = new ArrayList<>();
        for (String resourcePath : pluginResources) {
            try {
                resourceList.addAll(Arrays.asList(resolver.getResources(resourcePath)));
            } catch (IOException ioe) {
                LOG.debug("Unable to load plugins for resource path {}", resourcePath, ioe);
            }
        }

        this.pluginResources = resourceList.toArray(new Resource[0]);
    }

    public GrailsPluginDiscovery(Resource[] pluginFiles) {
        this();
        this.pluginResources = pluginFiles;
    }

    public GrailsPluginDiscovery(PluginFilterRetriever filterRetriever) {
        this.filterRetriever = filterRetriever;
    }

    public GrailsPluginInfo[] getDynamicPlugins() {
        return dynamicPlugins.toArray(new GrailsPluginInfo[0]);
    }

    public Map<String, GrailsPluginInfo> getFailedPlugins() {
        return failedPlugins;
    }

    public GrailsPluginInfo getPlugin(String pluginName, Object version, Environment environment) {
        if (plugins == null) {
            if (environment == null) {
                throw new IllegalArgumentException("Environment must be provided to fetch a plugin");
            }
            findPlugins(environment);
        }

        GrailsPluginInfo plugin = plugins.get(GrailsPluginUtils.normalizePluginName(pluginName));
        if (plugin != null && GrailsVersionUtils.isValidVersion(plugin.pluginVersion(), version.toString())) {
            return plugin;
        }
        return null;
    }

    public boolean hasPlugin(String name) {
        return plugins.containsKey(GrailsPluginUtils.normalizePluginName(name));
    }

    public GrailsPluginInfo getPlugin(String pluginName, Environment environment) {
        if (plugins == null) {
            if (environment == null) {
                throw new IllegalArgumentException("Environment must be provided to fetch a plugin");
            }

            findPlugins(environment);
        }

        return plugins.get(GrailsPluginUtils.normalizePluginName(pluginName));
    }

    public Collection<GrailsPluginInfo> getPluginObservers(GrailsPluginInfo plugin) {
        Objects.requireNonNull(plugin, "Argument [plugin] cannot be null");

        Collection<GrailsPluginInfo> c = pluginToObserverMap.get(plugin.name());

        // Add any wildcard observers.
        Collection<GrailsPluginInfo> wildcardObservers = pluginToObserverMap.get("*");
        if (wildcardObservers != null) {
            if (c != null) {
                c.addAll(wildcardObservers);
            } else {
                c = wildcardObservers;
            }
        }

        if (c != null) {
            // Make sure this plugin is not observing itself!
            c.remove(plugin);
            return c;
        }

        return Collections.emptySet();
    }

    public Collection<GrailsPluginInfo> getPlugins(Environment environment) {
        return plugins == null ? findPlugins(environment).values() : plugins.values();
    }

    /**
     * @return plugins ordered by a topographical sort.
     */
    public Collection<GrailsPluginInfo> getOrderedPlugins(Environment environment) {
        if (orderedPlugins == null) {
            findPlugins(environment);
        }

        return orderedPlugins;
    }

    /**
     * @return the order the plugins were loaded in.
     */
    public Collection<GrailsPluginInfo> getLoadOrderedPlugins(Environment environment) {
        if (loadOrderedPlugins == null) {
            findPlugins(environment);
        }

        return loadOrderedPlugins;
    }

    public void setLoadClasspathPlugins(boolean loadClasspathPlugins) {
        this.loadClasspathPlugins = loadClasspathPlugins;
    }

    public void setRequireClasspathPlugin(boolean requireClasspathPlugin) {
        this.requireClasspathPlugin = requireClasspathPlugin;
    }

    public void setPluginFilter(PluginFilter filter) {
        this.pluginFilter = filter;
    }

    public Resource[] getPluginResources() {
        return pluginResources;
    }

    List<GrailsPluginInfo> filterPlugins(List<GrailsPluginInfo> plugins, Environment environment) {
        if (pluginFilter == null) {
            pluginFilter = filterRetriever.getPluginFilter(environment);
        }

        var filteredMetadataSet = new HashSet<>(pluginFilter.filterPluginList(
                plugins.stream()
                        .map(GrailsPluginInfo::metadata)
                        .toList()
        ));

        return plugins.stream()
                .filter(p -> filteredMetadataSet.contains(p.metadata()))
                .toList();
    }

    LinkedHashMap<String, GrailsPluginInfo> findPlugins(Environment environment) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<GrailsPluginInfo> classpathPlugins = loadClasspathPlugins ? findClasspathPlugins(classLoader) : Collections.emptyList();
        if (loadClasspathPlugins && requireClasspathPlugin && classpathPlugins.isEmpty()) {
            LOG.debug("No Grails plugin classes found in META-INF/grails-plugin.xml descriptors");
            throw new IllegalStateException(
                    "Grails was unable to load plugins dynamically. This is normally a " +
                            "problem with the container class loader configuration, see " +
                            "troubleshooting and FAQ for more info.");
        }

        // TODO: These were previously never filtered, and continue to behave this way
        dynamicPlugins = findDynamicPlugins(classLoader);

        List<GrailsPluginInfo> allPlugins = new ArrayList<>(classpathPlugins);
        allPlugins.addAll(dynamicPlugins);

        List<GrailsPluginInfo> filteredPlugins = filterPlugins(allPlugins, environment);

        reset();

        if (filteredPlugins.isEmpty()) {
            LOG.debug("All plugins were excluded by plugin filtering");
            return plugins;
        }

        attemptRegisterPlugins(filteredPlugins);

        if (!delayedLoadPlugins.isEmpty()) {
            loadDelayedPlugins();
        }
        if (!delayedEvictions.isEmpty()) {
            processDelayedEvictions();
        }

        // TODO: why do we sort if we know the order it had to load in?
        orderedPlugins = GrailsPluginSorter.sort(
                loadOrderedPlugins,
                GrailsPluginInfo::name,
                GrailsPluginInfo::loadAfterNames,
                GrailsPluginInfo::loadBeforeNames
        );

        return plugins;
    }

    private void processDelayedEvictions() {
        for (Map.Entry<GrailsPluginInfo, String[]> entry : delayedEvictions.entrySet()) {
            GrailsPluginInfo plugin = entry.getKey();
            for (String pluginName : entry.getValue()) {
                evictPlugin(plugin, pluginName);
            }
        }
    }

    protected void evictPlugin(GrailsPluginInfo evictor, String evicteeName) {
        GrailsPluginInfo pluginToEvict = plugins.get(evicteeName);
        if (pluginToEvict != null) {
            orderedPlugins.remove(pluginToEvict);
            loadOrderedPlugins.remove(pluginToEvict);
            plugins.remove(pluginToEvict.name());

            if (LOG.isInfoEnabled()) {
                LOG.info("Grails plug-in {} was evicted by {}", pluginToEvict, evictor);
            }
        }
    }

    /**
     * This method will attempt to load that plug-ins not loaded in the first pass
     */
    private void loadDelayedPlugins() {
        while (!delayedLoadPlugins.isEmpty()) {
            GrailsPluginInfo plugin = delayedLoadPlugins.remove(0);
            if (areDependenciesResolved(plugin)) {
                if (!hasValidPluginsToLoadBefore(plugin)) {
                    registerPlugin(plugin);
                } else {
                    delayedLoadPlugins.add(plugin);
                }
            } else {
                // ok, it still hasn't resolved the dependency after the initial
                // load of all plugins. All hope is not lost, however, so let's first
                // look inside the remaining delayed loads before giving up
                boolean foundInDelayed = false;
                for (GrailsPluginInfo remainingPlugin : delayedLoadPlugins) {
                    if (isDependentOn(plugin, remainingPlugin)) {
                        foundInDelayed = true;
                        break;
                    }
                }
                if (foundInDelayed) {
                    delayedLoadPlugins.add(plugin);
                } else {
                    failedPlugins.put(plugin.name(), plugin);
                    LOG.error("ERROR: Plugin [{}] cannot be loaded because its dependencies [{}}] cannot be resolved", plugin.name(), plugin.dependsOnNames());
                }
            }
        }
    }

    /**
     * Checks whether the first plugin is dependent on the second plugin.
     *
     * @param plugin     The plugin to check
     * @param dependency The plugin which the first argument may be dependent on
     * @return true if it is
     */
    private boolean isDependentOn(GrailsPluginInfo plugin, GrailsPluginInfo dependency) {
        for (String name : plugin.dependsOnNames()) {
            String requiredVersion = plugin.metadata().getDependentVersion(name);

            if (name.equals(dependency.name()) &&
                    GrailsVersionUtils.isValidVersion(dependency.pluginVersion(), requiredVersion)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasValidPluginsToLoadBefore(GrailsPluginInfo plugin) {
        String[] loadAfterNames = plugin.loadAfterNames();
        for (GrailsPluginInfo other : delayedLoadPlugins) {
            for (String name : loadAfterNames) {
                if (other.name().equals(name)) {
                    return hasDelayedDependencies(other) || areDependenciesResolved(other);
                }
            }
        }
        return false;
    }

    private boolean hasDelayedDependencies(GrailsPluginInfo other) {
        String[] dependencyNames = other.dependsOnNames();
        for (String dependencyName : dependencyNames) {
            for (GrailsPluginInfo grailsPlugin : delayedLoadPlugins) {
                if (grailsPlugin.name().equals(dependencyName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void attemptRegisterPlugins(List<GrailsPluginInfo> filteredPlugins) {
        for (GrailsPluginInfo eligiblePlugin : filteredPlugins) {
            if (areDependenciesResolved(eligiblePlugin) && areNoneToLoadBefore(eligiblePlugin)) {
                registerPlugin(eligiblePlugin);
            } else {
                delayedLoadPlugins.add(eligiblePlugin);
            }
        }
    }

    private void registerPlugin(GrailsPluginInfo plugin) {
        if (!plugin.metadata().canRegisterPlugin()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Grails plugin {} is disabled and was not loaded", plugin);
            }
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Grails plug-in [" + plugin.name() + "] with version [" + plugin.pluginVersion() + "] loaded successfully");
        }

        String[] evictionNames = plugin.evictions();
        if (evictionNames.length > 0) {
            delayedEvictions.put(plugin, evictionNames);
        }

        String[] observedPlugins = plugin.observedPluginNames();
        for (String observedPlugin : observedPlugins) {
            Set<GrailsPluginInfo> observers = pluginToObserverMap.computeIfAbsent(observedPlugin, k -> new HashSet<>());
            observers.add(plugin);
        }
        loadOrderedPlugins.add(plugin);
        plugins.put(plugin.name(), plugin);
    }

    /**
     * Returns true if there are no plugins left that should, if possible, be loaded before this plugin.
     *
     * @param plugin The plugin
     * @return true if there are
     */
    private boolean areNoneToLoadBefore(GrailsPluginInfo plugin) {
        for (String name : plugin.loadAfterNames()) {
            if (getPlugin(name, null) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean areDependenciesResolved(GrailsPluginInfo plugin) {
        for (String name : plugin.metadata().dependsOnNames()) {
            if (!hasGrailsPlugin(name, plugin.metadata().getDependentVersion(name))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasGrailsPlugin(String name, String version) {
        return getPlugin(name, version, null) != null;
    }

    private List<GrailsPluginInfo> findDynamicPlugins(ClassLoader classLoader) {
        List<GrailsPluginInfo> discoveredPlugins = new ArrayList<>();
        LOG.info("Attempting to load [{}] dynamically defined plugins", pluginResources.length);
        for (Resource r : pluginResources) {
            Class<?> pluginClass = loadPluginClass(classLoader, r);
            if (GrailsPluginUtils.isGrailsPluginClassNamedCorrectly(pluginClass)) {
                try {
                    GrailsPluginInfo pluginInfo = GrailsPluginUtils.createPluginInfo(pluginClass, r, true);
                    pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
                    discoveredPlugins.add(pluginInfo);
                } catch (Exception e) {
                    LOG.warn("Error loading plugin class [{}]; skipping", pluginClass.getName());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(e.getMessage(), e);
                    }
                }
            } else {
                LOG.warn("Class [{}] loaded from Resource [{}] not loaded as plug-in. Grails plug-ins must end with the convention 'GrailsPlugin'!", pluginClass.getName(), r.getDescription());
            }
        }

        for (Class<?> pluginClass : pluginClasses) {
            if (GrailsPluginUtils.isGrailsPluginClassNamedCorrectly(pluginClass)) {
                try {
                    GrailsPluginInfo pluginInfo = GrailsPluginUtils.createPluginInfo(pluginClass, null, true);
                    pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
                    discoveredPlugins.add(pluginInfo);
                } catch (Exception e) {
                    LOG.warn("Error loading plugin class [{}]; skipping", pluginClass.getName());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(e.getMessage(), e);
                    }
                }
            } else {
                LOG.warn("Class [{}] not loaded as plug-in. Grails plug-ins must end with the convention 'GrailsPlugin'!", pluginClass.getName());
            }
        }
        return discoveredPlugins;
    }

    private Class<?> loadPluginClass(ClassLoader cl, Resource r) {
        Class<?> pluginClass;
        if (cl instanceof GroovyClassLoader) {
            try {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Parsing & compiling {}", r.getFilename());
                }
                pluginClass = ((GroovyClassLoader) cl).parseClass(IOGroovyMethods.getText(r.getInputStream(), "UTF-8"));
            } catch (CompilationFailedException e) {
                throw new PluginException("Error compiling plugin [" + r.getFilename() + "] " + e.getMessage(), e);
            } catch (IOException e) {
                throw new PluginException("Error reading plugin [" + r.getFilename() + "] " + e.getMessage(), e);
            }
        } else {
            String className = null;
            try {
                className = GrailsResourceUtils.getClassName(r.getFile().getAbsolutePath());
            } catch (IOException e) {
                throw new PluginException("Cannot find plugin class [" + className + "] resource: [" + r.getFilename() + "]", e);
            }
            try {
                pluginClass = Class.forName(className, true, cl);
            } catch (ClassNotFoundException e) {
                throw new PluginException("Cannot find plugin class [" + className + "] resource: [" + r.getFilename() + "]", e);
            }
        }
        return pluginClass;
    }

    /**
     * Discovers all plugin classes by scanning {@code META-INF/grails-plugin.xml}
     * descriptors on the classpath via {@link GrailsPluginUtils#scanPluginDescriptors},
     * then reads ordering metadata and configuration resource location from each plugin
     * class via {@link GrailsPluginUtils#extractPluginMetadata} and
     * {@link GrailsPluginUtils#readPluginConfiguration}.
     */
    List<GrailsPluginInfo> findClasspathPlugins(ClassLoader classLoader) {
        List<GrailsPluginDescriptor> pluginDescriptors = GrailsPluginUtils.scanPluginDescriptorResources(classLoader);
        if (pluginDescriptors.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<GrailsPluginInfo> discoveredPlugins = new ArrayList<>();

        LOG.debug("Attempting to load [{}] plugin descriptors", pluginDescriptors.size());
        for (GrailsPluginDescriptor pluginDescriptor : pluginDescriptors) {
            for (String pluginClassName : pluginDescriptor.providedPlugins()) {
                try {
                    Class<?> pluginClass = attemptPluginClassLoad(pluginClassName, classLoader);
                    if (GrailsPluginUtils.isGrailsPluginLoadable(pluginClass)) {
                        GrailsPluginLoadMetadata metadata = GrailsPluginUtils.extractPluginMetadata(pluginClass);
                        if (metadata != null) {
                            Resource configResource = GrailsPluginUtils.readPluginConfiguration(pluginClass);
                            GrailsPluginInfo pluginInfo = new GrailsPluginInfo(pluginDescriptor, metadata, configResource, false);
                            pluginInfo.isGrailsVersionCompatible(applicationMeta.getGrailsVersion());
                            discoveredPlugins.add(pluginInfo);
                        }
                    }
                } catch (Exception e) {
                    LOG.debug("Error loading plugin class [{}]: {}", pluginClassName, e.getMessage());
                }
            }
        }

        return discoveredPlugins;
    }

    private static Class<?> attemptPluginClassLoad(String pluginClassName, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(pluginClassName);
        } catch (ClassNotFoundException e) {
            LOG.warn("Grails Plugin [{}] not found, resuming load without..", pluginClassName);
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
        }
        return null;
    }

    protected void reset() {
        plugins = new LinkedHashMap<>();
        loadOrderedPlugins = new ArrayList<>();
        pluginToObserverMap = new HashMap<>();
        delayedLoadPlugins = new LinkedList<>();
        failedPlugins = new HashMap<>();
        delayedEvictions = new HashMap<>();
        orderedPlugins = new ArrayList<>();
    }
}
