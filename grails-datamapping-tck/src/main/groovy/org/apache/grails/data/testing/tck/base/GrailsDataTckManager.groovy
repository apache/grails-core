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

import java.beans.Introspector

import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session

abstract class GrailsDataTckManager {

    static final CURRENT_TEST_NAME = 'current.gorm.test'

    Session session

    abstract Session createSession()

    private Set<Class> domainClasses = []

    /**
     * Returns a defensive copy of the registered domain classes.
     * Mutating this array will not affect the manager state.
     */
    Class[] getDomainClasses() {
        domainClasses as Class[]
    }

    @Deprecated
    void addAllDomainClasses(Collection<Class> classes) {
        if (classes) {
            registerDomainClasses(classes as Class[])
        }
    }

    /**
     * Adds all the specified classes to the domain classes list.
     * @param classes The classes to add
     */
    void registerDomainClasses(Class... classes) {
        if (classes) {
            domainClasses.addAll(classes)
        }
    }

    void setupSpec() {
        // noop
    }

    /**
     * Runs once after the features of a spec. Flushes the JDK Introspector's caches for the current thread group:
     * Groovy reads the BeanInfo of a class when it builds its MetaClass, and the JDK keeps it in a cache keyed by the
     * class that releases an entry only when the class is unloaded, which the classes of a test JVM never are. A
     * domain class costs a few hundred method descriptors and each closure of a spec an entry of its own, so over the
     * several hundred specs of a module the cache grows to hundreds of megabytes and runs the suite out of heap. The
     * flush drops every entry of the thread group, those of earlier specs in the same fork included; a class a later
     * spec builds a MetaClass for is introspected again.
     * <p>
     * A manager that overrides this method must call {@code super.cleanupSpec()}, or its suite keeps the cache.
     */
    void cleanupSpec() {
        Introspector.flushCaches()
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
