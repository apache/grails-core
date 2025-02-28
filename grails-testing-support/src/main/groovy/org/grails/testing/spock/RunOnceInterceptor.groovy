package org.grails.testing.spock

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.AbstractMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class RunOnceInterceptor extends AbstractMethodInterceptor {
    boolean hasRun = false

    @Override
    void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
        if(!hasRun) {
            hasRun = true
            invocation.proceed()
        }
    }
}
