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
package org.grails.datastore.mapping.model

import java.beans.PropertyDescriptor

import grails.gorm.annotation.Entity
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.types.mapping.SimpleWithMapping

class PersistentPropertySpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity author = mappingContext.addPersistentEntity(PPAuthor)

    @Shared
    PersistentEntity book = mappingContext.addPersistentEntity(PPBook)

    @Shared
    PersistentEntity child = mappingContext.addPersistentEntity(PPChild)

    void "basic property metadata comes from the descriptor"() {
        given:
        PersistentProperty name = author.getPropertyByName('name')

        expect:
        name.name == 'name'
        name.capitilizedName == 'Name'
        name.type == String
        name.owner.is(author)
        name.toString() == 'name:java.lang.String (' + SimpleWithMapping.name + ')'
        name.mappedForm instanceof Property
        name.mappedForm.is(name.mapping.mappedForm)
        name.nullable == name.mappedForm.nullable
        name.ownerClassName == PPAuthor.name
    }

    void "readers and writers are resolved lazily through the entity reflector"() {
        given:
        PersistentProperty name = author.getPropertyByName('name')
        PPAuthor instance = new PPAuthor(name: 'a')

        expect:
        name.reader.read(instance) == 'a'
        name.reader.is(name.reader)
        name.writer.is(name.writer)

        when:
        name.writer.write(instance, 'b')

        then:
        instance.name == 'b'
    }

    void "inheritance is detected against parent entities"() {
        expect:
        !author.getPropertyByName('name').inherited
        child.getPropertyByName('name').inherited
        !child.getPropertyByName('extra').inherited
    }

    @Unroll
    void "#entityName.#property defaults: unidirectionalOneToMany=#uni lazyAble=#lazy bidirectionalManyToOne=#bidi joinColumn=#join sorted=#sorted"() {
        given:
        PersistentEntity entity = entityName == 'author' ? author : book
        PersistentProperty p = property == 'id' ? entity.identity : entity.getPropertyByName(property)

        expect:
        p.unidirectionalOneToMany == uni
        p.lazyAble == lazy
        p.bidirectionalManyToOne == bidi
        p.supportsJoinColumnMapping() == join
        p.sorted == sorted

        where:
        entityName | property   || uni   | lazy  | bidi  | join  | sorted
        'author'   | 'books'    || false | false | false | false | true
        'author'   | 'name'     || false | true  | false | false | false
        'author'   | 'id'       || false | false | false | false | false
        'author'   | 'address'  || false | false | false | false | false
        'book'     | 'author'   || false | true  | true  | false | false
        'book'     | 'tags'     || true  | false | false | true  | false
        'book'     | 'keywords' || false | false | false | true  | false
    }

    void "identity and composite identity properties are recognised"() {
        expect:
        author.getPropertyByName('id').identityProperty
        !author.getPropertyByName('name').identityProperty
        !author.getPropertyByName('name').compositeIdProperty
    }

    void "composite identifier members report themselves as composite id properties"() {
        given:
        PersistentProperty first = Stub(PersistentProperty) { getName() >> 'first' }
        PersistentProperty second = Stub(PersistentProperty) { getName() >> 'second' }
        PersistentEntity composite = Stub(PersistentEntity) {
            getCompositeIdentity() >> ([first, second] as PersistentProperty[])
        }

        expect:
        new SimpleWithMapping(composite, mappingContext, new PropertyDescriptor('first', PPComposite)).compositeIdProperty
        new SimpleWithMapping(composite, mappingContext, new PropertyDescriptor('second', PPComposite)).compositeIdProperty
        !new SimpleWithMapping(composite, mappingContext, new PropertyDescriptor('other', PPComposite)).compositeIdProperty
    }

    void "a property without an owner cannot report its owner class name"() {
        given:
        PersistentProperty orphan = new SimpleWithMapping(null, mappingContext, new PropertyDescriptor('name', PPAuthor))

        when:
        orphan.ownerClassName

        then:
        IllegalMappingException e = thrown()
        e.message == 'Property [name] has no owner entity defined'
    }

    void "a property whose mapping is null cannot resolve a mapped form"() {
        given:
        PersistentProperty orphan = new SimpleWithMapping(null, mappingContext, new PropertyDescriptor('name', PPAuthor))

        expect:
        orphan.mapping == null

        when:
        orphan.mappedForm

        then:
        thrown(NullPointerException)
    }
}

@Entity
class PPAuthor {
    Long id
    Long version
    String name
    SortedSet books
    PPAddress address
    static hasMany = [books: PPBook]
    static embedded = ['address']
}

@Entity
class PPChild extends PPAuthor {
    String extra
}

@Entity
class PPBook {
    Long id
    String title
    PPAuthor author
    Set tags
    List<String> keywords
    static belongsTo = [author: PPAuthor]
    static hasMany = [tags: PPTag]
}

@Entity
class PPTag {
    Long id
    String name
}

class PPAddress {
    String street
}

class PPComposite {
    String first
    String second
    String other
}
