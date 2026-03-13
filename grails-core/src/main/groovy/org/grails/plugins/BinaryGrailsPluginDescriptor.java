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

import java.util.List;

import org.springframework.core.io.Resource;

import org.apache.grails.core.plugins.GrailsPluginDescriptor;

/**
 * @deprecated Use {@link org.apache.grails.core.plugins.GrailsPluginDescriptor} instead.
 * This compatibility bridge will be removed in Grails 8.0.0.
 */
@Deprecated(forRemoval = true, since = "7.1")
public class BinaryGrailsPluginDescriptor {

    private final GrailsPluginDescriptor descriptor;

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.GrailsPluginDescriptor} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public BinaryGrailsPluginDescriptor(Resource resource, List<String> classNames) {
        this.descriptor = new GrailsPluginDescriptor(resource, List.of(), classNames);
    }

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.GrailsPluginDescriptor#resource()} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public Resource getResource() {
        return descriptor.resource();
    }

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.GrailsPluginDescriptor#providedClasses()} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public List<String> getProvidedClassNames() {
        return descriptor.providedClasses();
    }
}
