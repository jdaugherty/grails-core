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
package org.apache.grails.core.plugins

import spock.lang.Specification

class GrailsPluginDiscoverySpec extends Specification {

     def "scanPluginDescriptorResources discovers descriptors with rich info from classpath"() {
         when:
         def descriptors = GrailsPluginUtils.scanPluginDescriptorResources(
                 Thread.currentThread().getContextClassLoader())

         then: "At least one descriptor is found"
         !descriptors.isEmpty()

         and: "Each descriptor has a resource"
         descriptors.every { it.resource() != null }

         and: "The provided class names may be empty if only <type> elements are in the XML"
         descriptors != null  // Just verify we got results, don't assume what's in providedClassNames
     }

    def "scanPluginDescriptorResources returns empty list for classloader with no grails-plugin.xml"() {
        given:
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when:
        def descriptors = GrailsPluginUtils.scanPluginDescriptorResources(emptyClassLoader)

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
         def descriptors = GrailsPluginUtils.scanPluginDescriptorResources(classLoader)

         then: "The descriptor only includes <resource> elements, not <type> elements"
         descriptors.size() == 1
         descriptors[0].providedClasses() == ['com.example.MyDomainClass', 'com.example.MyService']
         descriptors[0].resource() != null
         descriptors[0].resource().exists()

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
        def descriptors = GrailsPluginUtils.scanPluginDescriptorResources(classLoader)

        then: "No exception is thrown, returns empty or partial results"
        noExceptionThrown()
        descriptors != null

        cleanup:
        tempDir.deleteDir()
    }

     def "scanPluginDescriptors delegates to scanPluginDescriptorResources and extracts class names"() {
         given: "A grails-plugin.xml with known <type> elements"
         def xml = '''<plugin name='test'>
             <type>com.example.AlphaGrailsPlugin</type>
             <type>com.example.BetaGrailsPlugin</type>
         </plugin>'''
         def tempDir = File.createTempDir()
         def metaInfDir = new File(tempDir, 'META-INF')
         metaInfDir.mkdirs()
         new File(metaInfDir, 'grails-plugin.xml').text = xml
         def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

         when:
         def classNames = GrailsPluginUtils.scanPluginDescriptors(classLoader)

         then: "Returns class names from <type> elements in grails-plugin.xml"
         classNames == ['com.example.AlphaGrailsPlugin', 'com.example.BetaGrailsPlugin']

         cleanup:
         tempDir.deleteDir()
     }

    def "scanPluginDescriptors returns empty list for classloader with no grails-plugin.xml"() {
        given:
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when:
        def classNames = GrailsPluginUtils.scanPluginDescriptors(emptyClassLoader)

        then:
        classNames.isEmpty()
    }

     def "getLogicalPluginName derives correct name from plugin class"() {
         expect:
         GrailsPluginUtils.getLogicalPluginName(pluginClass) == expectedName

         where:
         pluginClass                        || expectedName
         DiscoveryTestCoreGrailsPlugin      || 'discoveryTestCore'
         DiscoveryTestSimpleGrailsPlugin    || 'discoveryTestSimple'
         DiscoveryTestABCGrailsPlugin       || 'discoveryTestABC'
     }

     def "PluginMetadata equality is based on name only"() {
         given:
         def meta1 = new GrailsPluginLoadMetadata(
                 'test',  // name
                 '1.0',  // pluginVersion
                 '4.0.0',  // grailsVersion
                 String,  // pluginClass
                 ['a'] as String[],  // loadAfterNames
                 [] as String[],  // loadBeforeNames
                 [:],  // dependencies
                 [] as String[],  // dependsOnNames
                 [] as String[],  // evictions
                 [] as String[],  // observedPluginNames
                 [:],  // environments
                 true)  // enabled
         def meta2 = new GrailsPluginLoadMetadata(
                 'test',  // name
                 '2.0',  // pluginVersion (different)
                 '5.0.0',  // grailsVersion (different)
                 Integer,  // pluginClass (different)
                 ['b'] as String[],  // loadAfterNames (different)
                 ['c'] as String[],  // loadBeforeNames (different)
                 ['dep': '1.0'],  // dependencies (different)
                 ['d'] as String[],  // dependsOnNames (different)
                 [] as String[],  // evictions
                 [] as String[],  // observedPluginNames
                 [:],  // environments
                 false)  // enabled (different)

         expect: "Metadata equality is based on name only, not other attributes"
         meta1 == meta2
         meta1.hashCode() == meta2.hashCode()
     }

