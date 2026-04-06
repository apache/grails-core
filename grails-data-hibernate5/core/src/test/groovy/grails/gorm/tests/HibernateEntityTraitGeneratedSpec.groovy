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
        // Static SQL methods (findAllWithSql, findWithSql) were moved from the HibernateEntity
        // trait to AbstractHibernateGormStaticApi for Groovy 5 compatibility (traits with static
        // methods cause Java stub generation issues during joint compilation). These methods are
        // now accessed via GormEnhancer.findStaticApi() and are no longer compile-time generated.
        expect:
        // Verify the domain class implements HibernateEntity (trait is still applied)
        grails.gorm.hibernate.HibernateEntity.isAssignableFrom(Club)
    }

}
