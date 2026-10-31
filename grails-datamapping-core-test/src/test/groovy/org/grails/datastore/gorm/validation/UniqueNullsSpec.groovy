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

package org.grails.datastore.gorm.validation

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.context.support.StaticMessageSource

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.config.UniqueNulls
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * How the {@code unique} constraint reads a null in the constrained property and in its group, per reading, on the
 * in-memory datastore, which keeps no unique index and so reads nulls as values when the constraint does not say.
 */
@Transactional
class UniqueNullsSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            NullsKeyWildcard,
            NullsKeySkip,
            NullsKeyValue,
            NullsKeyFollowing,
            NullsFolderWildcard,
            NullsFolderSkip,
            NullsFolderValue,
            NullsSingleProperty,
            NullsSinglePropertyValue
    )

    def setup() {
        ConstraintRegistry constraintRegistry = new DefaultValidatorRegistry(
                datastore.mappingContext,
                datastore.connectionSources.defaultConnectionSource.settings
        )
        constraintRegistry.addConstraintFactory(
                new MappingContextAwareConstraintFactory(UniqueConstraint, new StaticMessageSource(), datastore.mappingContext)
        )
        datastore.mappingContext.setValidatorRegistry(constraintRegistry)
    }

    @Unroll
    void 'with the #reading reading the probes of a nullable key conflict as #conflicts'() {
        given: 'the rows a key may have, saved without validation since some of them conflict under some readings'
        [['en', 'US'], ['en', null], [null, 'US'], [null, null]].each { String l, String c ->
            domain.newInstance(language: l, country: c).save(validate: false, flush: true)
        }

        expect:
        PROBES.collect { String l, String c -> !domain.newInstance(language: l, country: c).validate() } == conflicts

        where:
        reading    | domain            || conflicts
        'wildcard' | NullsKeyWildcard  || [true, true, false, false, false, false]
        'skip'     | NullsKeySkip      || [true, false, false, false, false, false]
        'value'    | NullsKeyValue     || [true, true, false, true, true, false]
        'database' | NullsKeyFollowing || [true, true, false, true, false, false]
    }

    void 'a nullable unique property without a group allows multiple nulls by default, and rejects under value'() {
        given:
        new NullsSingleProperty(email: null).save(validate: false, flush: true)
        new NullsSinglePropertyValue(email: null).save(validate: false, flush: true)

        expect: 'default database reading allows a second null instance'
        new NullsSingleProperty(email: null).validate()

        and: 'explicit value reading rejects a second null instance'
        !new NullsSinglePropertyValue(email: null).validate()
    }

    @Unroll
    void 'with the #reading reading the probes of a folder name grouped by its nullable parent conflict as #conflicts'() {
        given:
        def other = domain.newInstance(name: 'Other').save(failOnError: true, flush: true)
        [['Root', null], ['Root', other]].each { String n, parent ->
            domain.newInstance(name: n, parent: parent).save(validate: false, flush: true)
        }

        expect:
        [['Root', null], ['Root', other], ['Other', null], ['Fresh', null]].collect { String n, parent ->
            !domain.newInstance(name: n, parent: parent).validate()
        } == conflicts

        where:
        reading    | domain              || conflicts
        'wildcard' | NullsFolderWildcard || [true, true, true, false]
        'skip'     | NullsFolderSkip     || [false, true, false, false]
        'value'    | NullsFolderValue    || [true, true, true, false]
    }

    void 'the constraint reports the error on the constrained property'() {
        given:
        new NullsKeyValue(language: 'en').save(validate: false, flush: true)

        when:
        def duplicate = new NullsKeyValue(language: 'en')

        then:
        !duplicate.validate()
        duplicate.errors.getFieldError('language').code == 'unique'
    }

    void 'a datastore without a unique index of its own reads nulls as values, and follows the setting'() {
        expect:
        datastore.databaseUniqueNulls == UniqueNulls.VALUE
        datastore.uniqueNulls == UniqueNulls.VALUE
    }

    @Unroll
    void 'the setting #setting is read as #expected'() {
        given:
        def configured = new SimpleMapDatastore(
                org.grails.datastore.mapping.core.DatastoreUtils.createPropertyResolver(
                        (org.grails.datastore.mapping.config.Settings.SETTING_UNIQUE_NULLS): setting),
                ['dataSource'], NullsKeyFollowing)

        expect:
        configured.uniqueNulls == expected

        cleanup:
        configured.close()

        where:
        setting    || expected
        'wildcard' || UniqueNulls.WILDCARD
        'skip'     || UniqueNulls.SKIP
        'SKIP'     || UniqueNulls.SKIP
        'value'    || UniqueNulls.VALUE
        'database' || UniqueNulls.VALUE
    }

    void 'a setting that names no reading is rejected'() {
        given:
        def configured = new SimpleMapDatastore(
                org.grails.datastore.mapping.core.DatastoreUtils.createPropertyResolver(
                        (org.grails.datastore.mapping.config.Settings.SETTING_UNIQUE_NULLS): 'sometimes'),
                ['dataSource'], NullsKeyFollowing)

        when:
        configured.uniqueNulls

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("'sometimes'")

        cleanup:
        configured.close()
    }

    @Unroll
    void 'the unique parameter #parameter is #outcome'() {
        when:
        def constraint = new UniqueConstraint(NullsKeyValue, 'language', parameter, new StaticMessageSource())

        then:
        constraint.group == group
        constraint.nulls == nulls

        where:
        parameter                                          || group                 | nulls
        true                                               || []                    | null
        'country'                                          || ['country']           | null
        ['region', 'channel']                              || ['region', 'channel'] | null
        [group: 'country']                                 || ['country']           | null
        [group: ['region', 'channel'], nulls: 'skip']      || ['region', 'channel'] | UniqueNulls.SKIP
        [nulls: 'WILDCARD']                                || []                    | UniqueNulls.WILDCARD
        [group: 'country', nulls: UniqueNulls.DATABASE]    || ['country']           | UniqueNulls.DATABASE
        outcome = 'accepted'
    }

    @Unroll
    void 'the unique parameter #parameter is rejected'() {
        when:
        new UniqueConstraint(NullsKeyValue, 'language', parameter, new StaticMessageSource())

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(message)

        where:
        parameter                               || message
        [nulls: 'sometimes']                    || "'sometimes'"
        [group: 'country', null: 'skip']        || "takes a map with a 'group'"
        [group: 7]                              || "takes a map with a 'group'"
        [:]                                     || "takes a map with a 'group'"
    }

    private static final List<List> PROBES = [['en', 'US'], ['en', null], ['en', 'FR'], [null, 'US'], [null, null], ['fr', null]]
}

