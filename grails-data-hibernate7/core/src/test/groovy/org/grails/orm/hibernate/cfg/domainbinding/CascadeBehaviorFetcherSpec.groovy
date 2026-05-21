/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding

import jakarta.persistence.Embeddable
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.hibernate.MappingException
import spock.lang.Shared
import spock.lang.Unroll
import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehaviorFetcher

import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL_DELETE_ORPHAN
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.DELETE
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.EVICT
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.LOCK
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.MERGE
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.NONE
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.PERSIST
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.REPLICATE
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.SAVE_UPDATE

class CascadeBehaviorFetcherSpec extends HibernateGormDatastoreSpec {

    // A single, comprehensive source of truth for all metadata test scenarios.
    private static final List cascadeMetadataTestData = [
            // --- UNIDIRECTIONAL hasMany (should be supported in Hibernate 6+) ---
            ["uni: explicit 'all'", AW_All_Uni, "books", BookUni, ALL.getValue()],
            ["uni: explicit 'persist,merge'", AW_SaveUpdate_Uni, "books", BookUni, SAVE_UPDATE.getValue()],
            ["uni: explicit 'merge'", AW_Merge_Uni, "books", BookUni, MERGE.getValue()],
            ["uni: explicit 'delete'", AW_Delete_Uni, "books", BookUni, DELETE.getValue()],
            ["uni: explicit 'lock'", AW_Lock_Uni, "books", BookUni, LOCK.getValue()],
            ["uni: explicit 'replicate'", AW_Replicate_Uni, "books", BookUni, REPLICATE.getValue()],
            ["uni: explicit 'evict'", AW_Evict_Uni, "books", BookUni, EVICT.getValue()],
            ["uni: explicit 'persist'", AW_Persist_Uni, "books", BookUni, PERSIST.getValue()],
            ["uni: invalid string", AW_Invalid_Uni, "books", BookUni, MappingException],

            // --- OTHER RELATIONSHIP TYPES ---
            ["uni: string", AW_Default_Uni, "books", BookUni, SAVE_UPDATE.getValue()],
            // FIX: This now expects ALL instead of MappingException to support Basic collections
            ["uni: String collection", AW_Default_String, "books", String, ALL.getValue()],
            ["bi: default", AW_Default_Bi, "books", Book_BT_Default, ALL.getValue()],
            ["bi: hasOne (with belongsTo)", AW_HasOne_Bi, "profile", Profile_BT, ALL.getValue()],
            ["uni: hasOne (no belongsTo)", AW_HasOne_Uni, "passport", Passport, ALL.getValue()],
            ["many-to-many (owning side)", Post, "tags", Tag_BT, SAVE_UPDATE.getValue()],
            ["many-to-many (circular subclass)", Dog, "animals", Mammal, SAVE_UPDATE.getValue()],
            ["many-to-many (inverse side)", Tag_BT, "posts", Post, NONE.getValue()],
            ["many-to-many (circular superclass)", Mammal, "dogs", Dog, NONE.getValue()],
            ["many-to-one (belongsTo)", Book_BT_Default, "author", AW_Default_Bi, NONE.getValue()],
            ["many-to-one (unidirectional)", A, "manyToOne", ManyToOne, SAVE_UPDATE.getValue()],
            ["many-to-one (bidirectional but superclass)", Bird, "canary", Canary, NONE.getValue()],

            // --- Additional Hibernate 6+ specific scenarios ---
            ["uni: hasMany with explicit none", AW_None_Uni, "books", BookUni, NONE.getValue()],
            ["bi: hasOne default conservative", AW_HasOne_Default, "profile", Profile_Default, ALL.getValue()],
            ["orphan removal scenario", AW_OrphanRemoval, "books", Book_Orphan, ALL_DELETE_ORPHAN.getValue()],

            // --- Map Association Scenarios ---
            ["map with belongsTo", ImpliedMapParent_All, "settings", ImpliedMapChild_All, ALL.getValue()],
            ["map without belongsTo", ImpliedMapParent_SaveUpdate, "settings", ImpliedMapChild_SaveUpdate, SAVE_UPDATE.getValue()],

            // --- Composite ID Scenario ---
            ["many-to-one in composite id", CompositeIdManyToOne, "parent", CompositeIdParent, ALL.getValue()],

            // --- Embedded Association Scenario ---
            ["embedded association", EOwner, "address", EAddress, ALL.getValue()],

            // --- EmbeddedCollection Scenario ---
            ["embedded collection", EmbeddedCollOwner, "items", EmbeddedItem, ALL.getValue()],

            // --- ManyToOne correctly owned (non-circular) → ALL ---
            ["many-to-one (correctly owned, non-circular)", MtoAllA, "b", MtoAllB, ALL.getValue()]
    ]

    @Shared CascadeBehaviorFetcher fetcher = new CascadeBehaviorFetcher()

    @Unroll
    void "test cascade behavior fetcher for #description"() {
        given: "A persistent property from the test entity"
        createPersistentEntity(childClass, grailsDomainBinder)
        def testProperty = createPersistentEntity(ownerClass, grailsDomainBinder)
                .getPropertyByName(associationName)

        when: "Getting the cascade behavior"
        def result = null
        def thrownException = null
        try {
            result = fetcher.getCascadeBehaviour(testProperty)
        } catch (Exception e) {
            thrownException = e
        }

        then: "The result matches the expectation"
        if (expectation instanceof Class && Exception.isAssignableFrom(expectation)) {
            assert thrownException != null
            assert expectation.isAssignableFrom(thrownException.class)
        } else {
            assert thrownException == null
            assert result == expectation
        }

        where:
        [description, ownerClass, associationName, childClass, expectation] << cascadeMetadataTestData
    }

