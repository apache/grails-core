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
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * An entity that is also embedded in another can declare a hasMany. The collection is bound once for the
 * embedded component and once for the entity itself, and each needs its own element and key.
 */
class EmbeddedEntityWithHasManySpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        manager.registerDomainClasses(EmbeddedHolder, EmbeddedWithMany, EmbeddedMember)
    }

    void "the collection of an embedded entity is saved and read back with its owner"() {
        given:
        EmbeddedHolder holder = new EmbeddedHolder(embed: new EmbeddedWithMany())
        holder.embed.addToMembers(new EmbeddedMember(name: 'a'))
        holder.save(flush: true, failOnError: true)
        manager.session.clear()

        expect:
        EmbeddedHolder.get(holder.id).embed.members*.name == ['a']
    }
}

@Entity
class EmbeddedHolder {
    EmbeddedWithMany embed

    static embedded = ['embed']
}

@Entity
class EmbeddedWithMany {
    static hasMany = [members: EmbeddedMember]
}

@Entity
class EmbeddedMember {
    String name
}
