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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import grails.io.IOUtils;
import grails.util.GrailsNameUtils;
import org.grails.io.support.SpringIOUtils;

/**
 * Shared utility for Grails plugin discovery, metadata extraction, and
 * configuration resource resolution.
 *
 * <p>This class provides the canonical implementations of plugin discovery
 * operations that are used by both {@link CorePluginFinder} (at runtime, when
 * the full {@link grails.core.GrailsApplication} is available) and
 * {@link grails.boot.config.GrailsPluginEnvironmentPostProcessor} (early in
 * the lifecycle, before ApplicationContext exists).</p>
 *
 * <p>By consolidating these operations here, we ensure that both code paths
 * discover the same plugins, resolve the same configuration resources, and
 * extract identical ordering metadata.</p>
 *
 * @since 7.1
 * @see CorePluginFinder
 * @see grails.boot.config.GrailsPluginEnvironmentPostProcessor
 */
public final class GrailsPluginDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPluginDiscovery.class);

    /**
     * The classpath location of the Grails plugin descriptor XML files.
     */
    public static final String CORE_PLUGIN_PATTERN = "META-INF/grails-plugin.xml";

    /**
     * The filename for YAML-based plugin configuration.
     */
    public static final String PLUGIN_YML = "plugin.yml";

    private static final String PLUGIN_YML_PATH = "/" + PLUGIN_YML;

    /**
     * The filename for Groovy ConfigSlurper-based plugin configuration.
     */
    public static final String PLUGIN_GROOVY = "plugin.groovy";

    private static final String PLUGIN_GROOVY_PATH = "/" + PLUGIN_GROOVY;

    private static final String GRAILS_PLUGIN_SUFFIX = "GrailsPlugin";

    /**
     * Default config keys to ignore when loading plugin configuration.
     */
    public static final List<String> DEFAULT_CONFIG_IGNORE_LIST = Arrays.asList("dataSource", "hibernate");

    private GrailsPluginDiscovery() {
        // utility class
    }

    /**
     * Scans all {@code META-INF/grails-plugin.xml} resources on the classpath
     * and returns rich descriptor information for each, including plugin type
     * names, provided resource class names, and the source XML resource.
     *
     * <p>This is the canonical XML scanning implementation, extracted from
     * {@link CorePluginFinder}. Both {@link CorePluginFinder} (which needs the
     * full descriptor information to construct
     * {@link BinaryGrailsPluginDescriptor} instances) and the
     * {@link grails.boot.config.GrailsPluginEnvironmentPostProcessor} (which
     * only needs plugin class names) delegate to this method.</p>
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of {@link PluginDescriptorInfo} records, one per
     *         {@code META-INF/grails-plugin.xml} resource found on the
     *         classpath
     */
    public static List<PluginDescriptorInfo> scanPluginDescriptorResources(ClassLoader classLoader) {
        List<PluginDescriptorInfo> descriptors = new ArrayList<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(CORE_PLUGIN_PATTERN);
            SAXParser saxParser = SpringIOUtils.newSAXParser();

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream input = url.openStream()) {
                    PluginXmlHandler handler = new PluginXmlHandler();
                    saxParser.parse(input, handler);
                    Resource xmlResource = new UrlResource(url);
                    descriptors.add(new PluginDescriptorInfo(
                            xmlResource,
                            handler.getPluginTypes(),
                            handler.getPluginClasses()));
                } catch (IOException | SAXException e) {
                    LOG.debug("Error parsing plugin descriptor at [{}]: {}", url, e.getMessage());
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOG.debug("Error scanning for plugin descriptors: {}", e.getMessage());
        }

        return descriptors;
    }

    /**
     * Convenience method that scans all {@code META-INF/grails-plugin.xml}
     * resources and returns just the plugin class names.
     *
     * <p>This delegates to {@link #scanPluginDescriptorResources} and extracts
     * only the plugin type names, which is sufficient for callers like
     * {@link grails.boot.config.GrailsPluginEnvironmentPostProcessor} that
     * do not need the full descriptor information.</p>
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of fully-qualified plugin class names discovered from
     *         {@code META-INF/grails-plugin.xml} descriptors
     */
    public static List<String> scanPluginDescriptors(ClassLoader classLoader) {
        List<PluginDescriptorInfo> descriptors = scanPluginDescriptorResources(classLoader);
        List<String> pluginClassNames = new ArrayList<>();
        for (PluginDescriptorInfo descriptor : descriptors) {
            pluginClassNames.addAll(descriptor.pluginTypes());
        }
        return pluginClassNames;
    }

    /**
     * Derives the logical plugin name from the plugin class, following Grails
     * conventions.
     *
     * <p>For example, {@code org.grails.plugins.CoreGrailsPlugin} becomes
     * {@code core}. This delegates to
     * {@link GrailsNameUtils#getLogicalPropertyName} with the
     * {@code "GrailsPlugin"} suffix.</p>
     *
     * @param pluginClass the plugin class
     * @return the logical plugin name
     */
    public static String getLogicalPluginName(Class<?> pluginClass) {
        return GrailsNameUtils.getLogicalPropertyName(
                pluginClass.getSimpleName(), GRAILS_PLUGIN_SUFFIX);
    }

    /**
     * Extracts plugin metadata ({@code loadAfter}, {@code loadBefore},
     * {@code dependsOn}) from a plugin class without requiring a full
     * {@link grails.core.GrailsApplication} or plugin manager.
     *
     * <p>The plugin class is instantiated to read its properties via a
     * {@link BeanWrapper}, mirroring the approach used by
     * {@link DefaultGrailsPlugin}. The {@code dependsOn} property is a
     * {@code Map<String, String>} where keys are dependency plugin names and
     * values are version constraints; only the keys are extracted.</p>
     *
     * @param pluginClass the plugin class to extract metadata from
     * @return a {@link PluginMetadata} record, or {@code null} if the class is
     *         not a valid Grails plugin
     */
    public static PluginMetadata extractPluginMetadata(Class<?> pluginClass) {
        if (pluginClass == null || !pluginClass.getName().endsWith(GRAILS_PLUGIN_SUFFIX)) {
            return null;
        }

        String pluginName = getLogicalPluginName(pluginClass);

        String[] loadAfterNames = {};
        String[] loadBeforeNames = {};
        String[] dependsOnNames = {};

        try {
            Object pluginInstance = pluginClass.getDeclaredConstructor().newInstance();
            BeanWrapper beanWrapper = new BeanWrapperImpl(pluginInstance);

            loadAfterNames = readStringListProperty(beanWrapper, "loadAfter");
            loadBeforeNames = readStringListProperty(beanWrapper, "loadBefore");
            dependsOnNames = readDependsOnNames(beanWrapper);
        } catch (Exception e) {
            LOG.warn("Could not extract ordering metadata from plugin [{}]: {}. " +
                    "Plugin will be loaded with default ordering.", pluginName, e.getMessage());
        }

        return new PluginMetadata(pluginName, pluginClass, loadAfterNames,
                loadBeforeNames, dependsOnNames);
    }

    /**
     * Reads the plugin configuration resource by probing for both
     * {@code plugin.yml} and {@code plugin.groovy} relative to the plugin
     * class.
     *
     * <p>Returns the resource for whichever exists, or {@code null} if neither
     * exists. Throws {@link RuntimeException} if both exist.</p>
     *
     * <p>This is the canonical implementation used by both
     * {@link AbstractGrailsPlugin} and the
     * {@link grails.boot.config.GrailsPluginEnvironmentPostProcessor}. It
     * delegates to {@link IOUtils#findResourceRelativeToClass} for path
     * resolution.</p>
     *
     * @param pluginClass the plugin class to resolve relative to
     * @return the configuration resource, or {@code null} if no config file
     *         found
     */
    public static Resource readPluginConfiguration(Class<?> pluginClass) {
        Resource ymlResource = getConfigurationResource(pluginClass, PLUGIN_YML_PATH);
        Resource groovyResource = getConfigurationResource(pluginClass, PLUGIN_GROOVY_PATH);

        boolean groovyResourceExists = groovyResource != null && groovyResource.exists();

        if (ymlResource != null && ymlResource.exists()) {
            if (groovyResourceExists) {
                throw new RuntimeException("A plugin [" + pluginClass.getName() +
                        "] may define a plugin.yml or a plugin.groovy, but not both");
            }
            return ymlResource;
        }
        if (groovyResourceExists) {
            return groovyResource;
        }
        return null;
    }

    /**
     * Finds a plugin configuration resource at the given path relative to the
     * plugin class, delegating to
     * {@link IOUtils#findResourceRelativeToClass}.
     *
     * @param pluginClass the plugin class to resolve relative to
     * @param configPath the path to probe (e.g., {@code "/plugin.yml"})
     * @return the resource wrapping the configuration URL, or {@code null} if
     *         not found
     */
    public static Resource getConfigurationResource(Class<?> pluginClass, String configPath) {
        URL urlToConfig = IOUtils.findResourceRelativeToClass(pluginClass, configPath);
        return urlToConfig != null ? new UrlResource(urlToConfig) : null;
    }

    /**
     * Reads a list-type property ({@code loadAfter}, {@code loadBefore}) from a
     * plugin instance and converts it to a {@code String} array.
     */
    @SuppressWarnings("unchecked")
    private static String[] readStringListProperty(BeanWrapper beanWrapper, String propertyName) {
        try {
            if (beanWrapper.isReadableProperty(propertyName)) {
                Object value = beanWrapper.getPropertyValue(propertyName);
                if (value instanceof List<?> list) {
                    return list.stream()
                            .map(Object::toString)
                            .toArray(String[]::new);
                }
            }
        } catch (Exception e) {
            LOG.trace("Could not read property [{}]: {}", propertyName, e.getMessage());
        }
        return new String[0];
    }

    /**
     * Reads the {@code dependsOn} map property from a plugin instance and
     * returns its keys as a {@code String} array. The {@code dependsOn}
     * property is a {@code Map<String, String>} where keys are dependency
     * plugin names and values are version constraints.
     */
    @SuppressWarnings("unchecked")
    private static String[] readDependsOnNames(BeanWrapper beanWrapper) {
        try {
            if (beanWrapper.isReadableProperty("dependsOn")) {
                Object value = beanWrapper.getPropertyValue("dependsOn");
                if (value instanceof Map<?, ?> map) {
                    return map.keySet().stream()
                            .map(Object::toString)
                            .toArray(String[]::new);
                }
            }
        } catch (Exception e) {
            LOG.trace("Could not read property [dependsOn]: {}", e.getMessage());
        }
        return new String[0];
    }

    /**
     * Lightweight value class holding plugin metadata extracted from a plugin
     * class.
     *
     * <p>This provides just enough information for the
     * {@link grails.boot.config.GrailsPluginEnvironmentPostProcessor} to sort
     * and filter plugins and load their configuration, without requiring a full
     * {@link grails.plugins.GrailsPlugin} instance.</p>
     *
     * @param name the logical plugin name (e.g., "core", "myPlugin")
     * @param pluginClass the plugin's class
     * @param loadAfterNames plugin names this plugin should load after
     * @param loadBeforeNames plugin names this plugin should load before
     * @param dependsOnNames plugin names this plugin depends on (used for
     *        transitive dependency resolution during filtering, not for
     *        sort ordering)
     */
    public record PluginMetadata(
            String name,
            Class<?> pluginClass,
            String[] loadAfterNames,
            String[] loadBeforeNames,
            String[] dependsOnNames) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PluginMetadata other)) return false;
            return name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return "PluginMetadata[" + name + "]";
        }
    }

    /**
     * Rich descriptor information parsed from a single
     * {@code META-INF/grails-plugin.xml} resource.
     *
     * <p>This captures all information that {@link CorePluginFinder} needs to
     * construct {@link BinaryGrailsPluginDescriptor} instances:</p>
     * <ul>
     *   <li>{@code xmlResource} — the Spring {@link Resource} for the XML
     *       descriptor file itself</li>
     *   <li>{@code pluginTypes} — fully-qualified class names from
     *       {@code <type>} elements (the plugin classes)</li>
     *   <li>{@code providedClassNames} — fully-qualified class names from
     *       {@code <resource>} elements (artefacts like domain classes,
     *       controllers, services provided by this plugin)</li>
     * </ul>
     *
     * @param xmlResource the Spring Resource pointing to the
     *        {@code META-INF/grails-plugin.xml} file
     * @param pluginTypes plugin class names from {@code <type>} elements
     * @param providedClassNames artefact class names from {@code <resource>}
     *        elements
     */
    public record PluginDescriptorInfo(
            Resource xmlResource,
            List<String> pluginTypes,
            List<String> providedClassNames) {
    }

    /**
     * SAX handler for parsing {@code META-INF/grails-plugin.xml} files.
     * Extracts {@code <type>} elements containing plugin class
     * fully-qualified names.
     */
    static class PluginXmlHandler extends DefaultHandler {

        private enum ParseState { IDLE, TYPE, RESOURCE }

        private ParseState state = ParseState.IDLE;
        private final List<String> pluginTypes = new ArrayList<>();
        private final List<String> pluginClasses = new ArrayList<>();
        private StringBuilder buffer = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            if ("type".equals(localName) || "type".equals(qName)) {
                state = ParseState.TYPE;
                buffer = new StringBuilder();
            } else if ("resource".equals(localName) || "resource".equals(qName)) {
                state = ParseState.RESOURCE;
                buffer = new StringBuilder();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (state == ParseState.TYPE || state == ParseState.RESOURCE) {
                buffer.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (state) {
                case TYPE:
                    pluginTypes.add(buffer.toString().trim());
                    break;
                case RESOURCE:
                    pluginClasses.add(buffer.toString().trim());
                    break;
                default:
                    break;
            }
            state = ParseState.IDLE;
        }

        public List<String> getPluginTypes() {
            return pluginTypes;
        }

        public List<String> getPluginClasses() {
            return pluginClasses;
        }
    }
}
