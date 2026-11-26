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

package grails.tenant.app


import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver
import org.grails.gorm.graphql.plugin.GormGraphqlGrailsPlugin
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class GraphqlMultiTenantSpec extends Specification implements GrailsUnitTest {

    void "test GraphQl with multi tenancy mode schema and session tenant resolver"() {

        given:
        HibernateDatastore datastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(
                        [(Settings.SETTING_MULTI_TENANCY_MODE)          : MultiTenancySettings.MultiTenancyMode.SCHEMA,
                         (Settings.SETTING_MULTI_TENANT_RESOLVER_CLASS) : SessionTenantResolver.name,
                         (Settings.SETTING_DB_CREATE)                   : 'create-drop']),
                [User] as Class[])

        defineBeans {
            hibernateDatastore(InstanceFactoryBean, datastore, HibernateDatastore)
            grailsDomainClassMappingContext(hibernateDatastore: "getMappingContext")
        }

        GormGraphqlGrailsPlugin graphqlGrailsPlugin = new GormGraphqlGrailsPlugin()
        graphqlGrailsPlugin.grailsApplication = grailsApplication
        this.defineBeans(graphqlGrailsPlugin)

        expect:
        grailsApplication.mainContext.containsBean("graphQL")

    }

}
