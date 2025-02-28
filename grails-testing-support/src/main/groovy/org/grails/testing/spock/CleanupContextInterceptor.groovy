package org.grails.testing.spock

import groovy.transform.CompileStatic
import org.grails.testing.GrailsUnitTest
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class CleanupContextInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        ((GrailsUnitTest)invocation.instance).cleanupGrailsApplication()
        invocation.proceed()
    }
}
