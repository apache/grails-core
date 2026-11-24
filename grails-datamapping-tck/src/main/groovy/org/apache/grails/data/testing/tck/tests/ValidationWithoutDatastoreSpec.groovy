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

package org.apache.grails.data.testing.tck.tests

import grails.gorm.annotation.Entity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.GormValidationApi
import spock.lang.Requires
import spock.lang.Unroll

/**
 * A validation API that is built from a mapping context and has no datastore, as the check of a class that is validated
 * without being stored is, runs the constraints of the instance through every entry point it has. Naming the connection
 * of a validation, which is what the {@code unique} constraint needs, asks the API's datastore, and an API without one
 * names none, so each entry point has to run and not throw for the datastore it was not given. The in-memory datastore does
 * not register its validators in the mapping context, so an API built from that context finds none there and validates
 * nothing: the spec runs for Hibernate 5 and MongoDB, which do.
 */
@Requires({ Boolean.getBoolean('hibernate5.gorm.suite') || Boolean.getBoolean('mongodb.gorm.suite') })
class ValidationWithoutDatastoreSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([VwdItem])
    }

    private GormValidationApi<VwdItem> api() {
        def datastore = manager.session.datastore
        new GormValidationApi<VwdItem>(VwdItem, datastore.mappingContext, datastore.applicationEventPublisher)
    }

    @Unroll
    void 'validating #state through #entry without a datastore runs the constraints'() {
        given:
        GormValidationApi<VwdItem> api = api()
        VwdItem item = new VwdItem(name: name, rank: rank)

        expect:
        call(api, item) == valid

        where:
        [state, entry, call, name, rank, valid] << [
            ['a valid instance', 'validate', VALIDATE, 'ok', 1, true],
            ['an instance whose blank name is refused', 'validate', VALIDATE, '', 1, false],
            ['an instance whose rank is too high', 'validate', VALIDATE, 'ok', 99, false],
            ['a valid instance', 'validate with fields', VALIDATE_RANK, 'ok', 1, true],
            ['an instance that is invalid in a field that is not asked for', 'validate with fields', VALIDATE_NAME, 'ok', 99, true],
            ['an instance that is invalid in a field that is asked for', 'validate with fields', VALIDATE_RANK, 'ok', 99, false],
            ['a valid instance', 'validate with arguments', VALIDATE_ARGS, 'ok', 1, true],
            ['an invalid instance', 'validate with arguments', VALIDATE_ARGS, '', 1, false],
            ['a valid instance', 'hasErrors after validate', NO_ERRORS, 'ok', 1, true],
            ['an invalid instance', 'hasErrors after validate', NO_ERRORS, '', 1, false]
        ]
    }

    private static final Closure<Boolean> VALIDATE = { GormValidationApi<VwdItem> api, VwdItem item -> api.validate(item) }
    private static final Closure<Boolean> VALIDATE_RANK = { GormValidationApi<VwdItem> api, VwdItem item -> api.validate(item, ['rank']) }
    private static final Closure<Boolean> VALIDATE_NAME = { GormValidationApi<VwdItem> api, VwdItem item -> api.validate(item, ['name']) }
    private static final Closure<Boolean> VALIDATE_ARGS = { GormValidationApi<VwdItem> api, VwdItem item -> api.validate(item, [deepValidate: false]) }
    private static final Closure<Boolean> NO_ERRORS = { GormValidationApi<VwdItem> api, VwdItem item ->
        api.validate(item)
        !api.hasErrors(item)
    }
}

@Entity
class VwdItem {

    String name
    Integer rank

    static constraints = {
        name blank: false
        rank max: 10
    }
}
