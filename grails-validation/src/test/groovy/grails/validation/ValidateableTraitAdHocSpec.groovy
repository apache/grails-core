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
package grails.validation

import grails.util.ClosureToMapPopulator
import grails.util.Holders
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

class ValidateableTraitAdHocSpec extends Specification {

    void 'Test that pre-declared constraints can be used'() {
        given:
        def person = new PersonAdHocValidateable(name: nameValue, age: ageValue)

        when:
        boolean actualValid = person.validate()

        then:
        actualValid == expectedValid
        person.errors['name']?.code == nameErrorCode
        person.errors['age']?.code == ageErrorCode

        where:
        nameValue | ageValue | expectedValid | nameErrorCode | ageErrorCode
        'Kirk'    | 32       | true          | null          | null
        ''        | 32       | false         | 'blank'       | null
        'Kirk'    | -1       | false         | null          | 'min.notmet'
        ''        | -1       | false         | 'blank'       | 'min.notmet'
    }

    void 'Test that ad-hoc constraints can be used'() {
        given:
        def person = new PersonAdHocValidateable(name: nameValue, age: ageValue)

        when:
        boolean actualValid = person.validate {
            name maxSize: 10
            age max: 18
        }

        then:
        actualValid == expectedValid
        person.errors['name']?.code == nameErrorCode
        person.errors['age']?.code == ageErrorCode

        where:
        nameValue   | ageValue | expectedValid | nameErrorCode      | ageErrorCode
        'Kirk'      | 15       | true          | null               | null
        'Kirk' * 10 | 15       | false         | 'maxSize.exceeded' | null
        'Kirk'      | 32       | false         | null               | 'max.exceeded'
        'Kirk' * 10 | 32       | false         | 'maxSize.exceeded' | 'max.exceeded'
    }

    void 'Test that "fieldsToValidate" can be used with ad-hoc constraints'() {
        given:
        def person = new PersonAdHocValidateable(name: nameValue, age: ageValue)

        when:
        boolean actualValid = person.validate(['age']) {
            name maxSize: 10
            age max: 18
        }

        then:
        actualValid == expectedValid
        person.errors['name']?.code == nameErrorCode
        person.errors['age']?.code == ageErrorCode

        where:
        nameValue   | ageValue | expectedValid | nameErrorCode | ageErrorCode
        'Kirk'      | 15       | true          | null          | null
        'Kirk' * 10 | 15       | true          | null          | null
        'Kirk'      | 32       | false         | null          | 'max.exceeded'
        'Kirk' * 10 | 32       | false         | null          | 'max.exceeded'
    }

    void 'Test that both pre-declared and ad-hoc constraints can be used together'() {
        given:
        def person = new PersonAdHocValidateable(name: nameValue, age: ageValue)

        when:
        boolean actualValid = person.validate {
            name maxSize: 10
        }

        then:
        actualValid == expectedValid
        person.errors['name']?.code == nameErrorCode
        person.errors['age']?.code == ageErrorCode

        where:
        nameValue   | ageValue | expectedValid | nameErrorCode      | ageErrorCode
        'Kirk'      | 32       | true          | null               | null
        'Kirk' * 10 | 32       | false         | 'maxSize.exceeded' | null
        'Kirk'      | -1       | false         | null               | 'min.notmet'
        'Kirk' * 10 | -1       | false         | 'maxSize.exceeded' | 'min.notmet'
    }

    void 'Test that another pre-declared closure can be used as ad-hoc constraints'() {
        given:
        def person = new PersonAdHocValidateable(name: 'Kirk' * 10, age: 32)

        when:
        boolean valid = person.validate(PersonAdHocValidateable.adHocConstraints)

        then:
        !valid
        person.errors['name']?.code == 'maxSize.exceeded'
        person.errors['age']?.code == 'max.exceeded'
        person.errors.errorCount == 2
    }

