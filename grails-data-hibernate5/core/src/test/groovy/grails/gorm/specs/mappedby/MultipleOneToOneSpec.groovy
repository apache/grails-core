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
package grails.gorm.specs.mappedby

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 29/05/2017.
 */
class MultipleOneToOneSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Org, OrgMember])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/950')
    void "test mappedBy with multiple many-to-one and a single one-to-one"() {
        given:
        Org branch = new Org(id: 1, name: "branch a").save()
        new OrgMember(org: branch).save(flush: true)
        def query = OrgMember.where({ branch == null })

        expect:
        query.updateAll(branch: branch) == 1
        OrgMember.findByBranch(branch)
    }
}


@Entity
class Org {

    String name

    OrgMember member

    static mappedBy = [member: "org"]

    static constraints = {
        member nullable: true
    }

    static mapping = {
        id generator: "assigned"
    }

}

@Entity
class OrgMember {
    static belongsTo = [org: Org]

    Org branch
    Org division
    Org region

    static mappedBy = [branch: "none", division: "none", region: "none"]

    static constraints = {
        org nullable: false
        branch nullable: true
        division nullable: true
        region nullable: true
    }

}