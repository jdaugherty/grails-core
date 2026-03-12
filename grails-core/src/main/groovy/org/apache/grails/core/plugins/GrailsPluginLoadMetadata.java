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

import java.util.Map;
import java.util.Set;

import grails.plugins.exceptions.PluginException;
import grails.util.Environment;

/**
 * Lightweight value class holding plugin metadata extracted from a plugin
 * class.
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
 * @param enabled if the plugin is enabled
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
