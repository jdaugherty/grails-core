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
package org.grails.plugins

import spock.lang.Specification

class GrailsPluginDiscoverySpec extends Specification {

    def "scanPluginDescriptorResources discovers descriptors with rich info from classpath"() {
        when:
        def descriptors = GrailsPluginDiscovery.scanPluginDescriptorResources(
                Thread.currentThread().getContextClassLoader())

        then: "At least one descriptor is found"
        !descriptors.isEmpty()

        and: "Each descriptor has an XML resource, plugin types, and provided class names list"
        descriptors.every { it.xmlResource() != null }
        descriptors.every { !it.pluginTypes().isEmpty() }

        and: "Core plugin classes are among the discovered types"
        def allTypes = descriptors.collectMany { it.pluginTypes() }
        allTypes.contains('org.grails.plugins.CoreGrailsPlugin')
        allTypes.contains('org.grails.plugins.domain.DomainClassGrailsPlugin')
    }

    def "scanPluginDescriptorResources returns empty list for classloader with no grails-plugin.xml"() {
        given:
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when:
        def descriptors = GrailsPluginDiscovery.scanPluginDescriptorResources(emptyClassLoader)

        then:
        descriptors.isEmpty()
    }

    def "scanPluginDescriptorResources captures provided class names from resource elements"() {
        given: "A grails-plugin.xml with both type and resource elements"
        def xml = '''<plugin name='test'>
            <type>com.example.TestGrailsPlugin</type>
            <resource>com.example.MyDomainClass</resource>
            <resource>com.example.MyService</resource>
        </plugin>'''
        def tempDir = File.createTempDir()
        def metaInfDir = new File(tempDir, 'META-INF')
        metaInfDir.mkdirs()
        new File(metaInfDir, 'grails-plugin.xml').text = xml
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when:
        def descriptors = GrailsPluginDiscovery.scanPluginDescriptorResources(classLoader)

        then:
        descriptors.size() == 1
        descriptors[0].pluginTypes() == ['com.example.TestGrailsPlugin']
        descriptors[0].providedClassNames() == ['com.example.MyDomainClass', 'com.example.MyService']
        descriptors[0].xmlResource() != null
        descriptors[0].xmlResource().exists()

        cleanup:
        tempDir.deleteDir()
    }

    def "scanPluginDescriptorResources handles malformed XML gracefully"() {
        given: "A classloader that returns a grails-plugin.xml with invalid content"
        def badXml = '<plugin><type>valid.Class</type><broken'
        def tempDir = File.createTempDir()
        def metaInfDir = new File(tempDir, 'META-INF')
        metaInfDir.mkdirs()
        new File(metaInfDir, 'grails-plugin.xml').text = badXml
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when:
        def descriptors = GrailsPluginDiscovery.scanPluginDescriptorResources(classLoader)

        then: "No exception is thrown, returns empty or partial results"
        noExceptionThrown()
        descriptors != null

        cleanup:
        tempDir.deleteDir()
    }

    def "scanPluginDescriptors delegates to scanPluginDescriptorResources and extracts class names"() {
        when:
        def classNames = GrailsPluginDiscovery.scanPluginDescriptors(
                Thread.currentThread().getContextClassLoader())

        then: "At least the core plugins declared in grails-core's grails-plugin.xml are found"
        classNames.size() >= 2
        classNames.contains('org.grails.plugins.CoreGrailsPlugin')
        classNames.contains('org.grails.plugins.domain.DomainClassGrailsPlugin')
    }

    def "scanPluginDescriptors returns empty list for classloader with no grails-plugin.xml"() {
        given:
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when:
        def classNames = GrailsPluginDiscovery.scanPluginDescriptors(emptyClassLoader)

        then:
        classNames.isEmpty()
    }

    def "getLogicalPluginName derives correct name from plugin class"() {
        expect:
        GrailsPluginDiscovery.getLogicalPluginName(pluginClass) == expectedName

        where:
        pluginClass                        || expectedName
        DiscoveryTestCoreGrailsPlugin      || 'discoveryTestCore'
        DiscoveryTestSimpleGrailsPlugin    || 'discoveryTestSimple'
        DiscoveryTestABCGrailsPlugin       || 'discoveryTestABC'
    }

    def "extractPluginMetadata extracts loadAfter, loadBefore, and dependsOn"() {
        when:
        def metadata = GrailsPluginDiscovery.extractPluginMetadata(PluginWithAllOrderingGrailsPlugin)

        then:
        metadata != null
        metadata.name() == 'pluginWithAllOrdering'
        metadata.loadAfterNames() == ['alpha', 'beta'] as String[]
        metadata.loadBeforeNames() == ['gamma'] as String[]
        metadata.dependsOnNames() == ['delta'] as String[]
    }

    def "extractPluginMetadata returns empty arrays for plugin with no ordering declarations"() {
        when:
        def metadata = GrailsPluginDiscovery.extractPluginMetadata(DiscoveryTestSimpleGrailsPlugin)

        then:
        metadata != null
        metadata.name() == 'discoveryTestSimple'
        metadata.loadAfterNames().length == 0
        metadata.loadBeforeNames().length == 0
        metadata.dependsOnNames().length == 0
    }

    def "extractPluginMetadata returns null for class not ending in GrailsPlugin"() {
        expect:
        GrailsPluginDiscovery.extractPluginMetadata(String) == null
    }

    def "extractPluginMetadata returns null for null input"() {
        expect:
        GrailsPluginDiscovery.extractPluginMetadata(null) == null
    }

