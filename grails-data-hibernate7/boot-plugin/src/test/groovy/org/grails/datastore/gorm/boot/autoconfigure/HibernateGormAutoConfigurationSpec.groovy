/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.boot.autoconfigure

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import spock.lang.Specification

import javax.sql.DataSource

class HibernateGormAutoConfigurationSpec extends Specification {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TestConfiguration, HibernateGormAutoConfiguration))
            .withInitializer { context ->
                AutoConfigurationPackages.register(context, 'org.grails.datastore.gorm.boot.autoconfigure')
            }
            .withPropertyValues('spring.datasource.url=jdbc:h2:mem:testdb')

    def 'should configure hibernate datastore'() {
        given:
        def dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build()

        when:
        contextRunner
                .withBean(DataSource, { dataSource })
                .run { context ->
                    assert context.containsBean('hibernateDatastore')
                    assert context.containsBean('sessionFactory')
                    assert context.containsBean('hibernateTransactionManager')
                    assert context.getBean(HibernateDatastore) != null
                }
        
        then:
        noExceptionThrown()

        cleanup:
        dataSource.shutdown()
    }

    @Configuration
    static class TestConfiguration {

    }
}

