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

package grails.testing.gorm

import spock.lang.Specification

import grails.gorm.validation.ConstrainedEntity

/**
 * The unit test datastore validates Bean Validation annotations together with the constraints block, like GORM does
 * at runtime.
 */
class BeanValidationSpec extends Specification implements DomainUnitTest<BeanValidationRecord> {

    void 'the annotations and the constraints block are validated, each violation once'() {
        given:
        domain.code = 'toolong'

        expect:
        !domain.validate()
        domain.errors.errorCount == 3
        domain.errors.getFieldError('name').code == 'NotNull'
        domain.errors.getFieldError('code').code == 'Size'
        domain.errors.getFieldError('note').code == 'nullable'

        when:
        domain.name = 'name'
        domain.code = 'code'
        domain.note = 'note'

        then:
        domain.save(flush: true)
        BeanValidationRecord.count() == 1
    }

    void 'the constraints of the mocked domain describe the annotations'() {
        given:
        def entity = datastore.mappingContext.getPersistentEntity(BeanValidationRecord.name)
        def validator = datastore.mappingContext.getEntityValidator(entity)

        expect:
        validator instanceof ConstrainedEntity
        def constraints = ((ConstrainedEntity) validator).constrainedProperties
        !constraints.name.nullable
        constraints.code.maxSize == 5
        !constraints.note.nullable
    }
}
