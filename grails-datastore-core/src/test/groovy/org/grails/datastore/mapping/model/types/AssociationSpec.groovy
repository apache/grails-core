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
package org.grails.datastore.mapping.model.types

import grails.gorm.annotation.Entity
import jakarta.persistence.CascadeType
import jakarta.persistence.FetchType
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class AssociationSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity author = mappingContext.addPersistentEntity(ASAuthor)

    @Shared
    PersistentEntity book = mappingContext.addPersistentEntity(ASBook)

    @Shared
    PersistentEntity profile = mappingContext.addPersistentEntity(ASProfile)

    @Shared
    PersistentEntity node = mappingContext.addPersistentEntity(ASNode)

    @Shared
    PersistentEntity left = mappingContext.addPersistentEntity(ASLeft)

    @Shared
    PersistentEntity right = mappingContext.addPersistentEntity(ASRight)

    @Shared
    PersistentEntity student = mappingContext.addPersistentEntity(ASStudent)

    @Shared
    PersistentEntity course = mappingContext.addPersistentEntity(ASCourse)

    private Association assoc(PersistentEntity entity, String name) {
        (Association) entity.getPropertyByName(name)
    }

    void "bidirectional associations know their inverse side"() {
        given:
        Association books = assoc(author, 'books')
        Association bookAuthor = assoc(book, 'author')
        Association tags = assoc(book, 'tags')

        expect:
        books.bidirectional
        books.referencedPropertyName == 'author'
        books.associatedEntity.is(book)
        books.inverseSide.is(bookAuthor)
        bookAuthor.inverseSide.is(books)
        !tags.bidirectional
        tags.inverseSide == null
        books.fetchStrategy == FetchType.LAZY
    }

    void "the owning side drives the default cascade behaviour"() {
        given:
        Association books = assoc(author, 'books')
        Association bookAuthor = assoc(book, 'author')
        Association tags = assoc(book, 'tags')

        expect:
        books.owningSide
        books.doesCascade(CascadeType.REMOVE)
        books.doesCascade(CascadeType.PERSIST, CascadeType.MERGE)
        books.doesCascade((CascadeType[]) null)
        !bookAuthor.owningSide
        !bookAuthor.doesCascade(CascadeType.PERSIST)
        !bookAuthor.doesCascade((CascadeType[]) null)
        !tags.owningSide
        tags.doesCascade(CascadeType.PERSIST)
        !tags.doesCascade(CascadeType.REMOVE)
        !books.orphanRemoval
    }

    void "cascade validation follows the mapped cascadeValidate setting"() {
        given:
        Association books = assoc(author, 'books')
        Association bookAuthor = assoc(book, 'author')
        Association tags = assoc(book, 'tags')
        Association notes = assoc(author, 'notes')
        Association owned = assoc(author, 'owned')
        ASBook dirty = new ASBook()
        ASBook clean = new ASBook()
        clean.trackChanges()

        expect:
        books.doesCascadeValidate(new ASBook())
        !bookAuthor.doesCascadeValidate(new ASAuthor())
        tags.doesCascadeValidate(new ASTag())
        !notes.doesCascadeValidate(new ASNote())
        !assoc(mappingContext.getPersistentEntity(ASNote.name), 'author').doesCascadeValidate(new ASAuthor())
        owned.doesCascadeValidate(new ASOwned())

        and: 'dirty cascade validation only validates changed dirty checkable objects'
        assoc(author, 'dirtyBooks').doesCascadeValidate(dirty)
        !assoc(author, 'dirtyBooks').doesCascadeValidate(clean)
        assoc(author, 'dirtyBooks').doesCascadeValidate('not dirty checkable')
    }

    void "association kinds are reported through the predicate methods"() {
        expect:
        assoc(author, 'address').embedded
        assoc(author, 'addresses').embedded
        !assoc(author, 'books').embedded
        assoc(book, 'keywords').basic
        !assoc(book, 'tags').basic
        assoc(book, 'keywords').list
        !assoc(author, 'books').list
        assoc(node, 'children').circular
        !assoc(author, 'books').circular
        assoc(author, 'books').correctlyOwned
        !assoc(book, 'author').correctlyOwned
        !assoc(book, 'keywords').correctlyOwned
        assoc(author, 'profile').hasOne
        !assoc(book, 'author').hasOne
        assoc(author, 'profile').oneToOne
        assoc(author, 'books').oneToMany
        assoc(book, 'author').manyToOne
        assoc(student, 'courses').manyToMany
        !assoc(author, 'books').manyToMany
        assoc(author, 'notes').bidirectionalToManyMap
        !assoc(author, 'books').bidirectionalToManyMap
        assoc(book, 'keywords').toString() == ASBook.name + '->keywords'
    }

    void "one-to-one associations can bind a single column when the other side owns them"() {
        expect:
        assoc(right, 'left').canBindOneToOneWithSingleColumnAndForeignKey()
        !assoc(left, 'right').canBindOneToOneWithSingleColumnAndForeignKey()
        !assoc(author, 'profile').canBindOneToOneWithSingleColumnAndForeignKey()
        !assoc(profile, 'author').canBindOneToOneWithSingleColumnAndForeignKey()
        !assoc(book, 'tags').canBindOneToOneWithSingleColumnAndForeignKey()
    }

    void "to-one associations expose the foreign key location"() {
        given:
        ToOne toOne = (ToOne) assoc(author, 'profile')
        ToOne bookAuthor = (ToOne) assoc(book, 'author')

        expect:
        toOne.foreignKeyInChild
        toOne.owningSide
        !bookAuthor.foreignKeyInChild

        when:
        bookAuthor.foreignKeyInChild = true

        then:
        bookAuthor.owningSide

        cleanup:
        bookAuthor.foreignKeyInChild = false
    }

    void "to-many associations are lazy only when eagerly fetched and marked lazy"() {
        expect:
        !((ToMany) assoc(author, 'books')).lazy
        ((ToMany) assoc(mappingContext.addPersistentEntity(ASEagerAuthor), 'books')).lazy
    }

    void "circular unidirectional one-to-many associations are nullable"() {
        expect:
        ((OneToMany) assoc(node, 'children')).nullable
        ((OneToMany) assoc(author, 'books')).nullable == assoc(author, 'books').mappedForm.nullable
        !((OneToMany) assoc(mappingContext.getPersistentEntity(ASEagerAuthor.name), 'books')).nullable
    }

    void "many-to-many associations record the inverse property name"() {
        given:
        ManyToMany students = (ManyToMany) assoc(course, 'students')

        expect:
        students.inversePropertyName == 'courses'
        students.referencedPropertyName == 'courses'
        students.bidirectional

        when:
        students.inversePropertyName = 'other'

        then:
        students.inversePropertyName == 'other'
        students.referencedPropertyName == 'other'

        cleanup:
        students.inversePropertyName = 'courses'
    }

    @Unroll
    void "basic collection #property has component type #componentType"() {
        given:
        Basic basic = (Basic) assoc(book, property)

        expect:
        basic.componentType == componentType
        basic.enum == isEnum
        basic.inverseSide == null
        basic.associatedEntity == null
        basic.owningSide

        where:
        property   | componentType | isEnum
        'keywords' | String        | false
        'colors'   | ASColor       | true
        'attrs'    | Integer       | false
        'untyped'  | Object        | false
    }

    void "basic collections accept a custom marshaller and ignore owning side changes"() {
        given:
        Basic basic = (Basic) assoc(book, 'keywords')
        CustomTypeMarshaller marshaller = Stub(CustomTypeMarshaller)

        expect:
        basic.customTypeMarshaller == null

        when:
        basic.customTypeMarshaller = marshaller
        basic.owningSide = false

        then:
        basic.customTypeMarshaller.is(marshaller)
        basic.owningSide

        cleanup:
        basic.customTypeMarshaller = null
    }

    void "embedded associations are always owned"() {
        expect:
        assoc(author, 'address').owningSide
        assoc(author, 'addresses').owningSide
        assoc(author, 'address').associatedEntity.javaClass == ASAddress
        assoc(author, 'addresses').associatedEntity.javaClass == ASAddress
    }

    void "the associated entity and referenced property can be changed"() {
        given:
        Association tags = assoc(book, 'tags')
        PersistentEntity original = tags.associatedEntity

        when:
        tags.associatedEntity = author
        tags.referencedPropertyName = 'books'

        then:
        tags.associatedEntity.is(author)
        tags.bidirectional

        cleanup:
        tags.associatedEntity = original
        tags.referencedPropertyName = null
    }
}

