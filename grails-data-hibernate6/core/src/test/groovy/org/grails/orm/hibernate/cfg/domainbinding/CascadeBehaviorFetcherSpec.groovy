package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import spock.lang.Shared
import spock.lang.Unroll
import org.hibernate.MappingException

class CascadeBehaviorFetcherSpec extends HibernateGormDatastoreSpec {



    // A single, comprehensive source of truth for all metadata test scenarios.
    private static final List cascadeMetadataTestData = [
            // --- UNIDIRECTIONAL hasMany (should be supported in Hibernate 6+) ---
            ["uni: explicit 'all'"          , AW_All_Uni        , "books", Book, "all"],
            ["uni: explicit 'save-update'"  , AW_SaveUpdate_Uni , "books", Book, "save-update"],
            ["uni: explicit 'merge'"        , AW_Merge_Uni      , "books", Book, "merge"],
            ["uni: explicit 'delete'"       , AW_Delete_Uni     , "books", Book, "delete"],
            ["uni: explicit 'lock'"         , AW_Lock_Uni       , "books", Book, "lock"],
            ["uni: explicit 'replicate'"    , AW_Replicate_Uni  , "books", Book, "replicate"],
            ["uni: explicit 'evict'"        , AW_Evict_Uni      , "books", Book, "evict"],
            ["uni: explicit 'persist'"      , AW_Persist_Uni    , "books", Book, "persist"],
            ["uni: invalid string"          , AW_Invalid_Uni    , "books", Book, MappingException],
            ["uni: default"                 , AW_Default_Uni    , "books", Book, "none"], // Hibernate 6 default

            // --- BIDIRECTIONAL hasMany (with belongsTo) ---
            ["bi: explicit 'all'"           , AW_All_Bi         , "books", Book_BT_All       , "all"],
            ["bi: explicit 'save-update'"   , AW_SaveUpdate_Bi  , "books", Book_BT_SaveUpdate, "save-update"],
            ["bi: explicit 'merge'"         , AW_Merge_Bi       , "books", Book_BT_Merge     , "merge"],
            ["bi: explicit 'delete'"        , AW_Delete_Bi      , "books", Book_BT_Delete    , "delete"],
            ["bi: explicit 'lock'"          , AW_Lock_Bi        , "books", Book_BT_Lock      , "lock"],
            ["bi: explicit 'replicate'"     , AW_Replicate_Bi   , "books", Book_BT_Replicate , "replicate"],
            ["bi: explicit 'evict'"         , AW_Evict_Bi       , "books", Book_BT_Evict     , "evict"],
            ["bi: explicit 'persist'"       , AW_Persist_Bi     , "books", Book_BT_Persist   , "persist"],
            ["bi: invalid string"           , AW_Invalid_Bi     , "books", Book_BT_Invalid   , MappingException],
            ["bi: default"                  , AW_Default_Bi     , "books", Book_BT_Default   , "save-update"], // More conservative default

            // --- OTHER RELATIONSHIP TYPES ---
            ["bi: hasOne (with belongsTo)"  , AW_HasOne_Bi      , "profile" , Profile_BT      , "save-update"], // Conservative default
            ["uni: hasOne (no belongsTo)"   , AW_HasOne_Uni     , "passport", Passport        , MappingException], // Should be supported
            ["many-to-many (owning side)"   , Post              , "tags"    , Tag_BT          , "save-update"],
            ["many-to-many (inverse side)"  , Tag_BT            , "posts"   , Post            , "none"],
            ["many-to-one (belongsTo)"      , Book_BT_Default   , "author"  , AW_Default_Bi   , "none"],

//             --- Additional Hibernate 6+ specific scenarios ---
            ["uni: hasMany with explicit none", AW_None_Uni     , "books", Book             , MappingException],
            ["bi: hasOne default conservative", AW_HasOne_Default, "profile", Profile_Default , "save-update"],
            ["orphan removal scenario"        , AW_OrphanRemoval , "books", Book_Orphan      , "all"]

    ]


    @Shared
    CascadeBehaviorFetcher fetcher = new CascadeBehaviorFetcher()



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
            // Expecting an exception
            assert thrownException != null : "Expected ${expectation.simpleName} to be thrown but no exception was thrown for ${description}"
            assert expectation.isAssignableFrom(thrownException.class) : "Expected ${expectation.simpleName} but got ${thrownException.class.simpleName} for ${description}"
        } else {
            // Expecting a string result
            assert thrownException == null : "Unexpected exception thrown: ${thrownException?.message} for ${description}"
            assert result == expectation : "Expected cascade behavior '${expectation}' but got '${result}' for ${description}"
        }

        where:
        [description, ownerClass, associationName, childClass, expectation] << cascadeMetadataTestData
    }




}

