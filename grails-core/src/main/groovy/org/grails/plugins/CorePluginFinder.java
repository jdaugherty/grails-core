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

import grails.core.GrailsApplication;

/**
 * @deprecated Plugin discovery is now handled by {@link org.apache.grails.core.plugins.GrailsPluginDiscovery}.
 * This compatibility stub will be removed in Grails 8.0.0.
 */
@Deprecated(forRemoval = true, since = "7.1")
public class CorePluginFinder {

    public static final String CORE_PLUGIN_PATTERN = "META-INF/grails-plugin.xml";

    private static final String UNSUPPORTED_MESSAGE = "CorePluginFinder is no longer supported. Use org.apache.grails.core.plugins.GrailsPluginDiscovery instead.";

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.GrailsPluginDiscovery} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public CorePluginFinder(GrailsApplication application) {
    }

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.GrailsPluginDiscovery} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public Class<?>[] getPluginClasses() {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.GrailsPluginDiscovery} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public BinaryGrailsPluginDescriptor getBinaryDescriptor(Class<?> pluginClass) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }
}