    def "getCascadeBehaviour throws MappingException when associated entity is null for non-basic association"() {
        given:
        def association = Mock(HibernateManyToOneProperty)
        association.getMappedForm() >> null
        association.getType() >> Object
        association.getAssociatedEntity() >> null

        when:
        fetcher.getCascadeBehaviour(association)

        then:
        thrown(MappingException)
    }

    def "getCascadeBehaviour throws MappingException for unrecognized association type"() {
        given:
        def association = Mock(UnrecognizedTestAssociation)
        association.getMappedForm() >> null
        association.getType() >> Object
        def mockEntity = Mock(PersistentEntity)
        association.getAssociatedEntity() >> mockEntity
        association.isHasOne() >> false

        when:
        fetcher.getCascadeBehaviour(association)

        then:
        thrown(MappingException)
    }
}

// --- Test Domain Classes ---

@Entity class BookUni { String title }

@Entity
class AW_All_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'all' }
}

@Entity
class AW_SaveUpdate_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'persist,merge' }
}

@Entity
class AW_Merge_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'merge' }
}

@Entity
class AW_Delete_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'delete' }
}

@Entity
class AW_Lock_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'lock' }
}

@Entity
class AW_Replicate_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'replicate' }
}

@Entity
class AW_Evict_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'evict' }
}

@Entity
class AW_Persist_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'persist' }
}

@Entity
class AW_Invalid_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'invalid-string' }
}

@Entity class AW_Default_Uni { static hasMany = [books: BookUni] }

// FIX: Replaced class Buffalo with String to test Basic collections properly
@Entity
class AW_Default_String {
    String title
    static hasMany = [books: String]
}

@Entity
class Book_BT_Default {
    String title
    static belongsTo = [author: AW_Default_Bi]
}

@Entity class AW_Default_Bi { static hasMany = [books: Book_BT_Default] }

@Entity class A { ManyToOne manyToOne }
@Entity class ManyToOne { }

@Entity class Passport { String passportNumber }
@Entity class AW_HasOne_Uni { static hasOne = [passport: Passport] }

@Entity
class Profile_BT {
    String bio
    static belongsTo = [author: AW_HasOne_Bi]
}

@Entity class AW_HasOne_Bi { static hasOne = [profile: Profile_BT] }

@Entity
class Post {
    String content
    static hasMany = [tags: Tag_BT]
}

@Entity
class Tag_BT {
    String name
    static hasMany = [posts: Post]
    static belongsTo = Post
}

@Entity class Mammal { String name; static hasMany = [dogs: Dog] }
@Entity class Dog extends Mammal { String foo; static hasMany = [animals: Mammal] }

@Entity class Bird { String title; static belongsTo = [canary: Canary] }
@Entity class Canary { static hasMany = [birds: Bird] }

@Entity
class AW_None_Uni {
    static hasMany = [books: BookUni]
    static mapping = { books cascade: 'none' }
}

@Entity
class Profile_Default {
    String bio
    static belongsTo = [author: AW_HasOne_Default]
}

@Entity class AW_HasOne_Default { static hasOne = [profile: Profile_Default] }

@Entity
class Book_Orphan {
    String title
    static belongsTo = [author: AW_OrphanRemoval]
}

@Entity
class AW_OrphanRemoval {
    static hasMany = [books: Book_Orphan]
    static mapping = { books cascade: 'all-delete-orphan' }
}

@Entity
class ImpliedMapParent_All {
    static hasMany = [settings: ImpliedMapChild_All]
    Map<String, ImpliedMapChild_All> settings
}

@Entity
class ImpliedMapChild_All {
    String value
    static belongsTo = [parent: ImpliedMapParent_All]
}

@Entity
class ImpliedMapParent_SaveUpdate {
    static hasMany = [settings: ImpliedMapChild_SaveUpdate]
    Map<String, ImpliedMapChild_SaveUpdate> settings
}

@Entity class ImpliedMapChild_SaveUpdate { String value }

@Entity
class CompositeIdParent {
    Long id
    String name
    static hasMany = [children: CompositeIdManyToOne]
}

@Entity
class CompositeIdManyToOne implements Serializable {
    String name
    CompositeIdParent parent
    static mapping = { id composite: ['name', 'parent'] }
    static belongsTo = [parent: CompositeIdParent]
}

@Entity
class EOwner {
    EAddress address
    static embedded = ['address']
}

@Embeddable class EAddress { String street }

// --- EmbeddedCollection scenario (L99) ---
@Embeddable class EmbeddedItem { String name }

@Entity
class EmbeddedCollOwner {
    static hasMany = [items: EmbeddedItem]
    static embedded = ['items']
}

// --- ManyToOne correctly owned, non-circular → ALL (L117) ---
// Mutual belongsTo: both entities own each other.
// MtoAllA.b → HibernateManyToOneProperty
// isCorrectlyOwned() = MtoAllB.isOwningEntity(MtoAllA) = (MtoAllA in MtoAllB.owners) = true
// isCircular() = MtoAllB.isAssignableFrom(MtoAllA) = false
// → returns ALL at L117
@Entity
class MtoAllA {
    MtoAllB b
    static belongsTo = [b: MtoAllB]
}

@Entity
class MtoAllB {
    MtoAllA a
    static belongsTo = [a: MtoAllA]
}

// --- Abstract helper for unrecognized association type mock (L124) ---
abstract class UnrecognizedTestAssociation extends org.grails.datastore.mapping.model.types.Association<PropertyConfig>
        implements HibernatePersistentProperty {
    UnrecognizedTestAssociation(
            org.grails.datastore.mapping.model.PersistentEntity owner,
            org.grails.datastore.mapping.model.MappingContext context,
            java.beans.PropertyDescriptor descriptor) {
        super(owner, context, descriptor)
    }
}