// --- Test Domain Classes for Various Scenarios ---
// Naming Convention:
//   AW_ = AuthorWith...
//   _Uni = Unidirectional hasMany (child has no belongsTo)
//   _Bi = Bidirectional hasMany (child has a belongsTo)
//   _BT = Suffix for a child class that has a `belongsTo`

// --- One-to-Many: Unidirectional ---
@Entity class Book { String title }
@Entity class AW_Default_Uni { static hasMany = [books: Book] }
@Entity class AW_All_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'all' } }
@Entity class AW_SaveUpdate_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'save-update' } }
@Entity class AW_Merge_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'merge' } }
@Entity class AW_Delete_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'delete' } }
@Entity class AW_Lock_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'lock' } }
@Entity class AW_Replicate_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'replicate' } }
@Entity class AW_Evict_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'evict' } }
@Entity class AW_Persist_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'persist' } }
@Entity class AW_Invalid_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'invalid-string' } }

// --- One-to-Many: Bidirectional ---
@Entity class Book_BT_Default { String title; static belongsTo = [author: AW_Default_Bi] }
@Entity class AW_Default_Bi { static hasMany = [books: Book_BT_Default] }

@Entity class Book_BT_All { String title; static belongsTo = [author: AW_All_Bi] }
@Entity class AW_All_Bi { static hasMany = [books: Book_BT_All]; static mapping = { books cascade: 'all' } }

@Entity class Book_BT_SaveUpdate { String title; static belongsTo = [author: AW_SaveUpdate_Bi] }
@Entity class AW_SaveUpdate_Bi { static hasMany = [books: Book_BT_SaveUpdate]; static mapping = { books cascade: 'save-update' } }

@Entity class Book_BT_Merge { String title; static belongsTo = [author: AW_Merge_Bi] }
@Entity class AW_Merge_Bi { static hasMany = [books: Book_BT_Merge]; static mapping = { books cascade: 'merge' } }

@Entity class Book_BT_Delete { String title; static belongsTo = [author: AW_Delete_Bi] }
@Entity class AW_Delete_Bi { static hasMany = [books: Book_BT_Delete]; static mapping = { books cascade: 'delete' } }

@Entity class Book_BT_Lock { String title; static belongsTo = [author: AW_Lock_Bi] }
@Entity class AW_Lock_Bi { static hasMany = [books: Book_BT_Lock]; static mapping = { books cascade: 'lock' } }

@Entity class Book_BT_Replicate { String title; static belongsTo = [author: AW_Replicate_Bi] }
@Entity class AW_Replicate_Bi { static hasMany = [books: Book_BT_Replicate]; static mapping = { books cascade: 'replicate' } }

@Entity class Book_BT_Evict { String title; static belongsTo = [author: AW_Evict_Bi] }
@Entity class AW_Evict_Bi { static hasMany = [books: Book_BT_Evict]; static mapping = { books cascade: 'evict' } }

@Entity class Book_BT_Persist { String title; static belongsTo = [author: AW_Persist_Bi] }
@Entity class AW_Persist_Bi { static hasMany = [books: Book_BT_Persist]; static mapping = { books cascade: 'persist' } }

 @Entity class Book_BT_Invalid { String title; static belongsTo = [author: AW_Invalid_Bi] }
 @Entity class AW_Invalid_Bi { static hasMany = [books: Book_BT_Invalid]; static mapping = { books cascade: 'invalid-string' } }

// --- One-to-One ---
@Entity class Passport { String passportNumber }
@Entity class AW_HasOne_Uni { static hasOne = [passport: Passport] } // Unidirectional

@Entity class Profile_BT { String bio; static belongsTo = [author: AW_HasOne_Bi] }
@Entity class AW_HasOne_Bi { static hasOne = [profile: Profile_BT] } // Bidirectional

// --- Many-to-Many ---
@Entity class Post { String content; static hasMany = [tags: Tag_BT] }
@Entity class Tag_BT { String name; static hasMany = [posts: Post]; static belongsTo = Post }
@Entity class AW_None_Uni { static hasMany = [books: Book]; static mapping = { books cascade: 'none' } }
@Entity class Profile_Default { String bio; static belongsTo = [author: AW_HasOne_Default] }
@Entity class AW_HasOne_Default { static hasOne = [profile: Profile_Default] }
@Entity class Book_Orphan { String title; static belongsTo = [author: AW_OrphanRemoval] }
@Entity class AW_OrphanRemoval { static hasMany = [books: Book_Orphan]; static mapping = { books cascade: 'all-delete-orphan' } }
