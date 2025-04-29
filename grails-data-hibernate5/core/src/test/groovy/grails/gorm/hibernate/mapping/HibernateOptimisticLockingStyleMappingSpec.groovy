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
package grails.gorm.hibernate.mapping

import grails.persistence.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.hibernate.boot.Metadata
import org.hibernate.engine.OptimisticLockStyle
import org.hibernate.mapping.PersistentClass

class HibernateOptimisticLockingStyleMappingSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([HibernateOptLockingStyleVersioned, HibernateOptLockingStyleNotVersioned])
    }

    void testEvaluateHibernateOptimisticLockStyleIsDefined() {
        setup:
        Metadata hibernateMetadata = manager.hibernateDatastore.getMetadata()

        when: 'Find out Hibernate PersistentClass representations for our domains'
        PersistentClass forVersioned = hibernateMetadata.getEntityBinding(HibernateOptLockingStyleVersioned.name)
        PersistentClass forNotVersioned = hibernateMetadata.getEntityBinding(HibernateOptLockingStyleNotVersioned.name)

        then:
        forVersioned.optimisticLockStyle == OptimisticLockStyle.VERSION
        forNotVersioned.optimisticLockStyle == OptimisticLockStyle.NONE
    }
}


@Entity
class HibernateOptLockingStyleVersioned implements Serializable {
    Long id
    Long version

    String name
}

@Entity
class HibernateOptLockingStyleNotVersioned implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        version false
    }
}