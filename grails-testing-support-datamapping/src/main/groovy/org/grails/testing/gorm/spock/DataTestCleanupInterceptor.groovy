package org.grails.testing.gorm.spock

import grails.testing.gorm.DataTest
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation

@CompileStatic
class DataTestCleanupInterceptor implements IMethodInterceptor {

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        cleanupDataTest((DataTest)invocation.instance)
        invocation.proceed()
    }

    void cleanupDataTest(DataTest testInstance) {
        if (testInstance.currentSession != null) {
            testInstance.currentSession.disconnect()
            DatastoreUtils.unbindSession(testInstance.currentSession)
        }
        SimpleMapDatastore simpleDatastore = testInstance.applicationContext.getBean(SimpleMapDatastore)
        simpleDatastore.clearData()
    }
}
