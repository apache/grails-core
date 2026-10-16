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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.unbootable.EmbeddedCollectionOwnerA
import grails.unbootable.EmbeddedCollectionOwnerB
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.PendingFeature

/**
 * Pins a defect of the domain binder found while auditing what {@link GrailsDomainGenerator} rejects: a collection inside an
 * embedded type is registered under a role built from the embedded type and the embedded property ({@code
 * EmbeddedCollectionHolder.inner.words}), and given a table and a key column named after the type, not the owner, so two
 * owners that embed the same type collide and Hibernate refuses the duplicate collection definition at boot. The generator
 * rejects such a type by name instead of copying that. This feature reports as fixed when the binder is.
 */
class GrailsDomainBinderEmbeddedCollectionDefectSpec extends HibernateGormDatastoreSpec {

    @PendingFeature(reason = 'the role, the table and the key column of a collection inside an embedded type come from the type, so two owners of the type collide')
    void "two entities can embed a type that has a collection"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(EmbeddedCollectionOwnerA, EmbeddedCollectionOwnerB)

        then:
        notThrown(Exception)

        cleanup:
        datastore?.close()
    }
}
