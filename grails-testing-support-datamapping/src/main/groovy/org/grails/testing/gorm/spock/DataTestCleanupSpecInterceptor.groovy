package org.grails.testing.gorm.spock

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class DataTestCleanupSpecInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        invocation.proceed()
    }
}
