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
package grails.gorm.tests

import spock.lang.Unroll

import org.springframework.dao.DataAccessException
import org.springframework.validation.FieldError

import grails.gorm.annotation.Entity
import grails.validation.ValidationException

class ValidationErrorResetSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.grailsConfig['grails.gorm.failOnError'] = true
        manager.registerDomainClasses(ValidationErrorResetRecord)
    }

    @Unroll
    void '#operation applies Hibernate error-reset semantics to #kind errors'() {
        when:
        Map outcome = ValidationErrorResetRecord.withTransaction { status ->
            def record = new ValidationErrorResetRecord(name: 'before').save(flush: true, failOnError: true)
            Long id = record.id
            ValidationErrorResetRecord.withSession { it.clear() }
            record = ValidationErrorResetRecord.get(id)
            record.name = 'after'

            switch (kind) {
                case 'global':
                    record.errors.reject('stale.global')
                    break
                case 'field':
                    record.errors.rejectValue('name', 'stale.field')
                    break
                case 'binding':
                    record.errors.addError(new FieldError(record.errors.objectName, 'name', 'invalid',
                            true, ['typeMismatch'] as String[], null, 'Invalid input'))
                    break
                case 'constraint':
                    record.name = null
                    break
                case 'callback':
                    record.name = 'callback-rejected'
                    break
            }

            boolean valid
            try {
                switch (operation) {
                    case 'validate':
                        valid = record.validate()
                        break
                    case 'validateMap':
                        valid = record.validate([deepValidate: false])
                        break
                    case 'validateList':
                        valid = record.validate(['name'])
                        break
                    case 'save':
                        valid = record.save(flush: true, failOnError: true) != null
                        break
                    case 'flush':
                        ValidationErrorResetRecord.withSession { it.flush() }
                        valid = true
                        break
                }
            } catch (ValidationException ignored) {
                valid = false
            } catch (DataAccessException failure) {
                if (!(failure.mostSpecificCause instanceof ValidationException)) {
                    throw failure
                }
                valid = false
            }

            Map result = [valid: valid, codes: record.errors.allErrors*.code]
            if (valid && operation in ['save', 'flush']) {
                ValidationErrorResetRecord.withSession { it.clear() }
                assert ValidationErrorResetRecord.get(id).name == 'after'
            }
            status.setRollbackOnly()
            result
        }

        then:
        outcome.valid == (kind in ['global', 'field'])
        outcome.codes == expectedCodes

        where:
        [kind, operation] << [['global', 'field', 'binding', 'constraint', 'callback'],
                             ['validate', 'validateMap', 'validateList', 'save', 'flush']].combinations()
        expectedCodes = [global: [], field: [], binding: ['typeMismatch'],
                         constraint: ['nullable'], callback: ['callback.rejected']][kind]
    }

    void 'revalidation retains binding failure details while resetting other errors'() {
        given:
        def record = new ValidationErrorResetRecord(name: 'valid')
        record.errors.reject('stale.global')
        record.errors.rejectValue('name', 'stale.field')
        record.errors.addError(new FieldError(record.errors.objectName, 'name', 'bad input',
                true, ['typeMismatch'] as String[], ['name'] as Object[], 'Invalid input'))

        when:
        boolean valid = record.validate()

        then:
        !valid
        record.errors.errorCount == 1
        with(record.errors.getFieldError('name')) {
            bindingFailure
            rejectedValue == 'bad input'
            codes.toList() == ['typeMismatch']
            arguments.toList() == ['name']
            defaultMessage == 'Invalid input'
        }
    }
}

@Entity
class ValidationErrorResetRecord implements Serializable {
    String name

    static constraints = {
        name nullable: false, blank: false
    }

    void beforeValidate() {
        if (name == 'callback-rejected') {
            errors.reject('callback.rejected')
        }
    }
}
