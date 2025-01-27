package org.grails.testing.gorm.spock

import grails.testing.gorm.DataTest
import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo

@CompileStatic
class GormTestingSupportExtension implements IGlobalExtension {

    DataTestSetupSpecInterceptor dataTestSetupSpecInterceptor = new DataTestSetupSpecInterceptor()
    DataTestSetupInterceptor dataTestSetupInterceptor = new DataTestSetupInterceptor()
    DataTestCleanupInterceptor dataTestCleanupInterceptor = new DataTestCleanupInterceptor()
    DataTestCleanupSpecInterceptor dataTestCleanupSpecInterceptor = new DataTestCleanupSpecInterceptor()

    @Override
    void visitSpec(SpecInfo spec) {
        if (DataTest.isAssignableFrom(spec.reflection)) {
            spec.addSetupSpecInterceptor(dataTestSetupSpecInterceptor)
            spec.addSetupInterceptor(dataTestSetupInterceptor)
            spec.addCleanupInterceptor(dataTestCleanupInterceptor)
            spec.addCleanupSpecInterceptor(dataTestCleanupSpecInterceptor)
        }
    }
}
