package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import spock.lang.Unroll

/**
 * A comprehensive specification to lock down the behavior of the original
 * CascadeBehaviorFetcher before refactoring.
 *
 * This test validates that the fetcher correctly determines the string-based
 * cascade behavior for all standard GORM association types.
 *
 * @author Gemini Code Assist
 */
class CascadeBehaviorFetcherSpec extends HibernateGormDatastoreSpec {

    // A single, comprehensive source of truth for all test scenarios.
    // This list serves as both test data and documentation for GORM's cascade conventions.
    private static final List cascadeTestData = [
            // --- EXPLICIT MAPPINGS ---
            // Test that all valid cascade strings from the mapping block are parsed correctly.
            ["an explicit 'all' cascade"        , AuthorWithAllCascade        , "books", [BookWithoutBelongsTo], "all"],
            ["an explicit 'save-update' cascade", AuthorWithSaveUpdateCascade , "books", [BookWithoutBelongsTo], "save-update"],
            ["an explicit 'merge' cascade"      , AuthorWithMergeCascade      , "books", [BookWithoutBelongsTo], "merge"],
            ["an explicit 'delete' cascade"     , AuthorWithDeleteCascade     , "books", [BookWithoutBelongsTo], "delete"],
            ["an explicit 'lock' cascade"       , AuthorWithLockCascade       , "books", [BookWithoutBelongsTo], "lock"],
            ["an explicit 'replicate' cascade"  , AuthorWithReplicateCascade  , "books", [BookWithoutBelongsTo], "replicate"],
            ["an explicit 'evict' cascade"      , AuthorWithEvictCascade      , "books", [BookWithoutBelongsTo], "evict"],
            ["an explicit 'persist' cascade"    , AuthorWithPersistCascade    , "books", [BookWithoutBelongsTo], "persist"],
            // The original implementation does not sanitize invalid strings. This test captures that behavior.
            ["an invalid cascade string"        , AuthorWithInvalidCascade    , "books", [BookWithoutBelongsTo], "invalid-string"],

            // --- IMPLICIT (DEFAULT) MAPPINGS ---
            // Test GORM's default conventions for different association types.

            // A unidirectional hasMany implies ownership, so the default is ALL (including delete-orphan).
            ["a default unidirectional one-to-many" , AuthorWithUnidirectionalOneToMany, "books"  , [BookWithoutBelongsTo], "save-update"],

            // A bidirectional hasMany is the inverse side, so the default is SAVE_UPDATE to prevent accidental deletes.
            ["a default bidirectional one-to-many"  , AuthorWithBidirectionalOneToMany , "books"  , [BookWithBelongsTo]   , "all"],

            // A unidirectional hasOne implies ownership and a required relationship.
            ["a default unidirectional hasOne"      , AuthorWithHasOne                 , "profile", [Profile]             , "all"],

            // The side without 'belongsTo' in a many-to-many is considered the owning side by convention.
            ["a default many-to-many (owning side)" , Post                             , "tags"   , [Tag]                 , "save-update"],

            // The side with 'belongsTo' is the inverse side and does not cascade by default.
            ["a default many-to-many (inverse side)", Tag                              , "posts"  , [Post]                , "none"],

            // A many-to-one (belongsTo) does not cascade from the "many" side to the "one" side.
            ["a default many-to-one (belongsTo)"    , BookWithBelongsTo                , "author" , [AuthorWithBidirectionalOneToMany], "none"]
    ]

    @Unroll
    void "test getCascadeBehaviour for #description"() {
        given: "A domain model configured for the test scenario"
        def binder = grailsDomainBinder
        def fetcher = new CascadeBehaviorFetcher()

        // Create all dependent entities first, explicitly typing the closure parameter to ensure correctness.
        dependentClasses.each { Class dependentClass ->
            createPersistentEntity(dependentClass, binder)
        }

        // Create the main entity to be tested
        def testEntity = createPersistentEntity(entityClass, binder)
        def testProperty = testEntity.getPropertyByName(propertyName)

        when: "The cascade behavior is fetched for the property"
        String behavior = fetcher.getCascadeBehaviour(testProperty)

        then: "The correct string value is returned based on the mapping"
        behavior == expectedBehavior

        where:
        [description, entityClass, propertyName, dependentClasses, expectedBehavior] << cascadeTestData
    }

}

// --- Test Domain Classes for Various Scenarios ---
// These are defined as top-level classes to be correctly processed by the GORM testing framework.

// --- Explicit Cascade Mapping Classes ---
@Entity class BookWithoutBelongsTo { String title }
@Entity class AuthorWithAllCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'all' } }
@Entity class AuthorWithSaveUpdateCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'save-update' } }
@Entity class AuthorWithMergeCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'merge' } }
@Entity class AuthorWithDeleteCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'delete' } }
@Entity class AuthorWithLockCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'lock' } }
@Entity class AuthorWithReplicateCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'replicate' } }
@Entity class AuthorWithEvictCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'evict' } }
@Entity class AuthorWithPersistCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'persist' } }
@Entity class AuthorWithInvalidCascade { static hasMany = [books: BookWithoutBelongsTo]; static mapping = { books cascade: 'invalid-string' } }

// --- Implicit (Default) Cascade Mapping Classes ---
@Entity class AuthorWithUnidirectionalOneToMany { static hasMany = [books: BookWithoutBelongsTo] }
@Entity class AuthorWithBidirectionalOneToMany { static hasMany = [books: BookWithBelongsTo] }

@Entity class BookWithBelongsTo { static belongsTo = [author: AuthorWithBidirectionalOneToMany] }

@Entity class Profile { String bio }
@Entity class AuthorWithHasOne { static hasOne = [profile: Profile] }

@Entity class Post { static hasMany = [tags: Tag] }
@Entity class Tag { static hasMany = [posts: Post]; static belongsTo = Post }