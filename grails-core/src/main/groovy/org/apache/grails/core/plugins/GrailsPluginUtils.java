package org.apache.grails.core.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import grails.io.IOUtils;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsVersionUtils;
import grails.plugins.Plugin;
import grails.plugins.VersionComparator;
import grails.plugins.exceptions.PluginException;
import grails.util.Environment;
import grails.util.GrailsClassUtils;
import grails.util.GrailsNameUtils;
import org.grails.io.support.SpringIOUtils;
import org.grails.plugins.DefaultGrailsPlugin;

public class GrailsPluginUtils {

    /**
     * The classpath location of the Grails plugin descriptor XML files.
     */
    public static final String PLUGIN_XML_PATTERN = "META-INF/grails-plugin.xml";
    /**
     * The filename for YAML-based plugin configuration.
     */
    public static final String PLUGIN_YML_CONFIG = "plugin.yml";
    /**
     * The filename for Groovy ConfigSlurper-based plugin configuration.
     */
    public static final String PLUGIN_GROOVY_CONFIG = "plugin.groovy";
    /**
     * Default config keys to ignore when loading plugin configuration.
     */
    public static final List<String> DEFAULT_CONFIG_IGNORE_LIST = Arrays.asList("dataSource", "hibernate");

    /**
     * The name of the field that contains the supported Grails version expression on a Grails Plugin
     */
    public static final String PLUGIN_GRAILS_VERSION_FIELD = "grailsVersion";

    private static final String PLUGIN_GROOVY_CONFIG_PATH = "/" + PLUGIN_GROOVY_CONFIG;
    private static final String PLUGIN_YML_CONFIG_PATH = "/" + PLUGIN_YML_CONFIG;
    private static final Logger LOG = LoggerFactory.getLogger(GrailsPluginUtils.class);
    private static final String GRAILS_PLUGIN_SUFFIX = "GrailsPlugin";

    public GrailsPluginUtils() {
        // prevent instantiation - all methods are static
    }

    /**
     * Scans all {@code META-INF/grails-plugin.xml} resources on the classpath
     * and returns rich descriptor information for each.
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of {@link GrailsPluginDescriptor} records, one per
     *         {@code META-INF/grails-plugin.xml} resource found on the
     *         classpath
     */
    public static List<GrailsPluginDescriptor> scanPluginDescriptorResources(ClassLoader classLoader) {
        List<GrailsPluginDescriptor> descriptors = new ArrayList<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(PLUGIN_XML_PATTERN);
            SAXParser saxParser = SpringIOUtils.newSAXParser();

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream input = url.openStream()) {
                    PluginXmlHandler handler = new PluginXmlHandler();
                    saxParser.parse(input, handler);
                    Resource xmlResource = new UrlResource(url);
                    descriptors.add(new GrailsPluginDescriptor(
                            xmlResource,
                            handler.getPluginClassNames(),
                            handler.getProvidedClasses()
                    ));
                } catch (IOException | SAXException e) {
                    LOG.debug("Error parsing plugin descriptor at [{}]: {}", url, e.getMessage());
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOG.debug("Error scanning for plugin descriptors: {}", e.getMessage());
        }

