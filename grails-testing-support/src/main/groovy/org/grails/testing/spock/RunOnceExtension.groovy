package org.grails.testing.spock

import grails.testing.spock.RunOnce
import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.model.MethodInfo

@CompileStatic
class RunOnceExtension extends AbstractAnnotationDrivenExtension<RunOnce> {

    @Override
    void visitFixtureAnnotation(RunOnce annotation, MethodInfo fixtureMethod) {
        fixtureMethod.addInterceptor new RunOnceInterceptor()
    }
}
