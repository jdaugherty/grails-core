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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import grails.config.Config;
import grails.config.Settings;
import grails.plugins.PluginFilter;

/**
 * @deprecated Use {@link org.apache.grails.core.plugins.filters.PluginFilterRetriever} instead.
 * This compatibility bridge will be removed in Grails 8.0.0.
 */
@Deprecated(forRemoval = true, since = "7.1")
public class PluginFilterRetriever extends org.apache.grails.core.plugins.filters.PluginFilterRetriever {

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.filters.PluginFilterRetriever#getPluginFilter(Environment)} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    public PluginFilter getPluginFilter(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("Config should not be null");
        }

        if (config instanceof Environment environment) {
            return super.getPluginFilter(environment);
        }

        Object includes = config.getProperty(Settings.PLUGIN_INCLUDES, Object.class, null);
        Object excludes = config.getProperty(Settings.PLUGIN_EXCLUDES, Object.class, null);
        return getPluginFilter(includes, excludes);
    }

    /**
     * @deprecated Use {@link org.apache.grails.core.plugins.filters.PluginFilterRetriever#getPluginFilter(Environment)} instead.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    PluginFilter getPluginFilter(Object includes, Object excludes) {
        if (includes != null) {
            if (includes instanceof Collection<?> includesCollection) {
                return new org.apache.grails.core.plugins.filters.IncludingPluginFilter(toSet(includesCollection));
            }
            return new org.apache.grails.core.plugins.filters.IncludingPluginFilter(StringUtils.commaDelimitedListToStringArray(includes.toString()));
        }

        if (excludes != null) {
            if (excludes instanceof Collection<?> excludesCollection) {
                return new org.apache.grails.core.plugins.filters.ExcludingPluginFilter(toSet(excludesCollection));
            }
            return new org.apache.grails.core.plugins.filters.ExcludingPluginFilter(StringUtils.commaDelimitedListToStringArray(excludes.toString()));
        }

        return new org.apache.grails.core.plugins.filters.NoOpPluginFilter();
    }

    private static Set<String> toSet(Collection<?> values) {
        var set = new LinkedHashSet<String>();
        for (Object v : values) {
            if (v == null) {
                continue;
            }

            String s = v.toString().trim();
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return set;
    }
}
