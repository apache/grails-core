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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.model.types.Association
import spock.lang.Issue

/**
 * @author graemerocher
 */
@Issue('https://github.com/grails/grails-core/issues/669')
class MappedByNoneSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Player, SoftballTeamPreference])
    }

    void "Test that mapped by with a value of 'none' disables the mapping"() {
        given: "A unidirectional associated mapped with 'none'"
        Association association = manager.session.mappingContext.getPersistentEntity(SoftballTeamPreference.name).getPropertyByName("players")

        expect: "The association to be unidirectional"
        !association.isBidirectional()
    }
}

@Entity
class Player {

    Long id
    String name
    SoftballTeamPreference softballTeampreference
    static hasOne = [softballTeampreference: SoftballTeamPreference]
}

@Entity
class SoftballTeamPreference {
    Long id
    Set players
    Player owner

    static constraints = {
    }


    static belongsTo = [owner: Player]
    static hasMany = [players: Player]
    static mappedBy = [players: "none"]
}