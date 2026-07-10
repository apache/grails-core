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
package org.grails.datastore.gorm.query

import org.slf4j.Logger

import spock.lang.Specification

class GormQuerySafetyWarningsSpec extends Specification {

    void 'warns once for a GString query without logging interpolated values'() {
        given:
        Logger logger = Mock() {
            isWarnEnabled() >> true
        }
        String title = 'secret-title'
        GString query = "from Book b where b.title = ${title}"

        when:
        boolean firstWarning = GormQuerySafetyWarnings.warnIfGStringQuery(logger, query, 'Book.find')
        boolean secondWarning = GormQuerySafetyWarnings.warnIfGStringQuery(logger, query, 'Book.find')

        then:
        firstWarning
        !secondWarning
        1 * logger.warn({ String message ->
            message.contains('explicit named parameters')
        }, 'Book.find', { String queryShape ->
            queryShape == 'from Book b where b.title = ${...}' && !queryShape.contains(title)
        })
        0 * logger.warn(_, _, _)
    }

    void 'does not warn for a plain String query'() {
        given:
        Logger logger = Mock()

        when:
        boolean warning = GormQuerySafetyWarnings.warnIfGStringQuery(logger, 'from Book b where b.title = :title', 'Book.find')

        then:
        !warning
        0 * logger._
    }

    void 'does not suppress a query shape when warn logging is disabled'() {
        given:
        Logger logger = Mock() {
            isWarnEnabled() >>> [false, true]
        }
        String title = 'enabled-later'
        GString query = "from Book b where b.title <> ${title}"

        when:
        boolean firstWarning = GormQuerySafetyWarnings.warnIfGStringQuery(logger, query, 'Book.executeQuery')
        boolean secondWarning = GormQuerySafetyWarnings.warnIfGStringQuery(logger, query, 'Book.executeQuery')

        then:
        !firstWarning
        secondWarning
        1 * logger.warn(_ as String, 'Book.executeQuery', 'from Book b where b.title <> ${...}')
    }
}
