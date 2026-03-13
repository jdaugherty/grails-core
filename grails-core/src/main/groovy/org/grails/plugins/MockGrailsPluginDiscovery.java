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
package org.grails.plugins;

import grails.plugins.GrailsPlugin;
import org.apache.grails.core.plugins.DefaultGrailsPluginDiscovery;
import org.apache.grails.core.plugins.GrailsPluginInfo;
import org.apache.grails.core.plugins.GrailsPluginUtils;
import org.springframework.core.env.Environment;

public class MockGrailsPluginDiscovery extends DefaultGrailsPluginDiscovery {

    public MockGrailsPluginDiscovery() {
        super();
        reset(); // do not search on the classpath by default
    }

    public MockGrailsPluginDiscovery(Class<?>[] pluginClasses) {
        super(pluginClasses);
    }

    /**
     * No-op: mock discovery does not scan the classpath.
     * Plugins are registered manually via {@link #registerMockPlugin}.
     */
    @Override
    public void init(Environment environment) {
    }

    public void registerMockPlugin(GrailsPlugin plugin) {
        registerMockPlugin(GrailsPluginUtils.createPluginInfo(plugin.getPluginClass(), null, true));
    }

    public void registerMockPlugin(BinaryGrailsPlugin plugin) {
        registerMockPlugin(GrailsPluginUtils.createPluginInfoByDescriptor(plugin.getPluginClass(), plugin.getBinaryDescriptor(), false));
    }

    public void registerMockPlugin(GrailsPluginInfo pluginInfo) {
        initPluginsIfNotDefined();

        plugins.put(pluginInfo.name(), pluginInfo);
        loadOrderedPlugins.add(pluginInfo);
        orderedPlugins.add(pluginInfo);
    }

    private void initPluginsIfNotDefined() {
        if (plugins == null) {
            reset();
        }
    }
}
