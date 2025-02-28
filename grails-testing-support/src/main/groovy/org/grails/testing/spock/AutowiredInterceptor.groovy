package org.grails.testing.spock

import grails.testing.spring.AutowiredTest
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@Slf4j
@CompileStatic
class AutowiredInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        ((AutowiredTest)invocation.instance).autowire()
        invocation.proceed()
    }
}