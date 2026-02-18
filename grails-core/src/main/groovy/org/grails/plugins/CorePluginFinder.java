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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationContext;

import grails.core.GrailsApplication;
import grails.core.support.ParentApplicationContextAware;

/**
 * Loads core plugin classes. Contains functionality moved in from
 * {@code DefaultGrailsPluginManager}.
 *
 * <p>All plugin descriptor scanning and XML parsing is delegated to
 * {@link GrailsPluginDiscovery#scanPluginDescriptorResources}, the canonical
 * shared implementation that is also used by
 * {@link grails.boot.config.GrailsPluginEnvironmentPostProcessor}. This
 * ensures that both code paths discover the same plugins from the same
 * {@code META-INF/grails-plugin.xml} descriptors.</p>
 *
 * @author Graeme Rocher
 * @author Phil Zoio
 */
public class CorePluginFinder {

    private static final Logger LOG = LoggerFactory.getLogger(CorePluginFinder.class);

    private final Set<Class<?>> foundPluginClasses = new HashSet<>();
    private final GrailsApplication application;
    @SuppressWarnings("rawtypes")
    private final Map<Class, BinaryGrailsPluginDescriptor> binaryDescriptors = new HashMap<>();

    public CorePluginFinder(GrailsApplication application) {
        this.application = application;
    }

    public Class<?>[] getPluginClasses() {

        // just in case we try to use this twice
        foundPluginClasses.clear();

        List<GrailsPluginDiscovery.PluginDescriptorInfo> descriptors =
                GrailsPluginDiscovery.scanPluginDescriptorResources(application.getClassLoader());

        if (descriptors.isEmpty()) {
            throw new IllegalStateException(
                    "Grails was unable to load plugins dynamically. This is normally a " +
                    "problem with the container class loader configuration, see " +
                    "troubleshooting and FAQ for more info.");
        }

        LOG.debug("Attempting to load [{}] core plugin descriptors", descriptors.size());

        for (GrailsPluginDiscovery.PluginDescriptorInfo descriptor : descriptors) {
            for (String pluginClassName : descriptor.pluginTypes()) {
                Class<?> pluginClass = attemptCorePluginClassLoad(pluginClassName);
                if (pluginClass != null) {
                    foundPluginClasses.add(pluginClass);
                    binaryDescriptors.put(pluginClass,
                            new BinaryGrailsPluginDescriptor(
                                    descriptor.xmlResource(),
                                    descriptor.providedClassNames()));
                }
            }
        }

        return foundPluginClasses.toArray(new Class[0]);
    }

    public BinaryGrailsPluginDescriptor getBinaryDescriptor(Class<?> pluginClass) {
        return binaryDescriptors.get(pluginClass);
    }

    private Class<?> attemptCorePluginClassLoad(String pluginClassName) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            return classLoader.loadClass(pluginClassName);
        } catch (ClassNotFoundException e) {
            LOG.warn("[GrailsPluginManager] Core plugin [{}] not found, resuming load without..",
                    pluginClassName);
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
        }
        return null;
    }

}
