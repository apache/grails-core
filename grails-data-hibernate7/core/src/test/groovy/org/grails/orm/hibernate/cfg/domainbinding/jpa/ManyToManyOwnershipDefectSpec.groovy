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

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import spock.lang.PendingFeature

/**
 * Pins a defect of the domain binder in the many-to-many ownership rules, found by comparing it with the annotations
 * {@link GrailsDomainGenerator} generates. The generator does not copy it: it follows the owning side. The feature reports
 * as fixed when the binder is. A many-to-many that neither side owns, the other defect found that way, no longer boots: see
 * {@code ManyToManyWithoutOwnerSpec}.
 */
class ManyToManyOwnershipDefectSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(DefectNamedOwner, DefectNamedInverse)
    }

    @PendingFeature(reason = 'the inverse side names its join table from its own mapping, not from the owner that names it, so it reads another table')
    void "the inverse side of a many-to-many sees the rows the owner wrote when only the owner names the join table"() {
        given:
        DefectNamedInverse inverse = new DefectNamedInverse(name: 'inverse')
        DefectNamedOwner owner = new DefectNamedOwner(name: 'owner')
        owner.addToOthers(inverse)
        owner.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectNamedInverse.get(inverse.id).owners*.name == ['owner']
    }
}

@Entity
class DefectNamedOwner {

    String name
    Set<DefectNamedInverse> others

    static hasMany = [others: DefectNamedInverse]

    static mapping = {
        others joinTable: [name: 'defect_named_others']
    }
}

@Entity
class DefectNamedInverse {

    String name
    Set<DefectNamedOwner> owners

    static hasMany = [owners: DefectNamedOwner]
    static belongsTo = [owners: DefectNamedOwner]
}
