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
package org.apache.grails.core.plugins.filters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grails.plugins.PluginFilter;
import org.apache.grails.core.plugins.GrailsPluginLoadMetadata;

/**
 * Base functionality shared by <code>IncludingPluginFilter</code> and
 * <code>ExcludingPluginFilter</code>.
 */
public abstract class BasePluginFilter implements PluginFilter {

    /**
     * The supplied included plugin names (a String).
     */
    private final Set<String> suppliedNames;

    /**
     * Plugins corresponding with the supplied names.
     */
    private final List<GrailsPluginLoadMetadata> explicitlyNamedPlugins = new ArrayList<>();

    /**
     * Plugins derivied through a dependency relationship.
     */
    private final List<GrailsPluginLoadMetadata> derivedPlugins = new ArrayList<>();

    /**
     * Holds a name to GrailsPluginClassMetadata map (String, Plugin).
     */
    protected Map<String, GrailsPluginLoadMetadata> nameMap;

    /**
     * Temporary field holding list of plugin names added to the filtered List
     * to return (String).
     */
    private Set<String> addedNames;

    private List<GrailsPluginLoadMetadata> originalPlugins;

    public BasePluginFilter(Set<String> suppliedNames) {
        this.suppliedNames = suppliedNames;
    }

    public BasePluginFilter(String[] included) {
        suppliedNames = new HashSet<>();
        for (String s : included) {
            suppliedNames.add(s.trim());
        }
    }

    /**
     * Defines operation for adding dependencies for a plugin to the list
     */
    protected abstract void addPluginDependencies(List<GrailsPluginLoadMetadata> additionalList, GrailsPluginLoadMetadata plugin);

    /**
     * Defines an operation getting the final list to return from the original
     * and derived lists
     */
    protected abstract List<GrailsPluginLoadMetadata> getPluginList(List<GrailsPluginLoadMetadata> original, List<GrailsPluginLoadMetadata> pluginList);

    /**
     * Template method shared by subclasses of <code>BasePluginFilter</code>.
     */
    public List<GrailsPluginLoadMetadata> filterPluginList(List<GrailsPluginLoadMetadata> original) {

        originalPlugins = Collections.unmodifiableList(original);
        addedNames = new HashSet<>();

        buildNameMap();
        buildExplicitlyNamedList();
        buildDerivedPluginList();

        List<GrailsPluginLoadMetadata> pluginList = new ArrayList<>();
        pluginList.addAll(explicitlyNamedPlugins);
        pluginList.addAll(derivedPlugins);

        return getPluginList(originalPlugins, pluginList);
    }

    /**
     * Builds list of <code>GrailsPlugins</code> which are derived from the
     * <code>explicitlyNamedPlugins</code> through a dependency relationship
     */
    private void buildDerivedPluginList() {
        // find their dependencies
        for (GrailsPluginLoadMetadata plugin : explicitlyNamedPlugins) {
            // recursively add in plugin dependencies
            addPluginDependencies(derivedPlugins, plugin);
        }
    }

    /**
     * Checks whether a plugin is dependent on another plugin with the specified
     * name
     *
     * @param plugin the plugin to compare
     * @param pluginName the name to compare against
     * @return true if <code>plugin</code> depends on <code>pluginName</code>
     */
    protected boolean isDependentOn(GrailsPluginLoadMetadata plugin, String pluginName) {
        for (var dependencyName : plugin.dependsOnNames()) {
            if (pluginName.equals(dependencyName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Given the supplied list of plugin names, add the associated plugin to explicitedNamed requirements
     */
    private void buildExplicitlyNamedList() {
        originalPlugins.stream()
                .filter(p -> suppliedNames.contains(p.name()))
                .forEach(p -> {
                    explicitlyNamedPlugins.add(p);
                    addedNames.add(p.name());
                });
    }

    /**
     * Builds a name to plugin map from the original list of plugins supplied
     */
    private void buildNameMap() {
        nameMap = new HashMap<>();
        for (GrailsPluginLoadMetadata plugin : originalPlugins) {
            nameMap.put(plugin.name(), plugin);
        }
    }

    /**
     * Adds a plugin to the additional if this hasn't happened already
     */
    protected void registerDependency(List<GrailsPluginLoadMetadata> additionalList, GrailsPluginLoadMetadata plugin) {
        if (!addedNames.contains(plugin.name())) {
            addedNames.add(plugin.name());
            additionalList.add(plugin);
            addPluginDependencies(additionalList, plugin);
        }
    }

    protected Collection<GrailsPluginLoadMetadata> getAllPlugins() {
        return Collections.unmodifiableCollection(nameMap.values());
    }

    protected GrailsPluginLoadMetadata getNamedPlugin(String name) {
        return nameMap.get(name);
    }

    protected Set<String> getSuppliedNames() {
        return suppliedNames;
    }
}
