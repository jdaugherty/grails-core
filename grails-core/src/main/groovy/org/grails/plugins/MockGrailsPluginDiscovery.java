package org.grails.plugins;

import grails.plugins.GrailsPlugin;
import org.apache.grails.core.plugins.GrailsPluginDiscovery;
import org.apache.grails.core.plugins.GrailsPluginInfo;
import org.apache.grails.core.plugins.GrailsPluginUtils;

public class MockGrailsPluginDiscovery extends GrailsPluginDiscovery {

    public MockGrailsPluginDiscovery() {
        super();
        reset(); // do not search on the classpath by default
    }

    public MockGrailsPluginDiscovery(Class<?>[] pluginClasses) {
        super(pluginClasses);
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
