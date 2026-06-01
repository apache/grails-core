/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.base

import spock.lang.Specification


import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session

abstract class GrailsDataTckManager {

    static final CURRENT_TEST_NAME = 'current.gorm.test'

    Session session

    abstract Session createSession()

    private Set<Class> domainClasses = [
    ]

    /**
     * Returns a defensive copy of the registered domain classes.
     * Mutating this array will not affect the manager state.
     */
    Class[] getDomainClasses() {
        return domainClasses as Class[]
    }

    /**
     * @deprecated Use {@link #registerDomainClasses(Class...)} instead.
     */
    @Deprecated
    void addAllDomainClasses(Collection<Class> classes) {
       registerDomainClasses(classes as Class[])
    }

    /**
     * Registers the domain classes that will be available when testing.
     * Note: Direct callers of addAllDomainClasses have not been refactored because there are
     * over 180 callers, and IntelliJ IDE does not support automatic inlining/refactoring of
     * Groovy methods. Thus, addAllDomainClasses remains as a deprecated wrapper to avoid
     * massive manual file modifications.
     * @param classes The classes to register
     */
    void registerDomainClasses(Class... classes) {
        if (classes) {
            domainClasses.addAll(classes)
        }
    }

    void setupSpec() {
        // noop
    }

    void cleanupSpec() {
        // noop
    }

    void cleanRegistry() {
        for (Class domainClass : domainClasses) {
            GroovySystem.metaClassRegistry.removeMetaClass(domainClass)
        }
    }

    void setup(Class<? extends Specification> spec) {
        System.setProperty(CURRENT_TEST_NAME, spec.getClass().simpleName - 'Spec')
        session = createSession()
        DatastoreUtils.bindSession(session)
    }

    void cleanup() {
        System.clearProperty(CURRENT_TEST_NAME)

        try {
            if (session) {
                session.disconnect()
                DatastoreUtils.unbindSession(session)
            }
        }
        catch (ignored) {

        }

        try {
            destroy()
        }
        catch (ignored) {

        }

        cleanRegistry()
    }

    void destroy() {
        // noop
    }

    boolean supportsMultipleDataSources() {
        false
    }

    void setupMultiDataSource(Class... domainClasses) {
        // noop - override in implementations that support multiple datasources
    }

    void cleanupMultiDataSource() {
        // noop - override in implementations that support multiple datasources
    }

    def getServiceForConnection(Class serviceType, String connectionName) {
        null
    }

    boolean supportsMultiTenantMultiDataSource() {
        false
    }

    void setupMultiTenantMultiDataSource(Class... domainClasses) {
        // noop - override in implementations that support DISCRIMINATOR multi-tenancy + multiple datasources
    }

    void cleanupMultiTenantMultiDataSource() {
        // noop - override in implementations that support DISCRIMINATOR multi-tenancy + multiple datasources
    }

    def getServiceForMultiTenantConnection(Class serviceType, String connectionName) {
        null
    }
}