     def "PluginMetadata with different names are not equal"() {
         given:
         def meta1 = new GrailsPluginLoadMetadata(
                 'alpha',  // name
                 '1.0',  // pluginVersion
                 '4.0.0',  // grailsVersion
                 String,  // pluginClass
                 [] as String[],  // loadAfterNames
                 [] as String[],  // loadBeforeNames
                 [:],  // dependencies
                 [] as String[],  // dependsOnNames
                 [] as String[],  // evictions
                 [] as String[],  // observedPluginNames
                 [:],  // environments
                 true)  // enabled
         def meta2 = new GrailsPluginLoadMetadata(
                 'beta',  // name (different)
                 '1.0',  // pluginVersion
                 '4.0.0',  // grailsVersion
                 String,  // pluginClass
                 [] as String[],  // loadAfterNames
                 [] as String[],  // loadBeforeNames
                 [:],  // dependencies
                 [] as String[],  // dependsOnNames
                 [] as String[],  // evictions
                 [] as String[],  // observedPluginNames
                 [:],  // environments
                 true)  // enabled

         expect:
         meta1 != meta2
     }

     def "PluginMetadata toString includes name"() {
         given:
         def meta = new GrailsPluginLoadMetadata(
                 'myPlugin',  // name
                 '1.0',  // pluginVersion
                 '4.0.0',  // grailsVersion
                 String,  // pluginClass
                 [] as String[],  // loadAfterNames
                 [] as String[],  // loadBeforeNames
                 [:],  // dependencies
                 [] as String[],  // dependsOnNames
                 [] as String[],  // evictions
                 [] as String[],  // observedPluginNames
                 [:],  // environments
                 true)  // enabled

         expect:
         meta.toString() == 'GrailsPluginClassMetadata[myPlugin]'
     }

    def "PluginXmlHandler parses type and resource elements"() {
        given:
        def xml = '''<plugin name='test'>
            <type>com.example.TestGrailsPlugin</type>
            <type>com.example.OtherGrailsPlugin</type>
            <resource>com.example.SomeClass</resource>
            <resource>com.example.AnotherClass</resource>
        </plugin>'''

        def handler = new PluginXmlHandler()
        def parser = org.grails.io.support.SpringIOUtils.newSAXParser()

        when:
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then:
        handler.pluginClassNames == ['com.example.TestGrailsPlugin', 'com.example.OtherGrailsPlugin']
        handler.providedClasses == ['com.example.SomeClass', 'com.example.AnotherClass']
    }

    def "PluginXmlHandler returns empty lists for XML with no type or resource elements"() {
        given:
        def xml = '<plugin name="empty"></plugin>'
        def handler = new PluginXmlHandler()
        def parser = org.grails.io.support.SpringIOUtils.newSAXParser()

        when:
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then:
        handler.pluginClassNames.isEmpty()
        handler.providedClasses.isEmpty()
    }

    def "PluginXmlHandler trims whitespace from element content"() {
        given:
        def xml = '''<plugin name='test'>
            <type>
                com.example.TestGrailsPlugin
            </type>
        </plugin>'''

        def handler = new PluginXmlHandler()
        def parser = org.grails.io.support.SpringIOUtils.newSAXParser()

        when:
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then:
        handler.pluginClassNames == ['com.example.TestGrailsPlugin']
    }

    def "constants have expected values"() {
        expect:
        GrailsPluginUtils.PLUGIN_XML_PATTERN == 'META-INF/grails-plugin.xml'
        GrailsPluginUtils.PLUGIN_YML_CONFIG == 'plugin.yml'
        GrailsPluginUtils.PLUGIN_GROOVY_CONFIG == 'plugin.groovy'
        GrailsPluginUtils.DEFAULT_CONFIG_IGNORE_LIST == ['dataSource', 'hibernate']
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


