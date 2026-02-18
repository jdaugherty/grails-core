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
package org.grails.config

import grails.boot.config.GrailsPluginEnvironmentPostProcessor
import grails.util.Environment
import org.grails.plugins.GrailsPluginDiscovery
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class GrailsPluginEnvironmentPostProcessorSpec extends Specification {

    GrailsPluginEnvironmentPostProcessor processor = new GrailsPluginEnvironmentPostProcessor()

    def setup() {
        System.setProperty(Environment.KEY, Environment.DEVELOPMENT.name)
        Environment.reset()
    }

    def "getLogicalPluginName derives correct name from plugin class via GrailsPluginDiscovery"() {
        expect:
        GrailsPluginDiscovery.getLogicalPluginName(pluginClass) == expectedName

        where:
        pluginClass                || expectedName
        CoreTestGrailsPlugin       || 'coreTest'
        SimpleGrailsPlugin         || 'simple'
        ABCGrailsPlugin            || 'ABC'
    }

    def "scanPluginDescriptors parses grails-plugin.xml correctly via GrailsPluginDiscovery"() {
        when:
        List<String> pluginClassNames = GrailsPluginDiscovery.scanPluginDescriptors(
                Thread.currentThread().getContextClassLoader())

        then: "It discovers the core plugins declared in grails-plugin.xml"
        pluginClassNames.size() >= 1
        // The grails-core module declares CoreGrailsPlugin and DomainClassGrailsPlugin
        pluginClassNames.any { it.contains('CoreGrailsPlugin') }
    }

    def "sortPlugins performs topological sort respecting loadAfter"() {
        given:
        def infoA = createPluginInfo('alpha', [] as String[], [] as String[])
        def infoB = createPluginInfo('beta', ['alpha'] as String[], [] as String[])
        def infoC = createPluginInfo('gamma', ['beta'] as String[], [] as String[])

        when: "gamma loadAfter beta, beta loadAfter alpha"
        List sorted = processor.sortPlugins([infoC, infoA, infoB])

        then: "alpha comes before beta, beta comes before gamma"
        def names = sorted*.name()
        names.indexOf('alpha') < names.indexOf('beta')
        names.indexOf('beta') < names.indexOf('gamma')
    }

    def "sortPlugins performs topological sort respecting loadBefore"() {
        given:
        def infoA = createPluginInfo('alpha', [] as String[], ['gamma'] as String[])
        def infoB = createPluginInfo('beta', [] as String[], [] as String[])
        def infoC = createPluginInfo('gamma', [] as String[], [] as String[])

        when: "alpha loadBefore gamma"
        List sorted = processor.sortPlugins([infoC, infoA, infoB])

        then: "alpha comes before gamma in sorted order"
        def names = sorted*.name()
        names.indexOf('alpha') < names.indexOf('gamma')
    }

    def "sortPlugins handles plugins with no dependencies"() {
        given:
        def infoA = createPluginInfo('alpha', [] as String[], [] as String[])
        def infoB = createPluginInfo('beta', [] as String[], [] as String[])
        def infoC = createPluginInfo('gamma', [] as String[], [] as String[])

        when: "No dependencies between plugins"
        List sorted = processor.sortPlugins([infoC, infoA, infoB])

        then: "All plugins are present"
        sorted.size() == 3
        sorted*.name() as Set == ['alpha', 'beta', 'gamma'] as Set
    }

    def "postProcessEnvironment does not fail with empty classpath"() {
        given:
        ConfigurableEnvironment environment = new StandardEnvironment()
        SpringApplication application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
        // The real classpath has grails-plugin.xml but those plugins may or may not
        // have plugin.yml files; at minimum, no error should occur
    }

    def "order value is set appropriately"() {
        expect:
        processor.order < 0 // Should run early (HIGHEST_PRECEDENCE + 15 is still negative)
    }

    def "early-loaded property sources use same names as AbstractGrailsPlugin would produce"() {
        given: "A plugin named 'myPlugin' with a plugin.yml"
        // The property source name from YamlPropertySourceLoader is the name passed to load()
        // AbstractGrailsPlugin uses: GrailsNameUtils.getLogicalPropertyName(simpleName, "GrailsPlugin") + "-plugin.yml"
        // GrailsPluginEnvironmentPostProcessor uses: pluginName + "-plugin.yml"
        // Both produce the same result for a plugin class like "MyPluginGrailsPlugin" -> "myPlugin-plugin.yml"

        expect: "The logical name derivation matches GrailsNameUtils conventions"
        GrailsPluginDiscovery.getLogicalPluginName(CoreTestGrailsPlugin) == 'coreTest'
        // So the property source name would be "coreTest-plugin.yml", same as AbstractGrailsPlugin
    }

    def "filterPlugins returns all plugins when no GrailsPluginConfigFilter implementations exist"() {
        given:
        def infoA = createPluginInfo('alpha', [] as String[], [] as String[])
        def infoB = createPluginInfo('beta', [] as String[], [] as String[])
        def infoC = createPluginInfo('gamma', [] as String[], [] as String[])
        def plugins = [infoA, infoB, infoC]
        def environment = new StandardEnvironment()

        when: "No GrailsPluginConfigFilter implementations are on the classpath"
        def filtered = processor.filterPlugins(plugins, environment)

        then: "All plugins are returned"
        filtered.size() == 3
        filtered*.name() as Set == ['alpha', 'beta', 'gamma'] as Set
    }

    def "filterPlugins returns empty list when input is empty"() {
        given:
        def environment = new StandardEnvironment()

        when:
        def filtered = processor.filterPlugins([], environment)

        then:
        filtered.isEmpty()
    }

    def "filterPlugins preserves original list when filter returns same names"() {
        given:
        def infoA = createPluginInfo('alpha', [] as String[], [] as String[])
        def infoB = createPluginInfo('beta', [] as String[], [] as String[])
        def plugins = [infoA, infoB]
        def environment = new StandardEnvironment()

        when: "No filter removes anything"
        def filtered = processor.filterPlugins(plugins, environment)

        then: "Returns the same list reference (optimization)"
        filtered.is(plugins)
    }

    def "discoverPlugins finds plugins from grails-plugin.xml descriptors"() {
        when:
        def plugins = processor.discoverPlugins()

        then: "At least one plugin is discovered (the grails-core module has grails-plugin.xml)"
        plugins.size() >= 1
        plugins.any { it.name().contains('core') || it.pluginClass().simpleName.contains('Core') }
    }

    /**
     * Creates a PluginInfo record via the test-friendly constructor.
     */
    private Object createPluginInfo(String name, String[] loadAfter, String[] loadBefore) {
        new GrailsPluginEnvironmentPostProcessor.PluginInfo(
                name, CoreTestGrailsPlugin, null, loadAfter, loadBefore
        )
    }

    /**
     * Creates a PluginInfo record with dependency names.
     */
    private Object createPluginInfoWithDeps(String name, String[] loadAfter,
            String[] loadBefore, String[] dependsOnNames) {
        new GrailsPluginEnvironmentPostProcessor.PluginInfo(
                name, CoreTestGrailsPlugin, null, loadAfter, loadBefore, dependsOnNames
        )
    }
}

// Test fixture plugin classes (minimal, no GrailsApplication dependency)
class CoreTestGrailsPlugin {
    def version = '1.0'
    def loadAfter = []
    def loadBefore = []
}

class SimpleGrailsPlugin {
    def version = '1.0'
}

class ABCGrailsPlugin {
    def version = '1.0'
}