@Entity
class NullsKeyWildcard {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: [group: 'country', nulls: 'wildcard'])
        country(nullable: true)
    }
}

@Entity
class NullsKeySkip {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: [group: 'country', nulls: 'skip'])
        country(nullable: true)
    }
}

@Entity
class NullsKeyValue {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: [group: 'country', nulls: 'value'])
        country(nullable: true)
    }
}

@Entity
class NullsKeyFollowing {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: 'country')
        country(nullable: true)
    }
}

@Entity
class NullsFolderWildcard {

    String name
    NullsFolderWildcard parent

    static constraints = {
        name(unique: [group: 'parent', nulls: 'wildcard'])
        parent(nullable: true)
    }
}

@Entity
class NullsFolderSkip {

    String name
    NullsFolderSkip parent

    static constraints = {
        name(unique: [group: 'parent', nulls: 'skip'])
        parent(nullable: true)
    }
}

@Entity
class NullsFolderValue {

    String name
    NullsFolderValue parent

    static constraints = {
        name(unique: [group: 'parent', nulls: 'value'])
        parent(nullable: true)
    }
}

@Entity
class NullsSingleProperty {

    String email

    static constraints = {
        email(nullable: true, unique: true)
    }
}

@Entity
class NullsSinglePropertyValue {

    String email

    static constraints = {
        email(nullable: true, unique: [nulls: 'value'])
    }
}
