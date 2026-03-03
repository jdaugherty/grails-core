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
