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

import java.util.Collection;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

import grails.plugins.PluginFilter;

/**
 * This class is responsible for locating Grails Plugins that should be loaded.
 *
 * @since 7.1
 */
public interface GrailsPluginDiscovery {

    /**
     * After the bootstrap process, the name of the bean that this plugin discovery will be promoted with
     */
    String BEAN_NAME = "grailsPluginDiscovery";

    /**
     * Initializes the plugin discovery mechanism. This method is called during the {@link grails.boot.config.GrailsEnvironmentPostProcessor}
     *
     * @param environment the environment to check for plugin exclusions
     */
    void init(Environment environment);

    /**
     * This method is typically only used in testing, specifically unit tests.
     *
     * @return Plugins that were defined dynamically outside of the classpath
     */
    GrailsPluginInfo[] getDynamicPlugins();

    /**
     * @return Plugins that failed to load during the discovery process
     */
    Map<String, GrailsPluginInfo> getFailedPlugins();

    /**
     * @return Plugins that were discovered during the bootstrap process, no ordering guaranteed
     */
    Collection<GrailsPluginInfo> getPlugins();

    /**
     * @param pluginName a plugin name to search for, the name may not be normalized
     * @return the plugin information if found
     */
    GrailsPluginInfo getPlugin(String pluginName);

    /**
     *
     * @param pluginName a plugin name to search for, the name may not be normalized
     * @param version the required version of the plugin
     * @return if a plugin was found, and it meets the required version it will be returned, otherwise null
     */
    GrailsPluginInfo getPlugin(String pluginName, Object version);

    /**
     * @return plugins ordered by a topographical sort.
     */
    Collection<GrailsPluginInfo> getOrderedPlugins();

    /**
     * @return the order the plugins were loaded in.
     */
    Collection<GrailsPluginInfo> getLoadOrderedPlugins();

    /**
     * @param name the name of the plugin - it may not be normalized
     * @return true if a plugin with the given name exists, false otherwise
     */
    boolean hasPlugin(String name);

    /**
     * @param plugin the plugin to get the observers for
     * @return the plugins that are observing the given plugin, empty if none
     */
    Collection<GrailsPluginInfo> getPluginObservers(GrailsPluginInfo plugin);

    /**
     * @param loadClasspathPlugins true if plugins should be loaded from the classpath,
     *                             otherwise no classpath plugins will be searched
     */
    void setLoadClasspathPlugins(boolean loadClasspathPlugins);

    /**
     * @param requireClasspathPlugin if true, classpath plugins will be required to be present
     */
    void setRequireClasspathPlugin(boolean requireClasspathPlugin);

    /**
     * @param filter an override to filter plugins that are found during the discovery process
     */
    void setPluginFilter(PluginFilter filter);

    /**
     * @return any dynamic configured plugin resources specified at construction
     */
    Resource[] getPluginResources();

    /**
     * Reset the state of the plugin discovery mechanism so it can be rediscovered
     *
     * WARNING: This should only be done in controlled environments, usually during testing
     */
    void reset();
}
