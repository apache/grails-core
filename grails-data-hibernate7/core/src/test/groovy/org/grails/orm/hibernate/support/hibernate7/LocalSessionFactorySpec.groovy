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
package org.grails.orm.hibernate.support.hibernate7

import javax.sql.DataSource
import org.hibernate.SessionFactory
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification

class LocalSessionFactorySpec extends Specification {

    def "test LocalSessionFactoryBean configuration"() {
        given: "A session factory bean and mocked dependencies"
        def bean = new LocalSessionFactoryBean()
        def dataSource = Mock(DataSource)
        def resourceLoader = Mock(ResourceLoader)

        when: "properties are set"
        bean.setDataSource(dataSource)
        bean.setResourceLoader(resourceLoader)
        bean.setHibernateProperties(new Properties([ "hibernate.dialect": "org.hibernate.dialect.H2Dialect" ]))

        then: "they are correctly held"
        bean.getObjectType() == SessionFactory
    }

    def "test LocalSessionFactoryBuilder configuration"() {
        given: "A session factory builder"
        def dataSource = Mock(DataSource)
        def builder = new LocalSessionFactoryBuilder(dataSource)

        expect: "it is correctly initialized"
        builder != null
    }
}