    void 'Test that ad-hoc constraints overwrites if the same kind of constraint is given as ad-hoc'() {
        given:
        def person = new PersonAdHocValidateable(name: '', age: 32)

        expect:
        !person.validate()

        and:
        person.validate {
            name blank: true
        }

        and: 'cached pre-declared constraints should not be affected'
        !person.validate()
    }

    void 'Test that empty closure as ad-hoc constraints is equivalent with only pre-declared constraints'() {
        given:
        def person = new PersonAdHocValidateable(name: nameValue, age: ageValue)

        when:
        boolean actualValid = person.validate {}

        then:
        actualValid == expectedValid
        person.errors['name']?.code == nameErrorCode
        person.errors['age']?.code == ageErrorCode

        where:
        nameValue | ageValue | expectedValid | nameErrorCode | ageErrorCode
        'Kirk'    | 32       | true          | null          | null
        ''        | 32       | false         | 'blank'       | null
        'Kirk'    | -1       | false         | null          | 'min.notmet'
        ''        | -1       | false         | 'blank'       | 'min.notmet'
    }

    void 'Test that "beforeValidator" is called with ad-hoc constraints'() {
        given:
        def person = new PersonAdHocValidateable(name: 'Kirk', age: 32)

        expect:
        person.validate {
            name maxSize: 10
            age max: 60
        }

        and:
        person.name == 'KIRK'
    }

    void 'Test that pre-declared is ignored when "inherit:false" is specified'() {
        given:
        def person = new PersonAdHocValidateable(name: nameValue, age: ageValue)

        when:
        boolean actualValid = person.validate(params, adHocConstraints)

        then:
        actualValid == expectedValid
        person.errors['name']?.code == nameErrorCode
        person.errors['age']?.code == ageErrorCode

        where:
        params           | adHocConstraints       | nameValue   | ageValue | expectedValid | nameErrorCode      | ageErrorCode
        [inherit: true]  | { name maxSize: 10 }   | 'Kirk'      | 32       | true          | null               | null
        [inherit: true]  | { name maxSize: 10 }   | 'Kirk' * 10 | 32       | false         | 'maxSize.exceeded' | null
        [inherit: true]  | { name maxSize: 10 }   | 'Kirk'      | -1       | false         | null               | 'min.notmet'
        [inherit: true]  | { name maxSize: 10 }   | 'Kirk' * 10 | -1       | false         | 'maxSize.exceeded' | 'min.notmet'
        [inherit: true]  | { age nullable: true } | null        | null     | false         | 'nullable'         | null
        [inherit: true]  | {}                     | null        | null     | false         | 'nullable'         | 'nullable'
        [inherit: false] | { name maxSize: 10 }   | 'Kirk'      | 32       | true          | null               | null
        [inherit: false] | { name maxSize: 10 }   | 'Kirk' * 10 | 32       | false         | 'maxSize.exceeded' | null
        [inherit: false] | { name maxSize: 10 }   | 'Kirk'      | -1       | true          | null               | null
        [inherit: false] | { name maxSize: 10 }   | 'Kirk' * 10 | -1       | false         | 'maxSize.exceeded' | null
        [inherit: false] | { age nullable: true } | null        | null     | false         | 'nullable'         | null       // default 'nullable:false' is applied to ad-hoc constraints
        [inherit: false] | {}                     | null        | null     | false         | 'nullable'         | 'nullable' // default 'nullable:false' is applied to ad-hoc constraints
    }

    void 'Test that errors are not cleared for each call when "clearErrors:false" is specified'() {
        given:
        def person = new PersonAdHocValidateable(name: '', age: -1)

        when:
        boolean actualValid = person.validate()

        then:
        !actualValid
        person.errors['name']?.code == 'blank'
        person.errors['age']?.code == 'min.notmet'

        when:
        actualValid = person.validate(params, adHocConstraints)

        then:
        actualValid == expectedValid
        person.errors['name']?.code == nameErrorCode
        person.errors['age']?.code == ageErrorCode

        where:
        params               | adHocConstraints                  | expectedValid | nameErrorCode | ageErrorCode
        [clearErrors: true]  | { age min: -9 }                   | false         | 'blank'       | null
        [clearErrors: true]  | { name blank: true }              | false         | null          | 'min.notmet'
        [clearErrors: true]  | { name blank: true; age min: -9 } | true          | null          | null
        [clearErrors: false] | { age max: -9 }                   | false         | 'blank'       | 'min.notmet'
        [clearErrors: false] | { name blank: true }              | false         | 'blank'       | 'min.notmet'
        [clearErrors: false] | { name blank: true; age min: -9 } | false         | 'blank'       | 'min.notmet'
    }

