package grails.spring

import grails.util.Holders
import grails.core.DefaultGrailsApplication
import org.grails.spring.context.support.GrailsPlaceholderConfigurer
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class GrailsPlaceholderConfigurerSpec extends Specification {

    void cleanup() {
        Holders.setConfig(null)
    }

    void "Test that property placeholder configuration works for simple properties in map syntax"() {
        when:"A bean is defined with a placeholder"
            def application = new DefaultGrailsApplication()
            application.config.foo = [bar: "test"]
            def bb = new BeanBuilder()
            bb.beans {
                addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('${', application.config.toProperties()))
                testBean(TestBean) {
                    name = '${foo.bar}'
                }
            }
            def applicationContext = bb.createApplicationContext()
            def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
            bean.name == "test"

    }

    void "Test that property placeholder configuration works for simple properties in dot syntax"() {
        when:"A bean is defined with a placeholder"
        def application = new DefaultGrailsApplication()
        application.config.foo.bar="test"
        def bb = new BeanBuilder()
        bb.beans {
            addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('${', application.config.toProperties()))
            testBean(TestBean) {
                name = '${foo.bar}'
            }
        }
        def applicationContext = bb.createApplicationContext()
        def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
        bean.name == "test"

    }

    @Issue('GRAILS-9490')
    void "Test that property placeholder configuration doesn't throw an error if invalid placeholders are configured"() {
        when:"A bean is defined with a placeholder"
        def application = new DefaultGrailsApplication()
        application.config.bar = [foo: "test"]
        application.config.more = [stuff: 'another ${place.holder}']
        def bb = new BeanBuilder()
        bb.beans {
            addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('${', application.config.toProperties()))
            testBean(TestBean) {
                name = '${foo.bar}'
            }
        }
        def applicationContext = bb.createApplicationContext()
        def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
        bean.name == '${foo.bar}'

    }

    void "Test that property placeholder configuration works for simple properties with a custom placeholder prefix"() {
        when:"A bean is defined with a placeholder"
        def application = new DefaultGrailsApplication()
        application.config.foo = [bar: "test"]
        application.config['grails.spring.placeholder.prefix']='£{'
        application.setConfig(application.config)
        def bb = new BeanBuilder()
        bb.beans {
            addBeanFactoryPostProcessor(new GrailsPlaceholderConfigurer('£{', application.config.toProperties()))
            testBean(TestBean) {
                name = '£{foo.bar}'
            }
        }
        def applicationContext = bb.createApplicationContext()
        def bean = applicationContext.getBean(TestBean)
        then:"The placeholder is replaced"
        bean.name == "test"

    }
}
class TestBean {
    String name
}
