/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests.generated

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.persister.collection.CollectionPersister

/**
 * A collection mapped {@code lazy: true} is extra-lazy: {@code size()}, {@code contains()} and {@code isEmpty()} ask the
 * database and leave the collection uninitialized. Hibernate 7's annotation binder cannot state that, so the generated-class
 * binding sets it on the bound collection; the specs read it back from the session factory and from a loaded owner.
 */
class GeneratedDomainClassesExtraLazySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        registerGeneratedDomainClasses(GdcLazyOwner, GdcLazyItem, GdcLazyTagged, GdcPlainOwner)
    }

    private static CollectionPersister persister(def sessionFactory, Class owner, String property) {
        return sessionFactory.mappingMetamodel.getCollectionDescriptor("${owner.name}.${property}".toString())
    }

    def "a collection mapped lazy true is bound extra-lazy, one mapped with the default is not"() {
        expect:
        persister(sessionFactory, GdcLazyOwner, 'items').isExtraLazy()
        persister(sessionFactory, GdcLazyTagged, 'tags').isExtraLazy()
        !persister(sessionFactory, GdcPlainOwner, 'plainItems').isExtraLazy()
    }

    def "size, contains and isEmpty of an extra-lazy collection of entities do not initialize it"() {
        given:
        GdcLazyOwner owner = new GdcLazyOwner(name: 'o')
        owner.addToItems(new GdcLazyItem(label: 'a'))
        owner.addToItems(new GdcLazyItem(label: 'b'))
        owner.save(flush: true)
        sessionFactory.currentSession.clear()

        when:
        GdcLazyOwner loaded = GdcLazyOwner.get(owner.id)
        int itemCount = loaded.items.size()
        boolean empty = loaded.items.isEmpty()

        then:
        itemCount == 2
        !empty
        !((PersistentCollection) loaded.items).wasInitialized()
    }

    def "size of an extra-lazy collection of values does not initialize it"() {
        given:
        GdcLazyTagged owner = new GdcLazyTagged(name: 't')
        owner.addToTags('x')
        owner.addToTags('y')
        owner.save(flush: true)
        sessionFactory.currentSession.clear()

        when:
        GdcLazyTagged loaded = GdcLazyTagged.get(owner.id)
        int tagCount = loaded.tags.size()

        then:
        tagCount == 2
        !((PersistentCollection) loaded.tags).wasInitialized()
    }
}

@Entity
class GdcLazyOwner {

    String name
    Set<GdcLazyItem> items

    static hasMany = [items: GdcLazyItem]

    static mapping = {
        items lazy: true
    }
}

@Entity
class GdcLazyTagged {

    String name
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags lazy: true
    }
}

@Entity
class GdcPlainOwner {

    String name
    Set<GdcLazyItem> plainItems

    static hasMany = [plainItems: GdcLazyItem]
}

@Entity
class GdcLazyItem {

    String label
}
