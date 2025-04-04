package org.grails.testing.gorm.spock

import grails.testing.gorm.DataTest
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class DataTestSetupInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        DataTest test = (DataTest)invocation.instance
        SimpleMapDatastore simpleDatastore = test.applicationContext.getBean(SimpleMapDatastore)
        test.currentSession = simpleDatastore.connect()
        DatastoreUtils.bindSession(test.currentSession)
        invocation.proceed()
    }
}
