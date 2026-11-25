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
package org.grails.datastore.gorm.validation

import java.lang.ref.WeakReference

import org.springframework.validation.Errors
import org.springframework.validation.Validator

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * A validation API built from a mapping context has no datastore and so no session to keep the errors of a plain bean
 * on, a bean that is not itself a holder of its errors: validating one records the failure on the API and does not
 * throw.
 */
class PlainBeanValidationWithoutDatastoreSpec extends Specification {

    private GormValidationApi<PlainBean> api() {
        MappingContext context = Stub(MappingContext) {
            getPersistentEntity(PlainBean.name) >> Stub(PersistentEntity)
            getEntityValidator(_) >> new NameValidator()
        }
        new GormValidationApi<PlainBean>(PlainBean, context, null)
    }

    @Unroll
    void 'validating a plain bean named "#name" records #outcome without a datastore'() {
        given:
        GormValidationApi<PlainBean> api = api()
        PlainBean bean = new PlainBean(name: name)

        expect:
        api.validate(bean) == valid
        api.hasErrors(bean) == !valid
        api.getErrors(bean).hasErrors() == !valid

        where:
        name | valid | outcome
        'ok' | true  | 'no failure'
        ''   | false | 'the failure'
    }

    void 'the errors of one plain bean are not the errors of another'() {
        given:
        GormValidationApi<PlainBean> api = api()
        PlainBean bad = new PlainBean(name: '')
        PlainBean good = new PlainBean(name: 'ok')

        when:
        api.validate(bad)
        api.validate(good)

        then:
        api.hasErrors(bad)
        !api.hasErrors(good)
    }

    void 'clearing the errors of a plain bean without a datastore removes the failure'() {
        given:
        GormValidationApi<PlainBean> api = api()
        PlainBean bean = new PlainBean(name: '')
        api.validate(bean)

        when:
        api.clearErrors(bean)

        then:
        !api.hasErrors(bean)
    }

    void 'two beans that are equal do not share their errors, as the session does not share them'() {
        given:
        GormValidationApi<PlainBean> api = api()
        PlainBean validated = new ValueBean(name: '')
        PlainBean equal = new ValueBean(name: '')

        when:
        api.validate(validated)

        then:
        validated == equal
        api.hasErrors(validated)
        !api.hasErrors(equal)
    }

    void 'a bean that is changed after it was validated keeps its errors'() {
        given:
        GormValidationApi<PlainBean> api = api()
        PlainBean bean = new ValueBean(name: '')
        api.validate(bean)

        when:
        bean.name = 'changed, and so another hash code'

        then:
        api.hasErrors(bean)
        api.getErrors(bean).hasErrors()
    }

    void 'an error added to the errors of a bean is still there the next time they are read'() {
        given:
        GormValidationApi<PlainBean> api = api()
        PlainBean bean = new PlainBean(name: 'ok')
        api.validate(bean)

        when:
        api.getErrors(bean).rejectValue('name', 'added')

        then:
        api.hasErrors(bean)
    }

    void 'an error recorded on a bean that was never validated is kept, and so is one recorded after the errors were cleared'() {
        given:
        GormValidationApi<PlainBean> api = api()
        PlainBean bean = new PlainBean(name: 'ok')

        when:
        api.getErrors(bean).rejectValue('name', 'first')

        then:
        api.hasErrors(bean)
        api.getErrors(bean).getFieldError('name').code == 'first'

        when:
        api.clearErrors(bean)

        then:
        !api.hasErrors(bean)

        when:
        api.getErrors(bean).rejectValue('name', 'second')

        then:
        api.hasErrors(bean)
        api.getErrors(bean).getFieldError('name').code == 'second'
    }

    void 'a bean that nothing refers to any more is freed with its errors'() {
        given:
        GormValidationApi<PlainBean> api = api()
        WeakReference<PlainBean> reference = validatedBean(api)

        when:
        for (int attempt = 0; attempt < 20 && reference.get() != null; attempt++) {
            System.gc()
            Thread.sleep(50)
        }

        then:
        reference.get() == null
    }

    private static WeakReference<PlainBean> validatedBean(GormValidationApi<PlainBean> api) {
        PlainBean bean = new PlainBean(name: '')
        api.validate(bean)
        new WeakReference<PlainBean>(bean)
    }

    static class ValueBean extends PlainBean {

        @Override
        boolean equals(Object other) {
            other instanceof ValueBean && other.name == name
        }

        @Override
        int hashCode() {
            Objects.hashCode(name)
        }
    }

    static class PlainBean {

        String name
    }

    static class NameValidator implements Validator {

        @Override
        boolean supports(Class<?> clazz) {
            PlainBean.isAssignableFrom(clazz)
        }

        @Override
        void validate(Object target, Errors errors) {
            if (!((PlainBean) target).name) {
                errors.rejectValue('name', 'blank')
            }
        }
    }
}
