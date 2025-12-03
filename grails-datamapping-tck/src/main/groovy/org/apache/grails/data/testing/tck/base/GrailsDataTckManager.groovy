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


import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import spock.lang.Specification

abstract class GrailsDataTckManager {
    static final CURRENT_TEST_NAME = 'current.gorm.test'

    Session session

    abstract Session createSession()

    private List<Class> domainClasses = [
//            Book,
//            ChildEntity,
//            City,
//            ClassWithListArgBeforeValidate,
//            ClassWithNoArgBeforeValidate,
//            ClassWithOverloadedBeforeValidate,
//            CommonTypes,
//            Country,
//            EnumThing,
//            Face,
//            Highway,
//            Location,
//            ModifyPerson,
//            Nose,
//            OptLockNotVersioned,
//            OptLockVersioned,
//            Person,
//            PersonEvent,
//            Pet,
//            PetType,
//            Plant,
//            PlantCategory,
//            Publication,
//            Task,
//            TestEntity
    ]

    /**
     * Returns an unmodifiable view of the domain classes list.
     * @return An unmodifiable list of domain classes
     */
    List<Class> getDomainClasses() {
        return Collections.unmodifiableList(domainClasses)
    }

    /**
     * Adds all the specified classes to the domain classes list.
     * @param classes The classes to add
     */
    void addAllDomainClasses(Collection<Class> classes) {
        domainClasses.addAll(classes)
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
}
