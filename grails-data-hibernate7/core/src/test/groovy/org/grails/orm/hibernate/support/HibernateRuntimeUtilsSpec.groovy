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

package org.grails.orm.hibernate.support

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.validation.ValidationErrors
import org.hibernate.Filter
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.context.support.ConversionServiceFactoryBean
import org.springframework.core.convert.ConversionService
import org.springframework.validation.FieldError
import spock.lang.Shared

class HibernateRuntimeUtilsSpec extends HibernateGormDatastoreSpec {

    @Shared ConversionService conversionService

    void setupSpec() {
        manager.addAllDomainClasses([HibernateRuntimeUtilsSpecProfile, HibernateRuntimeUtilsSpecAccount])
        def factory = new ConversionServiceFactoryBean()
        factory.afterPropertiesSet()
        conversionService = factory.object
    }

    // ─── enableDynamicFilterEnablerIfPresent ──────────────────────────────────

    void "enableDynamicFilterEnablerIfPresent does nothing when sessionFactory is null"() {
        given:
        def session = Mock(Session)
        when:
        HibernateRuntimeUtils.enableDynamicFilterEnablerIfPresent(null, session)
        then:
        0 * session._
    }

    void "enableDynamicFilterEnablerIfPresent does nothing when session is null"() {
        given:
        def sf = Mock(SessionFactory)
        when:
        HibernateRuntimeUtils.enableDynamicFilterEnablerIfPresent(sf, null)
        then:
        0 * sf._
    }

    void "enableDynamicFilterEnablerIfPresent does nothing when filter not defined"() {
        given:
        def sf = Mock(SessionFactory) { getDefinedFilterNames() >> ([] as Set) }
        def session = Mock(Session)
        when:
        HibernateRuntimeUtils.enableDynamicFilterEnablerIfPresent(sf, session)
        then:
        0 * session.enableFilter(_)
    }

    void "enableDynamicFilterEnablerIfPresent enables filter when dynamicFilterEnabler is defined"() {
        given:
        def sf = Mock(SessionFactory) { getDefinedFilterNames() >> (['dynamicFilterEnabler'] as Set) }
        def session = Mock(Session)
        when:
        HibernateRuntimeUtils.enableDynamicFilterEnablerIfPresent(sf, session)
        then:
        1 * session.enableFilter('dynamicFilterEnabler') >> Mock(Filter)
    }

    // ─── setupErrorsProperty ──────────────────────────────────────────────────

    void "setupErrorsProperty returns fresh ValidationErrors for GormValidateable with no prior errors"() {
        given:
        def profile = new HibernateRuntimeUtilsSpecProfile(name: "Alice")
        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(profile)
        then:
        errors instanceof ValidationErrors
        !errors.hasErrors()
    }

    void "setupErrorsProperty copies binding failures from existing errors"() {
        given:
        def profile = new HibernateRuntimeUtilsSpecProfile(name: "Alice")
        def existing = new ValidationErrors(profile)
        existing.addError(new FieldError("profile", "name", "bad", true, null, null, "binding failure"))
        profile.errors = existing
        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(profile)
        then:
        errors.getFieldErrors("name").size() == 1
        errors.getFieldErrors("name")[0].bindingFailure
    }

    void "setupErrorsProperty does not copy non-binding field errors"() {
        given:
        def profile = new HibernateRuntimeUtilsSpecProfile(name: "Alice")
        def existing = new ValidationErrors(profile)
        existing.addError(new FieldError("profile", "name", "bad", false, null, null, "validation error"))
        profile.errors = existing
        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(profile)
        then:
        !errors.hasErrors()
    }

    // ─── autoAssociateBidirectionalOneToOnes ──────────────────────────────────

    void "autoAssociateBidirectionalOneToOnes sets inverse side when null"() {
        given:
        def profile = new HibernateRuntimeUtilsSpecProfile(name: "Alice")
        def account = new HibernateRuntimeUtilsSpecAccount(login: "alice")
        profile.account = account
        def entity = mappingContext.getPersistentEntity(HibernateRuntimeUtilsSpecProfile.name)
        when:
        HibernateRuntimeUtils.autoAssociateBidirectionalOneToOnes(entity, profile)
        then:
        account.profile == profile
    }

