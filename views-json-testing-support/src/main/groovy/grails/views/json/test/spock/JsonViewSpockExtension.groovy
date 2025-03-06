package grails.views.json.test.spock

import grails.views.json.test.JsonViewUnitTest
import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo

@CompileStatic
class JsonViewSpockExtension implements IGlobalExtension {

    JsonViewSetupSpecInterceptor jsonViewSetupSpecInterceptor = new JsonViewSetupSpecInterceptor()

    @Override
    void visitSpec(SpecInfo spec) {
        if (JsonViewUnitTest.isAssignableFrom(spec.reflection)) {
            spec.addSetupSpecInterceptor(jsonViewSetupSpecInterceptor)
        }
    }
}
