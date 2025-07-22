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

import org.apache.grails.data.testing.tck.domains.Book
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.City
import org.apache.grails.data.testing.tck.domains.ClassWithListArgBeforeValidate
import org.apache.grails.data.testing.tck.domains.ClassWithNoArgBeforeValidate
import org.apache.grails.data.testing.tck.domains.ClassWithOverloadedBeforeValidate
import org.apache.grails.data.testing.tck.domains.CommonTypes
import org.apache.grails.data.testing.tck.domains.Country
import org.apache.grails.data.testing.tck.domains.EnumThing
import org.apache.grails.data.testing.tck.domains.Face
import org.apache.grails.data.testing.tck.domains.Highway
import org.apache.grails.data.testing.tck.domains.Location
import org.apache.grails.data.testing.tck.domains.ModifyPerson
import org.apache.grails.data.testing.tck.domains.Nose
import org.apache.grails.data.testing.tck.domains.OptLockNotVersioned
import org.apache.grails.data.testing.tck.domains.OptLockVersioned
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.PersonEvent
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.PetType
import org.apache.grails.data.testing.tck.domains.Plant
import org.apache.grails.data.testing.tck.domains.PlantCategory
import org.apache.grails.data.testing.tck.domains.Publication
import org.apache.grails.data.testing.tck.domains.Task
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import spock.lang.Specification

abstract class GrailsDataTckManager {
    static final CURRENT_TEST_NAME = 'current.gorm.test'

    Session session

    abstract Session createSession()

    List<Class> domainClasses = [
            Book,
            ChildEntity,
            City,
            ClassWithListArgBeforeValidate,
            ClassWithNoArgBeforeValidate,
            ClassWithOverloadedBeforeValidate,
            CommonTypes,
            Country,
            EnumThing,
            Face,
            Highway,
            Location,
            ModifyPerson,
            Nose,
            OptLockNotVersioned,
            OptLockVersioned,
            Person,
            PersonEvent,
            Pet,
            PetType,
            Plant,
            PlantCategory,
            Publication,
            Task,
            TestEntity
    ]

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
