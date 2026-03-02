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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import grails.config.Settings;
import grails.plugins.PluginFilter;

/**
 * Implements mechanism for figuring out what <code>PluginFilter</code>
 * implementation to use based on a set of provided configuration properties.
 */
public class PluginFilterRetriever {

    private PluginFilter filter;

    public PluginFilter getPluginFilter(Environment environment) {
        if(filter != null) {
            return filter;
        }

        filter = findPluginFilter(environment);
        return filter;
    }

    private PluginFilter findPluginFilter(Environment environment) {
        if(environment == null) {
            throw new IllegalArgumentException("Environment cannot be null");
        }

        Binder binder = Binder.get(environment);
        List<String> includes = binder.bind(Settings.PLUGIN_INCLUDES, Bindable.listOf(String.class)).orElse(null);
        if(includes != null && !includes.isEmpty()) {
            return new IncludingPluginFilter(toSet(includes));
        }
        String includesCompatibility = environment.getProperty(Settings.PLUGIN_INCLUDES);
        if(StringUtils.hasText(includesCompatibility)) {
            return new IncludingPluginFilter(toSet(StringUtils.commaDelimitedListToSet(includesCompatibility)));
        }

        List<String> excludes = binder.bind(Settings.PLUGIN_EXCLUDES, Bindable.listOf(String.class)).orElse(null);
        if(excludes != null && !excludes.isEmpty()) {
            return new ExcludingPluginFilter(toSet(excludes));
        }
        String excludesCompatibility = environment.getProperty(Settings.PLUGIN_EXCLUDES);
        if(StringUtils.hasText(excludesCompatibility)) {
            return new ExcludingPluginFilter(toSet(StringUtils.commaDelimitedListToSet(excludesCompatibility)));
        }

        return new NoOpPluginFilter();
    }

    private static Set<String> toSet(Collection<String> values) {
        var set = new LinkedHashSet<String>();
        for (var v : values) {
            if (v == null) {
                continue;
            }

            var s = v.trim();
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return set;
    }
}
