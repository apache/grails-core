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

import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.validation.ValidationErrors

class HibernateRuntimeUtilsErrorsSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(RuntimeUtilsErrorsRecord)
    }

    void 'setupErrorsProperty returns fresh ValidationErrors for a target with no prior errors'() {
        given:
        def record = new RuntimeUtilsErrorsRecord(name: 'Alice')

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(record)

        then:
        errors instanceof ValidationErrors
        !errors.hasErrors()
        record.errors.is(errors)
    }

    void 'setupErrorsProperty replaces the previous errors object'() {
        given:
        def record = new RuntimeUtilsErrorsRecord(name: 'Alice')
        def previous = record.errors

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(record)

        then:
        !errors.is(previous)
    }

    void 'setupErrorsProperty copies binding failures with their details'() {
        given:
        def record = new RuntimeUtilsErrorsRecord(name: 'Alice')
        record.errors.addError(new FieldError('record', 'name', 'bad', true, ['typeMismatch'] as String[], null, 'Invalid input'))

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(record)

        then:
        errors.getFieldErrors('name').size() == 1
        FieldError copied = errors.getFieldError('name')
        copied.bindingFailure
        copied.rejectedValue == 'bad'
        copied.codes == ['typeMismatch'] as String[]
        copied.defaultMessage == 'Invalid input'
    }

    void 'setupErrorsProperty does not copy non-binding field errors'() {
        given:
        def record = new RuntimeUtilsErrorsRecord(name: 'Alice')
        record.errors.addError(new FieldError('record', 'name', 'bad', false, null, null, 'validation error'))

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(record)

        then:
        !errors.hasErrors()
    }

    void 'setupErrorsProperty does not copy global errors'() {
        given:
        def record = new RuntimeUtilsErrorsRecord(name: 'Alice')
        record.errors.addError(new ObjectError('record', 'global error'))
        record.errors.reject('stale.global')

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(record)

        then:
        !errors.hasErrors()
        errors.globalErrorCount == 0
    }

    void 'setupErrorsProperty keeps only binding failures from a mix of errors'() {
        given:
        def record = new RuntimeUtilsErrorsRecord(name: 'Alice')
        record.errors.reject('stale.global')
        record.errors.addError(new FieldError('record', 'name', 'bad', false, null, null, 'validation error'))
        record.errors.addError(new FieldError('record', 'other', 'worse', true, null, null, 'binding failure'))

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(record)

        then:
        errors.errorCount == 1
        errors.getFieldError('other').bindingFailure
        errors.getFieldError('name') == null
        errors.globalErrorCount == 0
    }

    void 'setupErrorsProperty handles a target that is not GormValidateable'() {
        given:
        def target = new RuntimeUtilsErrorsPlainTarget()
        target.errors = new ValidationErrors(target)
        target.errors.addError(new FieldError('plain', 'name', 'bad', true, null, null, 'fail'))
        target.errors.reject('stale.global')

        when:
        def errors = HibernateRuntimeUtils.setupErrorsProperty(target)

        then:
        errors.errorCount == 1
        errors.getFieldErrors('name').size() == 1
        target.errors.is(errors)
    }

    void 'validate resets an existing global error and keeps constraint failures'() {
        given:
        def valid = new RuntimeUtilsErrorsRecord(name: 'Alice')
        valid.errors.reject('stale.global')
        def invalid = new RuntimeUtilsErrorsRecord(name: null)
        invalid.errors.reject('stale.global')

        expect:
        valid.validate()
        !valid.hasErrors()
        !invalid.validate()
        invalid.errors.errorCount == 1
        invalid.errors.getFieldError('name') != null
    }
}

class RuntimeUtilsErrorsPlainTarget {
    String name
    Object errors
}

@Entity
class RuntimeUtilsErrorsRecord {
    String name
    String other

    static constraints = {
        name nullable: false
        other nullable: true
    }
}
