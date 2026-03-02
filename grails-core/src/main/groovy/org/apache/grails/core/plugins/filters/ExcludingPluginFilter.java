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
import java.util.List;
import java.util.Set;

import org.apache.grails.core.plugins.GrailsPluginLoadMetadata;

/**
 * Implementation of <code>PluginFilter</code> which removes all of the supplied
 * plugins (identified by name) as well as their dependencies are omitted from the
 * filtered plugin list.
 */
public class ExcludingPluginFilter extends BasePluginFilter {

    public ExcludingPluginFilter(Set<String> excluded) {
        super(excluded);
    }

    public ExcludingPluginFilter(String[] excluded) {
        super(excluded);
    }

    @Override
    protected List<GrailsPluginLoadMetadata> getPluginList(List<GrailsPluginLoadMetadata> original, List<GrailsPluginLoadMetadata> pluginList) {
        var newList = new ArrayList<>(original);
        newList.removeIf(pluginList::contains);
        return newList;
    }

    @Override
    protected void addPluginDependencies(List<GrailsPluginLoadMetadata> additionalList, GrailsPluginLoadMetadata plugin) {
        var pluginName = plugin.name();

        getAllPlugins().stream()
                .filter(p -> !pluginName.equals(p.name())) // looking for dependents, so don't include self
                .filter(p -> isDependentOn(p, pluginName))
                .forEach(p -> registerDependency(additionalList, p));
    }
}
