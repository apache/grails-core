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
package org.grails.datastore.gorm.jdbc

import javax.sql.DataSource

import spock.lang.Specification

class MultiTenantDataSourceSpec extends Specification {

    void "target and tenantId are exposed"() {
        given:
        def target = Mock(DataSource)

        when:
        def dataSource = new MultiTenantDataSource(target, 'tenantA')

        then:
        dataSource.target.is(target)
        dataSource.tenantId == 'tenantA'
    }

    void "getConnection delegates to the target data source"() {
        given:
        def target = Mock(DataSource)
        def connection = Mock(java.sql.Connection)
        def dataSource = new MultiTenantDataSource(target, 'tenantA')

        when:
        def result = dataSource.getConnection()

        then:
        1 * target.getConnection() >> connection
        result.is(connection)
    }

    void "two instances with the same tenantId are equal regardless of target"() {
        given:
        def targetA = Mock(DataSource)
        def targetB = Mock(DataSource)

        expect:
        new MultiTenantDataSource(targetA, 'tenantA') == new MultiTenantDataSource(targetB, 'tenantA')
        new MultiTenantDataSource(targetA, 'tenantA').hashCode() == new MultiTenantDataSource(targetB, 'tenantA').hashCode()
    }

    void "two instances with different tenantIds are not equal"() {
        given:
        def target = Mock(DataSource)

        expect:
        new MultiTenantDataSource(target, 'tenantA') != new MultiTenantDataSource(target, 'tenantB')
    }
}