        return descriptors;
    }

    public static GrailsPluginInfo createPluginInfo(Class<?> pluginClass, Resource descriptorResource, boolean dynamic) {
        List<String> pluginClassNames = List.of(pluginClass.getName());
        return createPluginInfoByDescriptor(pluginClass, new GrailsPluginDescriptor(descriptorResource, pluginClassNames, List.of()), dynamic);
    }

    public static GrailsPluginInfo createPluginInfoByDescriptor(Class<?> pluginClass, GrailsPluginDescriptor descriptor, boolean dynamic) {
        GrailsPluginLoadMetadata metadata = GrailsPluginUtils.extractPluginMetadata(pluginClass);
        Resource configResource = GrailsPluginUtils.readPluginConfiguration(pluginClass);
        return new GrailsPluginInfo(
                descriptor,
                metadata,
                configResource,
                dynamic
        );
    }

    public static String normalizePluginName(String name) {
        if (name.indexOf('-') > -1) {
            return GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(name);
        }
        return name;
    }

    /**
     * Convenience method that scans all {@code META-INF/grails-plugin.xml}
     * resources and returns just the plugin class names.
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of fully-qualified plugin class names discovered from
     *         {@code META-INF/grails-plugin.xml} descriptors
     */
    public static List<String> scanPluginDescriptors(ClassLoader classLoader) {
        List<GrailsPluginDescriptor> descriptors = scanPluginDescriptorResources(classLoader);
        List<String> pluginClassNames = new ArrayList<>();
        for (GrailsPluginDescriptor descriptor : descriptors) {
            pluginClassNames.addAll(descriptor.providedPlugins());
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
        return getLogicalPluginNameFromClassName(pluginClass.getSimpleName());
    }

    public static String getLogicalPluginNameFromClassName(String name) {
        return GrailsNameUtils.getLogicalPropertyName(name, GRAILS_PLUGIN_SUFFIX);
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
     * @return a {@link GrailsPluginLoadMetadata} record, or {@code null} if the class is
     *         not a valid Grails plugin
     */
    public static GrailsPluginLoadMetadata extractPluginMetadata(Class<?> pluginClass) {
        if (!isGrailsPluginClassNamedCorrectly(pluginClass)) {
            return null;
        }

        String pluginName = getLogicalPluginName(pluginClass);
        String pluginVersion;
        String grailsVersion;

        String[] loadAfterNames;
        String[] loadBeforeNames;
        String[] evictions;
        String[] observedPluginNames;
        PluginDependencies dependencies;
        Map<String, Set<Object>> environments;
        String status;

        Object pluginInstance;
        try {
            pluginInstance = pluginClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate plugin [" + pluginName + "]: ", e);
        }

        BeanWrapper beanWrapper = new BeanWrapperImpl(pluginInstance);

        pluginVersion = evaluatePluginVersion(beanWrapper, pluginInstance, pluginName);
        grailsVersion = getPluginGrailsVersion(pluginInstance, pluginName);
        dependencies = evaluatePluginDependencies(beanWrapper, pluginInstance);
        loadAfterNames = evaluatePluginLoadAfters(beanWrapper, pluginInstance, pluginName);
        loadBeforeNames = evaluatePluginLoadBefores(beanWrapper, pluginInstance, pluginName);
        evictions = evaluatePluginEvictionPolicy(beanWrapper, pluginInstance);
        observedPluginNames = evaluateObservedPlugins(beanWrapper, pluginInstance);
        status = evaluatePluginStatus(beanWrapper, pluginInstance);
        environments = evaluatePluginEnvironments(beanWrapper, pluginInstance);

        return new GrailsPluginLoadMetadata(
                pluginName,
                pluginVersion,
                grailsVersion,
                pluginClass,
                loadAfterNames,
                loadBeforeNames,
                dependencies.dependencies,
                dependencies.dependencyNames,
                evictions,
                observedPluginNames,
                environments,
                status != null && status.equalsIgnoreCase(DefaultGrailsPlugin.STATUS_ENABLED)
        );
    }

    private static String[] evaluateObservedPlugins(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.OBSERVE)) {
            return new String[0];
        }

        Object observeProperty = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(beanWrapper, pluginInstance, DefaultGrailsPlugin.OBSERVE);
        if (observeProperty instanceof Collection) {
            Collection observeList = (Collection) observeProperty;
            String[] observedPlugins = new String[observeList.size()];
            int j = 0;
            for (Object anObserveList : observeList) {
                String pluginName = anObserveList.toString();
                observedPlugins[j++] = pluginName;
            }
            return observedPlugins;
        }

        return new String[0];
    }

    private static Map<String, Set<Object>> evaluatePluginEnvironments(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(GrailsPlugin.ENVIRONMENTS)) {
            return new HashMap<>();
        }

        Function<Object, Object> converter = arguments -> {
            String envName = (String) arguments;
            Environment env = Environment.getEnvironment(envName);
            if (env != null) return env.getName();
            return arguments;
        };
        return evaluateIncludeExcludeProperty(pluginInstance, GrailsPlugin.ENVIRONMENTS, converter);
    }

    private static String[] evaluatePluginLoadAfters(BeanWrapper beanWrapper, Object pluginInstance, String pluginName) {
        if (!beanWrapper.isReadableProperty(GrailsPlugin.PLUGIN_LOAD_AFTER_NAMES)) {
            return new String[0];
        }

        List loadAfterNamesList = (List) GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(beanWrapper, pluginInstance, GrailsPlugin.PLUGIN_LOAD_AFTER_NAMES);
        if (loadAfterNamesList != null) {
            return (String[]) loadAfterNamesList.toArray(new String[loadAfterNamesList.size()]);
        }

        return new String[0];
    }

    private static String[] evaluatePluginLoadBefores(BeanWrapper beanWrapper, Object pluginInstance, String pluginName) {
        if (!beanWrapper.isReadableProperty(GrailsPlugin.PLUGIN_LOAD_BEFORE_NAMES)) {
            return new String[0];
        }

        List loadBeforeNamesList = (List) GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(beanWrapper, pluginInstance, GrailsPlugin.PLUGIN_LOAD_BEFORE_NAMES);
        if (loadBeforeNamesList != null) {
            return (String[]) loadBeforeNamesList.toArray(new String[loadBeforeNamesList.size()]);
        }

        return new String[0];
    }

    private static String[] evaluatePluginEvictionPolicy(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.EVICT)) {
            return new String[0];
        }

        List pluginsToEvict = (List) GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(beanWrapper, pluginInstance, DefaultGrailsPlugin.EVICT);
        if (pluginsToEvict == null) {
            return new String[0];
        }

        String[] evictions = new String[pluginsToEvict.size()];
        int index = 0;
        for (Object o : pluginsToEvict) {
            evictions[index++] = o == null ? "" : o.toString();
        }
        return evictions;
    }

    private static String evaluatePluginVersion(BeanWrapper beanWrapper, Object plugin, String name) {
        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.VERSION)) {
            throw new PluginException("Plugin [" + name + "] must specify a version!");
        }

        Object vobj = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, DefaultGrailsPlugin.VERSION);
        if (vobj == null) {
            throw new PluginException("Plugin [" + name + "] must specify a version. eg: def version = 0.1");
        }

        return vobj.toString();
    }

    private static String evaluatePluginStatus(BeanWrapper beanWrapper, Object plugin) {
        if (plugin instanceof Plugin) {
            return ((Plugin) plugin).enabled ? DefaultGrailsPlugin.STATUS_ENABLED : DefaultGrailsPlugin.STATUS_DISABLED;
        }

        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.STATUS)) {
            return DefaultGrailsPlugin.STATUS_ENABLED;
        }

        Object statusObj = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, DefaultGrailsPlugin.STATUS);
        if (statusObj != null) {
            return statusObj.toString().toLowerCase();
        }

        return DefaultGrailsPlugin.STATUS_ENABLED;
    }

    private record PluginDependencies(String[] dependencyNames, Map<String, Object> dependencies) {

    }

    private static PluginDependencies evaluatePluginDependencies(BeanWrapper beanWrapper, Object plugin) {
        try {
            if (beanWrapper.isReadableProperty(GrailsPlugin.DEPENDS_ON)) {
                Map<String, Object> dependencies = (Map) GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(beanWrapper, plugin, GrailsPlugin.DEPENDS_ON);
                String[] dependsOnNames = dependencies.keySet().toArray(new String[dependencies.size()]);
                return new PluginDependencies(dependsOnNames, dependencies);
            }
        } catch (Exception e) {
            LOG.trace("Could not read property [dependsOn]: {}", e.getMessage());
        }
        return new PluginDependencies(new String[0], new HashMap<>());
    }

    private static String getPluginGrailsVersion(Object pluginInstance, String pluginName) {
        try {
            final Object grailsVersionValue = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(pluginInstance, PLUGIN_GRAILS_VERSION_FIELD);
            return grailsVersionValue != null ? grailsVersionValue.toString() : null;
        } catch (Exception e) {
            LOG.warn("Could not determine Grails Plugin Version for plugin [{}], assuming compatible", pluginName);
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
            return null;
        }
    }

    /**
     * Reads the plugin configuration resource by probing for both
     * {@code plugin.yml} and {@code plugin.groovy} relative to the plugin
     * class.
     *
     * <p>Returns the resource for whichever exists, or {@code null} if neither
     * exists. Throws {@link RuntimeException} if both exist.</p>
     *
     * @param pluginClass the plugin class to resolve relative to
     * @return the configuration resource, or {@code null} if no config file
     *         found
     */
    public static Resource readPluginConfiguration(Class<?> pluginClass) {
        Resource ymlResource = getConfigurationResource(pluginClass, PLUGIN_YML_CONFIG_PATH);
        Resource groovyResource = getConfigurationResource(pluginClass, PLUGIN_GROOVY_CONFIG_PATH);

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

    public static boolean isGrailsPluginClassNamedCorrectly(Class<?> pluginClass) {
        return pluginClass != null && pluginClass.getName().endsWith(GRAILS_PLUGIN_SUFFIX);
    }

    public static boolean isGrailsPluginLoadable(Class<?> pluginClass) {
        return pluginClass != null && !Modifier.isAbstract(pluginClass.getModifiers()) && pluginClass != DefaultGrailsPlugin.class;
    }

    public static boolean isPluginVersionCompatible(String pluginVersion, String pluginSupportedVersion, String grailsVersion, String pluginDescription) {
        if (pluginSupportedVersion == null || pluginSupportedVersion.contains("@")) {
            LOG.debug("Plugin grails version is null or containing '@'. Compatibility check skipped.");
            return true;
        }

        final String pluginMinGrailsVersion = GrailsVersionUtils.getLowerVersion(pluginSupportedVersion);
        final String pluginMaxGrailsVersion = GrailsVersionUtils.getUpperVersion(pluginSupportedVersion);
        if (grailsVersion == null) {
            return true;
        }

        if (pluginMinGrailsVersion.equals("*")) {
            LOG.error("grailsVersion not formatted as expected, unable to determine compatibility.");
            return false;
        }

        VersionComparator comparator = new VersionComparator();

        if (pluginMinGrailsVersion.equals(pluginMaxGrailsVersion)) {
            //exact version compatibility required
            if (!grailsVersion.equals(pluginMinGrailsVersion)) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is not equal" +
                                " to the one that plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersion, grailsVersion);
                return false;
            }
        }
        if (!pluginMaxGrailsVersion.equals("*")) {
            // Case 1: max version not specified. Forward compatibility expected

            // minimum version required by plugin cannot be greater than grails app version
            if (comparator.compare(pluginMinGrailsVersion, grailsVersion) > 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is less" +
                                " than the plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersion, grailsVersion);
                return false;
            }
        } else {
            // Case 2: both max and min version specified. Strict compatibility expected

            // minimum version required by plugin cannot be greater than grails app version
            if (comparator.compare(pluginMinGrailsVersion, grailsVersion) > 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is less" +
                                " than the plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersion, grailsVersion);
                return false;
            }

            // maximum version required by plugin cannot be less than grails app version
            if (comparator.compare(pluginMaxGrailsVersion, grailsVersion) < 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is greater" +
                                " than the plugins max specified. Plugin is compatible with Grails versions {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersion, grailsVersion);
                return false;
            }
        }

        return true;
    }

    public static Map<String, Set<Object>> evaluateIncludeExcludeProperty(Object pluginBean, String name, Function<Object, Object> converter) {
        Map<String, Set<Object>> resultMap = new HashMap<>();
        Object propertyValue = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(pluginBean, name);
        if (propertyValue instanceof Map) {
            Map containedMap = (Map) propertyValue;

            Object includes = containedMap.get(DefaultGrailsPlugin.INCLUDES);
            evaluateAndAddIncludeExcludeObject(resultMap, includes, true, converter);

            Object excludes = containedMap.get(DefaultGrailsPlugin.EXCLUDES);
            evaluateAndAddIncludeExcludeObject(resultMap, excludes, false, converter);
        } else {
            evaluateAndAddIncludeExcludeObject(resultMap, propertyValue, true, converter);
        }
        return resultMap;
    }

    private static void evaluateAndAddIncludeExcludeObject(Map<String, Set<Object>> targetMap, Object includeExcludeObject, boolean include, Function<Object, Object> converter) {
        if (includeExcludeObject instanceof String) {
            final String includeExcludeString = (String) includeExcludeObject;
            evaluateAndAddToIncludeExcludeSet(targetMap, includeExcludeString, include, converter);
        } else if (includeExcludeObject instanceof List) {
            List includeExcludeList = (List) includeExcludeObject;
            evaluateAndAddListOfValues(targetMap, includeExcludeList, include, converter);
        }
    }

    private static void evaluateAndAddListOfValues(Map targetMap, List includeExcludeList, boolean include, Function<Object, Object> converter) {
        for (Object value : includeExcludeList) {
            if (value instanceof String) {
                final String scopeName = (String) value;
                evaluateAndAddToIncludeExcludeSet(targetMap, scopeName, include, converter);
            }
        }
    }

    private static void evaluateAndAddToIncludeExcludeSet(Map<String, Set<Object>> targetMap, String includeExcludeString, boolean include, Function<Object, Object> converter) {
        Set<Object> set = lazilyCreateIncludeOrExcludeSet(targetMap, include);
        set.add(converter.apply(includeExcludeString));
    }

    private static Set<Object> lazilyCreateIncludeOrExcludeSet(Map<String, Set<Object>> targetMap, boolean include) {
        String key = include ? DefaultGrailsPlugin.INCLUDES : DefaultGrailsPlugin.EXCLUDES;
        return targetMap.computeIfAbsent(key, k -> new HashSet<>());
    }

    public static boolean supportsValueInIncludeExcludeMap(Map<String, Set<Object>> includeExcludeMap, Object value) {
        if (includeExcludeMap.isEmpty()) {
            return true;
        }

        Set<Object> includes = includeExcludeMap.get(DefaultGrailsPlugin.INCLUDES);
        if (includes != null) {
            return includes.contains(value);
        }

        Set<Object> excludes = includeExcludeMap.get(DefaultGrailsPlugin.EXCLUDES);
        return !(excludes != null && excludes.contains(value));
    }
}
