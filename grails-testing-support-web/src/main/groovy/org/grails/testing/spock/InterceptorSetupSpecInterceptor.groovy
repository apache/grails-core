package org.grails.testing.spock

import grails.testing.web.interceptor.InterceptorUnitTest
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.plugins.web.interceptors.GrailsInterceptorHandlerInterceptorAdapter
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class InterceptorSetupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        InterceptorUnitTest test = (InterceptorUnitTest)invocation.instance
        setup(test)
        invocation.proceed()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    void setup(InterceptorUnitTest test) {
        test.defineBeans {
            grailsInterceptorHandlerInterceptorAdapter(GrailsInterceptorHandlerInterceptorAdapter)
        }
    }
}