@Entity
class ASAuthor {
    Long id
    String name
    Set books
    Set dirtyBooks
    Set owned
    Map notes
    ASProfile profile
    ASAddress address
    List<ASAddress> addresses
    static hasMany = [books: ASBook, dirtyBooks: ASBook, owned: ASOwned, notes: ASNote]
    static hasOne = [profile: ASProfile]
    static embedded = ['address', 'addresses']
    static mappedBy = [books: 'author', dirtyBooks: 'none']
    static mapping = {
        dirtyBooks cascadeValidate: 'dirty'
        notes cascadeValidate: 'none'
        owned cascadeValidate: 'owned'
    }
}

@Entity
class ASEagerAuthor {
    Long id
    Set books
    static hasMany = [books: ASBook]
    static mapping = {
        books fetch: 'eager', lazy: true, nullable: false
    }
}

@Entity
class ASBook implements DirtyCheckable {
    Long id
    String title
    ASAuthor author
    Set tags
    List<String> keywords
    Set colors
    String[] codes
    Map<String, Integer> attrs
    Set untyped
    static belongsTo = [author: ASAuthor]
    static hasMany = [tags: ASTag, colors: ASColor]
}

@Entity
class ASTag {
    Long id
    String name
}

@Entity
class ASNote {
    Long id
    String text
    ASAuthor author
    static belongsTo = [author: ASAuthor]
    static mapping = {
        author cascadeValidate: 'none'
    }
}

@Entity
class ASOwned {
    Long id
    String text
    static belongsTo = ASAuthor
}

@Entity
class ASProfile {
    Long id
    ASAuthor author
    static belongsTo = [author: ASAuthor]
}

class ASAddress {
    String street
}

@Entity
class ASNode {
    Long id
    Set children
    static hasMany = [children: ASNode]
}

@Entity
class ASLeft {
    Long id
    ASRight right
}

@Entity
class ASRight {
    Long id
    ASLeft left
    static belongsTo = [left: ASLeft]
}

@Entity
class ASStudent {
    Long id
    Set courses
    static hasMany = [courses: ASCourse]
}

@Entity
class ASCourse {
    Long id
    Set students
    static hasMany = [students: ASStudent]
    static belongsTo = ASStudent
}

enum ASColor {
    RED, BLUE
}
