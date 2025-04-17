package org.grails.testing.spock

import grails.testing.web.UrlMappingsUnitTest
import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class UrlMappingSetupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        ((UrlMappingsUnitTest)invocation.instance).configuredMockedControllers()
        invocation.proceed()
    }
}
