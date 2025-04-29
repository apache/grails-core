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

import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 01/02/16.
 */
@Issue('https://github.com/grails/grails-core/issues/9290')
class CascadeToBidirectionalAsssociationSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Club, Team, Player, Contract])
    }

    /**
     * This test currently fails because the association between Contract and Player is left unassigned
     */
    void "test cascades work correctly with a bidirectional association"() {
        when:
        Club c = new Club(name: "Padres").save()
        Team padres = new Team(
                name: "Padres 1",
                club: c
        )


        def p = new Player(
                name: "John",
                contract: new Contract(
                        salary: 40_000_000
                )
        )
        padres.addToPlayers(p)

        // Desired behavior: Team cascades saves down to Player, which
        // cascades its saves down to Contract
        padres.save(flush: true)
        then:
        padres.hasErrors()
        padres.errors.getFieldError('players[0].contract.player')

        when:"the contract id is assigned"
        p.contract.player = p
        padres.save(flush: true)

        then:"The object is saved"
        padres.id

    }
}
