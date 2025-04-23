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

package functional.tests


import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import org.grails.datastore.gorm.neo4j.Neo4jGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class Neo4jWithHibernateSpec extends Specification implements GrailsUnitTest {

    @Override
    Set<String> getIncludePlugins() {
        ["domainClass"]
    }

    void "test that both grailsDomainClassMappingContext and neo4jMappingContext are same when using Neo4j with Hibernate"() {

        setup:
        GrailsPluginManager pluginManager = new MockGrailsPluginManager(grailsApplication)
        GrailsPlugin hibernate = Mock(GrailsPlugin)
        hibernate.getName() >> "hibernate"
        pluginManager.registerMockPlugin(hibernate)

        Neo4jGrailsPlugin neo4jGrailsPlugin = new Neo4jGrailsPlugin()
        neo4jGrailsPlugin.grailsApplication = grailsApplication
        neo4jGrailsPlugin.applicationContext = applicationContext
        neo4jGrailsPlugin.pluginManager = pluginManager
        this.defineBeans(neo4jGrailsPlugin)

        expect:
        applicationContext.containsBean("grailsDomainClassMappingContext")
        applicationContext.containsBean("neo4jMappingContext")
        applicationContext.getBean("grailsDomainClassMappingContext") != applicationContext.getBean("neo4jMappingContext")
    }
}
