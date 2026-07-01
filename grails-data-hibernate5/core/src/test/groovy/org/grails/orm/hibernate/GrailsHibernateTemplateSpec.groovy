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
package org.grails.orm.hibernate

import java.sql.Connection
import java.util.UUID

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.hibernate.dialect.H2Dialect
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

class GrailsHibernateTemplateSpec extends Specification {

    void "executeWithNewSession succeeds and restores TSM when DataSource holder is pre-bound without a SessionFactory holder"() {
        given: "a fresh datastore with no active outer transaction and synchronization inactive"
        assert !TransactionSynchronizationManager.isSynchronizationActive()
        HibernateDatastore localDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver([
                'dataSource.url'        : "jdbc:h2:mem:h5TsmTest_${UUID.randomUUID().toString().replace('-', '')};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'   : 'create-drop',
                'dataSource.dialect'    : H2Dialect.name,
                'hibernate.hbm2ddl.auto': 'create-drop',
            ]), H5TemplateBook)
        GrailsHibernateTemplate localTemplate = new GrailsHibernateTemplate(localDatastore.sessionFactory)

        and: "synchronization is activated then the DataSource is pre-bound, simulating SQLErrorCodesFactory behaviour"
        // Spring's SQLErrorCodesFactory.getErrorCodes() calls DataSourceUtils.getConnection()
        // while a parent-transaction sync is active, binding DS to TSM without a matching SF.
        // Without the fix, executeWithNewSession skipped the DS unbind when SF was absent,
        // leaving DS bound so HibernateTransactionManager.doBegin threw "Already value bound".
        TransactionSynchronizationManager.initSynchronization()
        javax.sql.DataSource ds = localTemplate.@dataSource
        Connection preBoundConn = ds.getConnection()
        TransactionSynchronizationManager.bindResource(ds, new ConnectionHolder(preBoundConn))
        assert !TransactionSynchronizationManager.hasResource(localDatastore.sessionFactory)

        when: "executeWithNewSession is called with DS bound but SF not bound in TSM"
        localTemplate.executeWithNewSession { sess -> }

        then: "no exception is thrown — the bug caused 'Already value bound' for the DataSource"
        noExceptionThrown()

        and: "the DataSource resource is accessible in TSM after the nested-session scope exits"
        TransactionSynchronizationManager.hasResource(ds)

        cleanup:
        if (ds != null && TransactionSynchronizationManager.hasResource(ds)) {
            TransactionSynchronizationManager.unbindResource(ds)
        }
        preBoundConn?.close()
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        localDatastore?.close()
    }
}

@Entity
class H5TemplateBook implements HibernateEntity<H5TemplateBook> {
    String title
    String author
}
