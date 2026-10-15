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

import grails.gorm.annotation.Entity

/**
 * An entity that is also embedded in another is bound twice, once as a component and once as an entity, and each
 * binding of its hasMany needs its own collection. The set, list and map second passes all have to keep theirs.
 */
class EmbeddedEntityCollectionKindsSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(
                EmbKindsHolder, EmbKindsSetOwner, EmbKindsListOwner, EmbKindsMapOwner, EmbKindsMember)
    }

    void "the set of an embedded entity is saved and read back with its owner"() {
        given:
        EmbKindsHolder holder = new EmbKindsHolder(name: 'set', setOwner: new EmbKindsSetOwner())
        holder.setOwner.addToMembers(new EmbKindsMember(name: 'a'))
        holder.setOwner.addToMembers(new EmbKindsMember(name: 'b'))
        holder.save(flush: true, failOnError: true)
        session.clear()

        expect:
        EmbKindsHolder.get(holder.id).setOwner.members*.name.sort() == ['a', 'b']
    }

    void "the list of an embedded entity is saved and read back in order"() {
        given:
        EmbKindsHolder holder = new EmbKindsHolder(name: 'list', listOwner: new EmbKindsListOwner())
        holder.listOwner.addToMembers(new EmbKindsMember(name: 'first'))
        holder.listOwner.addToMembers(new EmbKindsMember(name: 'second'))
        holder.save(flush: true, failOnError: true)
        session.clear()

        expect:
        EmbKindsHolder.get(holder.id).listOwner.members*.name == ['first', 'second']
    }

    void "the map of an embedded entity is saved and read back by key"() {
        given:
        EmbKindsHolder holder = new EmbKindsHolder(name: 'map', mapOwner: new EmbKindsMapOwner())
        holder.mapOwner.members = [x: new EmbKindsMember(name: 'ex'), y: new EmbKindsMember(name: 'why')]
        holder.save(flush: true, failOnError: true)
        session.clear()

        when:
        Map<String, EmbKindsMember> members = EmbKindsHolder.get(holder.id).mapOwner.members

        then:
        members.keySet() == ['x', 'y'] as Set
        members.x.name == 'ex'
        members.y.name == 'why'
    }
}

@Entity
class EmbKindsHolder {
    String name
    EmbKindsSetOwner setOwner
    EmbKindsListOwner listOwner
    EmbKindsMapOwner mapOwner

    static embedded = ['setOwner', 'listOwner', 'mapOwner']
    static constraints = {
        setOwner nullable: true
        listOwner nullable: true
        mapOwner nullable: true
    }
}

@Entity
class EmbKindsSetOwner {
    static hasMany = [members: EmbKindsMember]
}

@Entity
class EmbKindsListOwner {
    List<EmbKindsMember> members
    static hasMany = [members: EmbKindsMember]
}

@Entity
class EmbKindsMapOwner {
    Map<String, EmbKindsMember> members
    static hasMany = [members: EmbKindsMember]
}

@Entity
class EmbKindsMember {
    String name
}
