package grails.views


import org.grails.config.CodeGenConfig
import org.springframework.beans.BeanUtils
import spock.lang.Specification

import java.beans.PropertyDescriptor

class GenericViewConfigurationSpec extends Specification {

    void "test setting of boolean"() {
        given:
        def testClass = new TestClass()
        String yml = 'grails.views.json.compileStatic: false'
        CodeGenConfig config = new CodeGenConfig()
        config.loadYml(new ByteArrayInputStream(yml.bytes))

        expect:
        testClass.compileStatic
        !testClass.useAbsoluteLinks

        when: "no relevant properties in the config"
        testClass.readConfiguration(config)

        then: "the properties don't change from the defaults"
        !testClass.compileStatic
        !testClass.useAbsoluteLinks
    }
}

class TestClass implements GenericViewConfiguration {
    @Override
    String getViewModuleName() {
        "json"
    }

    PropertyDescriptor[] findViewConfigPropertyDescriptor() {
        BeanUtils.getPropertyDescriptors(GenericViewConfiguration)
    }
}
