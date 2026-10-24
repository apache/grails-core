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

import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.UniqueNullKeyDatabase
import org.apache.grails.data.testing.tck.domains.UniqueNullKeySkip
import org.apache.grails.data.testing.tck.domains.UniqueNullKeyValue
import org.apache.grails.data.testing.tck.domains.UniqueNullKeyWildcard
import org.grails.datastore.mapping.config.UniqueNulls

/**
 * How the {@code unique} constraint reads a {@code null} in a composite key, per reading: the same rows and the same
 * probes against a language whose constraint asks for each reading. A reading that follows the database is checked
 * against the reading the datastore says its database has.
 *
 * The simple in-memory datastore of the TCK does not register the {@code unique} constraint, so this specification
 * does not run there; {@code UniqueConstraintSpec} of {@code grails-datamapping-core-test} covers it.
 */
@IgnoreIf({ Boolean.getBoolean('simple.gorm.suite') })
@Issue('https://github.com/apache/grails-core/issues/14503')
class UniqueConstraintNullsSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.registerDomainClasses(UniqueNullKeyWildcard, UniqueNullKeySkip, UniqueNullKeyValue, UniqueNullKeyDatabase)
    }

    @Unroll
    void 'with the #reading reading, the probe (#language, #country) conflicts: #conflicts'() {
        given: 'the rows a key may have, saved without validation since some of them conflict under some readings'
        [['en', 'US'], ['en', null], [null, 'US'], [null, null]].each { String l, String c ->
            domain.newInstance(language: l, country: c).save(validate: false, flush: true)
        }

        expect:
        !domain.newInstance(language: language, country: country).validate() == conflicts

        where:
        reading    | domain                | language | country || conflicts
        'wildcard' | UniqueNullKeyWildcard | 'en'     | 'US'    || true
        'wildcard' | UniqueNullKeyWildcard | 'en'     | null    || true
        'wildcard' | UniqueNullKeyWildcard | 'en'     | 'FR'    || false
        'wildcard' | UniqueNullKeyWildcard | null     | 'US'    || false
        'wildcard' | UniqueNullKeyWildcard | null     | null    || false
        'wildcard' | UniqueNullKeyWildcard | 'fr'     | null    || false
        'skip'     | UniqueNullKeySkip     | 'en'     | 'US'    || true
        'skip'     | UniqueNullKeySkip     | 'en'     | null    || false
        'skip'     | UniqueNullKeySkip     | 'en'     | 'FR'    || false
        'skip'     | UniqueNullKeySkip     | null     | 'US'    || false
        'skip'     | UniqueNullKeySkip     | null     | null    || false
        'skip'     | UniqueNullKeySkip     | 'fr'     | null    || false
        'value'    | UniqueNullKeyValue    | 'en'     | 'US'    || true
        'value'    | UniqueNullKeyValue    | 'en'     | null    || true
        'value'    | UniqueNullKeyValue    | 'en'     | 'FR'    || false
        'value'    | UniqueNullKeyValue    | null     | 'US'    || true
        'value'    | UniqueNullKeyValue    | null     | null    || true
        'value'    | UniqueNullKeyValue    | 'fr'     | null    || false
    }

    @Unroll
    void 'a constraint without a reading follows the database for the probe (#language, #country)'() {
        given:
        UniqueNulls databaseReading = manager.session.datastore.databaseUniqueNulls
        [['en', 'US'], ['en', null], [null, 'US'], [null, null]].each { String l, String c ->
            UniqueNullKeyDatabase.newInstance(language: l, country: c).save(validate: false, flush: true)
        }

        expect: 'the datastore answers SKIP or VALUE, and the constraint reads the same'
        databaseReading in [UniqueNulls.SKIP, UniqueNulls.VALUE]
        manager.session.datastore.uniqueNulls == databaseReading
        !new UniqueNullKeyDatabase(language: language, country: country).validate() ==
                (databaseReading == UniqueNulls.VALUE ? valueConflicts : skipConflicts)

        where:
        language | country || valueConflicts | skipConflicts
        'en'     | 'US'    || true           | true
        'en'     | null    || true           | false
        'en'     | 'FR'    || false          | false
        null     | 'US'    || true           | false
        null     | null    || true           | false
        'fr'     | null    || false          | false
    }
}
