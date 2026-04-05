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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity

class HibernateManyToManyPropertySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HMMPA, HMMPB])
    }

    def "test HibernateManyToManyProperty basic methods"() {
        given:
        def entityA = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HMMPA.name)
        def property = (HibernateManyToManyProperty) entityA.getPropertyByName("others")

        expect:
        property.getHibernateAssociatedEntity().name == HMMPB.name
        property.getReferencedEntityName() == HMMPB.name
        property.isManyToMany()
        !property.isOneToMany()
        property.isLazy()
        !property.isAssociationColumnNullable()
    }
}

@Entity
class HMMPA {
    Long id
    static hasMany = [others: HMMPB]
    static mapping = {
        others joinTable: [name: "h_m_m_p_a_others"]
    }
}

@Entity
class HMMPB {
    Long id
    static hasMany = [owners: HMMPA]
    static belongsTo = [owners: HMMPA]
}