    def "extractPluginMetadata handles plugin that throws on instantiation"() {
        when:
        def metadata = GrailsPluginDiscovery.extractPluginMetadata(FailingConstructorGrailsPlugin)

        then: "Returns metadata with default (empty) ordering arrays"
        metadata != null
        metadata.name() == 'failingConstructor'
        metadata.loadAfterNames().length == 0
        metadata.loadBeforeNames().length == 0
        metadata.dependsOnNames().length == 0
    }

    def "extractPluginMetadata extracts multiple dependsOn names"() {
        when:
        def metadata = GrailsPluginDiscovery.extractPluginMetadata(PluginWithMultipleDepsGrailsPlugin)

        then:
        metadata != null
        metadata.name() == 'pluginWithMultipleDeps'
        metadata.dependsOnNames() as Set == ['core', 'i18n'] as Set
    }

    def "readPluginConfiguration returns null when no plugin.yml or plugin.groovy exists"() {
        when:
        def resource = GrailsPluginDiscovery.readPluginConfiguration(DiscoveryTestSimpleGrailsPlugin)

        then: "No config file exists for this test fixture class"
        resource == null
    }

    def "getConfigurationResource returns resource that does not exist for non-existent path"() {
        when:
        def resource = GrailsPluginDiscovery.getConfigurationResource(
                DiscoveryTestSimpleGrailsPlugin, '/nonexistent.yml')

        then: "IOUtils constructs a URL but the resource does not exist on disk"
        resource == null || !resource.exists()
    }

    def "PluginMetadata equality is based on name only"() {
        given:
        def meta1 = new GrailsPluginDiscovery.PluginMetadata(
                'test', String, ['a'] as String[], [] as String[], [] as String[])
        def meta2 = new GrailsPluginDiscovery.PluginMetadata(
                'test', Integer, ['b'] as String[], ['c'] as String[], ['d'] as String[])

        expect:
        meta1 == meta2
        meta1.hashCode() == meta2.hashCode()
    }

    def "PluginMetadata with different names are not equal"() {
        given:
        def meta1 = new GrailsPluginDiscovery.PluginMetadata(
                'alpha', String, [] as String[], [] as String[], [] as String[])
        def meta2 = new GrailsPluginDiscovery.PluginMetadata(
                'beta', String, [] as String[], [] as String[], [] as String[])

        expect:
        meta1 != meta2
    }

    def "PluginMetadata toString includes name"() {
        given:
        def meta = new GrailsPluginDiscovery.PluginMetadata(
                'myPlugin', String, [] as String[], [] as String[], [] as String[])

        expect:
        meta.toString() == 'PluginMetadata[myPlugin]'
    }

    def "PluginXmlHandler parses type and resource elements"() {
        given:
        def xml = '''<plugin name='test'>
            <type>com.example.TestGrailsPlugin</type>
            <type>com.example.OtherGrailsPlugin</type>
            <resource>com.example.SomeClass</resource>
            <resource>com.example.AnotherClass</resource>
        </plugin>'''

        def handler = new GrailsPluginDiscovery.PluginXmlHandler()
        def parser = org.grails.io.support.SpringIOUtils.newSAXParser()

        when:
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then:
        handler.pluginTypes == ['com.example.TestGrailsPlugin', 'com.example.OtherGrailsPlugin']
        handler.pluginClasses == ['com.example.SomeClass', 'com.example.AnotherClass']
    }

    def "PluginXmlHandler returns empty lists for XML with no type or resource elements"() {
        given:
        def xml = '<plugin name="empty"></plugin>'
        def handler = new GrailsPluginDiscovery.PluginXmlHandler()
        def parser = org.grails.io.support.SpringIOUtils.newSAXParser()

        when:
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then:
        handler.pluginTypes.isEmpty()
        handler.pluginClasses.isEmpty()
    }

    def "PluginXmlHandler trims whitespace from element content"() {
        given:
        def xml = '''<plugin name='test'>
            <type>
                com.example.TestGrailsPlugin
            </type>
        </plugin>'''

        def handler = new GrailsPluginDiscovery.PluginXmlHandler()
        def parser = org.grails.io.support.SpringIOUtils.newSAXParser()

        when:
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then:
        handler.pluginTypes == ['com.example.TestGrailsPlugin']
    }

    def "constants have expected values"() {
        expect:
        GrailsPluginDiscovery.CORE_PLUGIN_PATTERN == 'META-INF/grails-plugin.xml'
        GrailsPluginDiscovery.PLUGIN_YML == 'plugin.yml'
        GrailsPluginDiscovery.PLUGIN_GROOVY == 'plugin.groovy'
        GrailsPluginDiscovery.DEFAULT_CONFIG_IGNORE_LIST == ['dataSource', 'hibernate']
    }
}

// Test fixture plugin classes for GrailsPluginDiscoverySpec

class DiscoveryTestCoreGrailsPlugin {
    def version = '1.0'
}

class DiscoveryTestSimpleGrailsPlugin {
    def version = '1.0'
}

class DiscoveryTestABCGrailsPlugin {
    def version = '1.0'
}

class PluginWithAllOrderingGrailsPlugin {
    def version = '1.0'
    def loadAfter = ['alpha', 'beta']
    def loadBefore = ['gamma']
    def dependsOn = [delta: '1.0']
}

class FailingConstructorGrailsPlugin {
    def version = '1.0'

    FailingConstructorGrailsPlugin() {
        throw new RuntimeException("Intentional failure for testing")
    }
}

class PluginWithMultipleDepsGrailsPlugin {
    def version = '1.0'
    def dependsOn = [core: '1.0', i18n: '1.0']
}
