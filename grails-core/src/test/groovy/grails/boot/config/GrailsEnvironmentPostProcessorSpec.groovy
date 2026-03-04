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
package grails.boot.config

import grails.util.Environment
import org.apache.grails.core.plugins.GrailsPluginDiscovery
import org.springframework.boot.ConfigurableBootstrapContext
import org.springframework.boot.SpringApplication
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class GrailsEnvironmentPostProcessorSpec extends Specification {

    def setup() {
        System.setProperty(Environment.KEY, Environment.DEVELOPMENT.name)
        Environment.reset()
    }

    def cleanup() {
        Environment.reset()
    }

    def "postProcessEnvironment loads plugin configurations when discovery bean is available"() {
        given:
        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        def discovery = Mock(GrailsPluginDiscovery)
        bootstrapContext.get(GrailsPluginDiscovery.class) >> discovery
        discovery.getPlugins(_) >> []
        
        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
    }

    def "postProcessEnvironment handles missing discovery bean gracefully"() {
        given:
        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        bootstrapContext.get(GrailsPluginDiscovery.class) >> null
        
        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)
        def environment = new StandardEnvironment()
        def application = Mock(SpringApplication)

        when:
        processor.postProcessEnvironment(environment, application)

        then:
        noExceptionThrown()
    }

    def "order value is set appropriately for early loading"() {
        given:
        def bootstrapContext = Mock(ConfigurableBootstrapContext)
        bootstrapContext.get(GrailsPluginDiscovery.class) >> null
        
        def processor = new GrailsEnvironmentPostProcessor(bootstrapContext)

        expect:
        processor.order < 0  // Should run early (HIGHEST_PRECEDENCE + 15 is still negative)
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
