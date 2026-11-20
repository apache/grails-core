/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.tests

import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity

/**
 * Hibernate converts String identifiers to the entity's numeric id type and preserves the
 * supplied order in {@code getAll}. This is not a universal GORM contract (for example MongoDB
 * ObjectId identifiers are not derived from arbitrary numeric strings), so it lives in the
 * Hibernate modules rather than the shared TCK.
 */
class HibernateGetAllConvertibleIdSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(TestEntity, ChildEntity)
    }

    void 'getAll preserves order for convertible String ids'() {
        given:
        def bob = new TestEntity(name: 'Bob', age: 40, child: new ChildEntity(name: 'Bob Child')).save()
        def fred = new TestEntity(name: 'Fred', age: 41, child: new ChildEntity(name: 'Fred Child')).save()
        def barney = new TestEntity(name: 'Barney', age: 42, child: new ChildEntity(name: 'Barney Child')).save()
        manager.session.flush()

        when: 'String ids that need conversion are supplied in a specific order'
        def results = TestEntity.getAll([barney.id.toString(), bob.id.toString(), fred.id.toString()])

        then: 'the converted ids preserve the requested order'
        ['Barney', 'Bob', 'Fred'] == results*.name
    }
}
