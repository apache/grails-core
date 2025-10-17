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
package org.grails.datastore.gorm.boot.autoconfigure

import grails.gorm.annotation.Entity
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDataSourceConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.MapPropertySource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Created by graemerocher on 06/02/14.
 */
class HibernateGormAutoConfigurationSpec extends Specification{

    protected AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    void cleanup() {
        context.close()
    }

    void setup() {

        AutoConfigurationPackages.register(context, "org.grails.datastore.gorm.boot.autoconfigure")
        this.context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("foo", ['hibernate.hbm2ddl.auto':'create']))
        def beanFactory = this.context.defaultListableBeanFactory
        beanFactory.registerSingleton("dataSource", new DriverManagerDataSource("jdbc:h2:mem:grailsDb1;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1", 'sa', ''))
        this.context.register( TestConfiguration.class,
                PropertyPlaceholderAutoConfiguration.class);
    }

    void 'Test that GORM is correctly configured'() {
        when:"The context is refreshed"
            context.refresh()

            def result = Person.withTransaction {
                Person.count()
            }

        then:"GORM queries work"
            result == 0

        when:"The addTo* methods are called"
            def p = new Person()
            p.addToChildren(firstName:"Bob")

        then:"They work too"
            p.children.size() == 1
    }

    @Configuration
    @Import(HibernateGormAutoConfiguration)
    static class TestConfiguration {
    }

}


@Entity
class Person {
    String firstName
    String lastName
    Integer age = 18

    Set children = []
    static hasMany = [children: Person]
}


