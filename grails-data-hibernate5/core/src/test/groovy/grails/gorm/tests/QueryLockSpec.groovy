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

import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.LockMode
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14501')
class QueryLockSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Club])
    }

    void 'lock(#flag) sets the lock mode #mode on the query'() {
        given:
        HibernateQuery query = manager.session.createQuery(Club)

        when:
        query.lock(flag)

        then:
        query.hibernateCriteria.@lockModes.values().first() == mode

        where:
        flag  | mode
        true  | LockMode.PESSIMISTIC_WRITE
        false | LockMode.NONE
    }

    void 'lock(false) after lock(true) releases the lock'() {
        given:
        HibernateQuery query = manager.session.createQuery(Club)

        when:
        query.lock(true).lock(false)

        then:
        query.hibernateCriteria.@lockModes.values().first() == LockMode.NONE
    }
}