    void "Test that default and shared constraints can be applied from configuration"() {
        given:
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        applicationContext.refresh()
        def defaultConstraints = new ClosureToMapPopulator().populate {
            '*' matches: /DEFAULT_CONSTRAINT/
            myShared max: 20
        }
        applicationContext.beanFactory.registerSingleton(
                "constraintEvaluator",
                new DefaultConstraintEvaluator(defaultConstraints)
        )

        def strategy = Mock(GrailsApplicationDiscoveryStrategy)
        strategy.findApplicationContext() >> applicationContext
        Holders.addApplicationDiscoveryStrategy(strategy)

        and:
        def person = new PersonAdHocSharedConstraintsValidateable(name: 'FOO', age: 99)

        expect:
        !person.validate {}
        person.hasErrors()
        person.errors.errorCount == 2
        person.errors['name']?.code == 'matches.invalid'
        person.errors['age']?.code == 'max.exceeded'

        and:
        !person.validate(inherit: false) {
            name()
            age shared: 'myShared'
        }
        person.hasErrors()
        person.errors.errorCount == 2
        person.errors['name']?.code == 'matches.invalid'
        person.errors['age']?.code == 'max.exceeded'

        cleanup:
        Holders.clear()
    }

    void 'Test that multiple ad-hoc constraints can be used'() {
        given:
        def person = new PersonAdHocValidateable(name: 'Kirk' * 10, age: 32)

        when: 'a single ad-hoc constraints'
        def valid = person.validate(inherit: false) {
            name maxSize: 10
            age max: 18
        }

        then:
        !valid
        person.errors['name']?.code == 'maxSize.exceeded'
        person.errors['age']?.code == 'max.exceeded'
        person.errors.errorCount == 2

        when: 'double ad-hoc constraints'
        person.validate(inherit: false) {
            name maxSize: 10
            age max: 18
        } {
            name maxSize: 100
        }

        then:
        !valid
        person.errors['age']?.code == 'max.exceeded'
        person.errors.errorCount == 1
    }

    void 'Test for a variety of overload methods'() {
        given:
        def person = new PersonAdHocValidateable(name: 'Kirk', age: 32)

        expect:
        person.validate()
        person.validate(clearErrors: true)
        !person.validate { age max: 18 }
        !person.validate { name maxSize: 99 } { age max: 18 }
        !person.validate(clearErrors: true, inherit: false) { age max: 18 }
        !person.validate(clearErrors: true, inherit: false) { name maxSize: 99 } { age max: 18 }
        person.validate(['age'])
        person.validate(['age'], [clearErrors: true])
        !person.validate(['age']) { age max: 18 }
        !person.validate(['age']) { name maxSize: 99 } { age max: 18 }
        !person.validate(['age'], [clearErrors: true, inherit: false]) { age max: 18 }
        !person.validate(['age'], [clearErrors: true, inherit: false]) { name maxSize: 99 } { age max: 18 }
    }
}

class PersonAdHocValidateable implements Validateable {
    String name
    Integer age

    static constraints = {
        name blank: false
        age min: 0
    }

    static adHocConstraints = {
        name maxSize: 10
        age max: 18
    }

    def beforeValidate() {
        name = name?.toUpperCase()
    }
}

class PersonAdHocSharedConstraintsValidateable implements Validateable {
    String name
    Integer age

    static constraints = {
        name blank: false
        age min: 0, shared: "myShared"
    }
}
