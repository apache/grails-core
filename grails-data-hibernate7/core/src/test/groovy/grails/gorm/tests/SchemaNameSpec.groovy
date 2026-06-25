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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 20/10/16.
 */
class SchemaNameSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(['dataSource.url':'jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000;INIT=create schema if not exists myschema', (Settings.SETTING_DB_CREATE):'create-drop'],CustomSchema)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10083')
    void 'test schema name alteration with h2'() {
        when:"An object with a custom schema is saved"
        new CustomSchema(name: "Test").save(flush:true)

        then:"The object was persisted"
        CustomSchema.count() == 1
    }


}
@Entity
class CustomSchema {
    String name
    static mapping = {
        table schema:'myschema'
    }
}


