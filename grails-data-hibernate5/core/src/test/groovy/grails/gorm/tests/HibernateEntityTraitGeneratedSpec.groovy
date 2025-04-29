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

import grails.gorm.transactions.Rollback
import groovy.transform.Generated
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

@Rollback
class HibernateEntityTraitGeneratedSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Club)

    void "test that all HibernateEntity trait methods are marked as Generated"() {
        // Unfortunately static methods have to check directly one by one
        expect:
        Club.getMethod('findAllWithSql', CharSequence).isAnnotationPresent(Generated)
        Club.getMethod('findWithSql', CharSequence).isAnnotationPresent(Generated)
        Club.getMethod('findAllWithSql', CharSequence, Map).isAnnotationPresent(Generated)
        Club.getMethod('findWithSql', CharSequence, Map).isAnnotationPresent(Generated)
    }

}
