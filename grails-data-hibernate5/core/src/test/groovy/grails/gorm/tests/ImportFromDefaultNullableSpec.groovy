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

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings

class ImportFromDefaultNullableSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            [
                    'dataSource.url': 'jdbc:h2:mem:importFromDefaultNullable;LOCK_TIMEOUT=10000',
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'grails.gorm.default.nullable': false
            ],
            ImportFromNullableSource, ImportFromNullableTarget
    )

    @Rollback
    void 'importFrom applies the configured nullable default to the properties the source leaves unconstrained'() {
        given:
        var target = new ImportFromNullableTarget()

        expect: 'the unconstrained property is required on the source'
        !new ImportFromNullableSource().validate()

        and: 'it is required on the target too'
        !target.validate()
        target.errors.getFieldError('name')?.code == 'nullable'

        and: 'the explicit nullable constraint is imported as it is'
        !target.errors.hasFieldErrors('description')

        when:
        target.name = 'target'

        then:
        target.save(flush: true)
        ImportFromNullableTarget.count() == 1
    }
}

@Entity
class ImportFromNullableSource {

    String name
    String description

    static constraints = {
        description(nullable: true)
    }
}

@Entity
class ImportFromNullableTarget {

    String name
    String description

    static constraints = {
        importFrom(ImportFromNullableSource)
    }
}