    void "autoAssociateBidirectionalOneToOnes does not overwrite already-set inverse"() {
        given:
        def profile = new HibernateRuntimeUtilsSpecProfile(name: "Alice")
        def account = new HibernateRuntimeUtilsSpecAccount(login: "alice")
        def otherProfile = new HibernateRuntimeUtilsSpecProfile(name: "Other")
        profile.account = account
        account.profile = otherProfile
        def entity = mappingContext.getPersistentEntity(HibernateRuntimeUtilsSpecProfile.name)
        when:
        HibernateRuntimeUtils.autoAssociateBidirectionalOneToOnes(entity, profile)
        then:
        account.profile == otherProfile
    }

    void "autoAssociateBidirectionalOneToOnes does nothing when association value is null"() {
        given:
        def profile = new HibernateRuntimeUtilsSpecProfile(name: "Alice")
        // profile.account is null
        def entity = mappingContext.getPersistentEntity(HibernateRuntimeUtilsSpecProfile.name)
        when:
        HibernateRuntimeUtils.autoAssociateBidirectionalOneToOnes(entity, profile)
        then:
        noExceptionThrown()
    }

    // ─── convertValueToType ───────────────────────────────────────────────────

    void "convertValueToType returns null when value is null"() {
        expect:
        HibernateRuntimeUtils.convertValueToType(null, Long, conversionService) == null
    }

    void "convertValueToType returns value unchanged when targetType is null"() {
        expect:
        HibernateRuntimeUtils.convertValueToType("hello", null, conversionService) == "hello"
    }

    void "convertValueToType returns value unchanged when already the correct type"() {
        expect:
        HibernateRuntimeUtils.convertValueToType(42L, Long, conversionService) == 42L
    }

    void "convertValueToType converts CharSequence to String when target is String"() {
        given:
        def sb = new StringBuilder("hello")
        when:
        def result = HibernateRuntimeUtils.convertValueToType(sb, String, conversionService)
        then:
        result == "hello"
        result instanceof String
    }

    void "convertValueToType converts Number to Long"() {
        expect:
        HibernateRuntimeUtils.convertValueToType(42, Long, conversionService) == 42L
    }

    void "convertValueToType converts Number to Integer"() {
        expect:
        HibernateRuntimeUtils.convertValueToType(42L, Integer, conversionService) == 42
    }

    void "convertValueToType converts String to Long"() {
        expect:
        HibernateRuntimeUtils.convertValueToType("123", Long, conversionService) == 123L
    }

    void "convertValueToType converts String to Integer"() {
        expect:
        HibernateRuntimeUtils.convertValueToType("99", Integer, conversionService) == 99
    }

    void "convertValueToType uses ConversionService for other types"() {
        expect:
        HibernateRuntimeUtils.convertValueToType("42.5", Double, conversionService) == 42.5d
    }

    void "convertValueToType returns original value when conversion fails"() {
        given:
        def badValue = "not-a-number"
        when:
        def result = HibernateRuntimeUtils.convertValueToType(badValue, Integer, conversionService)
        then:
        result == badValue
    }

    // ─── Additional edge cases for coverage ───────────────────────────────────

    void "setupErrorsProperty handles non-GormValidateable target"() {
        given:
        def target = new NonGormValidateable()
        target.errors = new ValidationErrors(target)
        target.errors.addError(new FieldError("nonGorm", "name", "bad", true, null, null, "fail"))

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(target)

        then:
        errors.getFieldErrors("name").size() == 1
    }

    void "setupErrorsProperty copies ObjectError"() {
        given:
        def profile = new HibernateRuntimeUtilsSpecProfile(name: "Alice")
        def existing = new ValidationErrors(profile)
        existing.addError(new org.springframework.validation.ObjectError("profile", "global error"))
        profile.errors = existing

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(profile)

        then:
        errors.getGlobalErrors().size() == 1
    }

    void "convertValueToType converts String to other Number types"() {
        expect:
        HibernateRuntimeUtils.convertValueToType("123.45", BigDecimal, conversionService) == 123.45g
        HibernateRuntimeUtils.convertValueToType("123.45", Float, conversionService) == 123.45f
    }
}

class NonGormValidateable {
    org.springframework.validation.Errors errors
}

@Entity
class HibernateRuntimeUtilsSpecProfile {
    String name
    HibernateRuntimeUtilsSpecAccount account

    static hasOne = [account: HibernateRuntimeUtilsSpecAccount]

    static constraints = {
        account nullable: true
    }
}

@Entity
class HibernateRuntimeUtilsSpecAccount {
    String login
    HibernateRuntimeUtilsSpecProfile profile

    static belongsTo = [profile: HibernateRuntimeUtilsSpecProfile]

    static constraints = {
        profile nullable: true
    }
}
