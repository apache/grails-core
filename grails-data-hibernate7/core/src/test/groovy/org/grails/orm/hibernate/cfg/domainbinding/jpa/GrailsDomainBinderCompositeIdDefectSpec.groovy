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
import grails.unbootable.UnbootableComposite
import grails.unbootable.UnbootableJoinToComposite
import grails.unbootable.UnbootableJoinedChild
import grails.unbootable.UnbootableJoinedParent
import grails.unbootable.UnbootableParts
import grails.unbootable.UnbootableRefToParts
import grails.unbootable.UnbootableRefToTop
import grails.unbootable.UnbootableTarget
import grails.unbootable.UnbootableTop
import grails.unbootable.UnbootableMiddle
import grails.unbootable.UnbootableFlat
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.PendingFeature

/**
 * Pins a defect of the domain binder found while describing composite identifiers: a unidirectional collection of entities that
 * have a composite identifier is bound through a join table, and the binder gives the element its columns from the column configs
 * of the collection property (one for each identifier property of the target) and then reads the same configs again for the key,
 * which should point at the owner. The key gets the element's columns and Hibernate refuses the foreign key ("must have same number
 * of columns as the referenced primary key"), so the mapping cannot boot. The generator rejects such a collection by name. This
 * feature reports as fixed when the binder is.
 */
class GrailsDomainBinderCompositeIdDefectSpec extends HibernateGormDatastoreSpec {

    @PendingFeature(reason = 'the key of a join table to a composite identifier gets the columns of the element, so Hibernate refuses the foreign key')
    void "a unidirectional collection of entities with a composite identifier can be bound"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(UnbootableJoinToComposite, UnbootableComposite)

        then:
        notThrown(Exception)

        cleanup:
        datastore?.close()
    }

    @PendingFeature(reason = 'ForeignKeyColumnCountCalculator counts a composite identifier part that refers to an entity with a simple identifier as no column, so the foreign key has fewer columns than the key and Hibernate refuses it')
    void "a foreign key to a composite identifier with a part that refers to an entity can be bound"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(UnbootableRefToParts, UnbootableParts, UnbootableTarget)

        then:
        notThrown(Exception)

        cleanup:
        datastore?.close()
    }

    @PendingFeature(reason = 'CompositeIdentifierToManyToOneBinder expands a nested composite identifier one level only, so a foreign key to a composite identifier three levels deep has fewer columns than the key')
    void "a foreign key to a composite identifier three levels deep can be bound"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(UnbootableRefToTop, UnbootableTop, UnbootableMiddle, UnbootableFlat)

        then:
        notThrown(Exception)

        cleanup:
        datastore?.close()
    }

    @PendingFeature(reason = 'the key of a joined subclass is bound with one column, so a joined subclass of an entity with a composite identifier cannot be bound')
    void "a joined subclass of an entity with a composite identifier can be bound"() {
        when:
        HibernateDatastore datastore = new HibernateDatastore(UnbootableJoinedParent, UnbootableJoinedChild)

        then:
        notThrown(Exception)

        cleanup:
        datastore?.close()
    }
}
