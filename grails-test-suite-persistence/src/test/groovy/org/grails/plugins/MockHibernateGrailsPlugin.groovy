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
package org.grails.plugins

import grails.util.GrailsUtil

import org.apache.tomcat.jdbc.pool.DataSource
import org.grails.core.artefact.AnnotationDomainClassArtefactHandler
import org.springframework.jdbc.datasource.DataSourceTransactionManager

class MockHibernateGrailsPlugin {

    def version = GrailsUtil.grailsVersion
    def dependsOn = [dataSource: version, i18n: version, core: version, domainClass: version]

    def artefacts = [new AnnotationDomainClassArtefactHandler()]
    def loadAfter = ['controllers']
    def doWithSpring = {
        dataSource(DataSource) {
            driverClassName = 'org.h2.Driver'
            url = 'jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000'
            username = 'sa'
            password = ''
        }

        transactionManager(DataSourceTransactionManager, ref('dataSource'))
    }
}
