package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.types.Association
import spock.lang.Issue

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
@Issue('https://github.com/grails/grails-core/issues/669')
class MappedByNoneSpec extends GormDatastoreSpec {

    void "Test that mapped by with a value of 'none' disables the mapping"() {
        given: "A unidirectional associated mapped with 'none'"
            Association association = session.mappingContext.getPersistentEntity(SoftballTeamPreference.name).getPropertyByName("players")

        expect:"The association to be unidirectional"
            !association.isBidirectional()

    }

    @Override
    List getDomainClasses() {
        [Player, SoftballTeamPreference]
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