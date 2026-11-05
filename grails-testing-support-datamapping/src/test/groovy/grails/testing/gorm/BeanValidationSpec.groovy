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
class BeanValidationSpec extends Specification implements DataTest {

    Class[] getDomainClassesToMock() {
        [BeanValidationRecord, BeanValidationParent, BeanValidationChild, BeanValidationGroupedRecord, BeanValidationShelf, BeanValidationShelfBook]
    }

    void 'the annotations and the constraints block are validated, each violation once'() {
        given:
        def record = new BeanValidationRecord(code: 'toolong')

        expect:
        !record.validate()
        record.errors.errorCount == 3
        record.errors.getFieldError('name').code == 'NotNull'
        record.errors.getFieldError('code').code == 'Size'
        record.errors.getFieldError('note').code == 'nullable'

        when:
        record = new BeanValidationRecord(name: 'name', code: 'code', note: 'note')

        then:
        record.save(flush: true)
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

    void 'validation cascades to an annotated association, checking its annotations and its constraints block once'() {
        given:
        def parent = new BeanValidationParent(name: 'parent')
        parent.addToChildren(new BeanValidationChild(label: '', code: null))

        expect:
        !parent.validate()
        parent.errors.errorCount == 2
        parent.errors.getFieldError('children[0].label').code == 'NotBlank'
        parent.errors.getFieldError('children[0].code').code == 'nullable'
        parent.save() == null

        and: 'deepValidate: false leaves the child out'
        parent.validate(deepValidate: false)
    }

    void 'an annotation of another group than the default one leaves the constraints block in force'() {
        given:
        def record = new BeanValidationGroupedRecord()
        def entity = datastore.mappingContext.getPersistentEntity(BeanValidationGroupedRecord.name)
        def constraints = ((ConstrainedEntity) datastore.mappingContext.getEntityValidator(entity)).constrainedProperties

        expect: 'validate() does not run the group, so the block reports the null'
        !record.validate()
        record.errors.errorCount == 1
        record.errors.getFieldError('author').code == 'nullable'
        record.save() == null

        and: 'the constraints say what is enforced'
        !constraints.author.nullable
    }

    void 'an association marked @Valid of a domain class without constraint annotations is validated in the group its @ConvertGroup converts to'() {
        given: 'a shelf whose book violates an annotation of each group'
        def shelf = new BeanValidationShelf(name: 'shelf', book: new BeanValidationShelfBook(title: '', isbn: null))

        expect: 'the book is validated in PublishChecks, not in its default group'
        !shelf.validate()
        shelf.errors.errorCount == 1
        shelf.errors.getFieldError('book.title').code == 'NotBlank'
        shelf.save() == null
    }
}
