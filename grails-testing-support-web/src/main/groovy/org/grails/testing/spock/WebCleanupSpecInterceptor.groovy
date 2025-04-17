package org.grails.testing.spock

import groovy.transform.CompileStatic
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class WebCleanupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        ConvertersConfigurationHolder.clear()
        invocation.proceed()
    }
}
