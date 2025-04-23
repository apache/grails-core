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


import org.grails.datastore.gorm.neo4j.Neo4jGrailsPlugin
import org.grails.plugins.MockGrailsPluginManager
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class StandaloneNeo4jSpec extends Specification implements GrailsUnitTest {

    @Override
    Set<String> getIncludePlugins() {
        ["domainClass"]
    }

    void "test that both grailsDomainClassMappingContext and neo4jMappingContext are same when using standalone Neo4j"() {

        setup:
        Neo4jGrailsPlugin plugin = new Neo4jGrailsPlugin()
        plugin.grailsApplication = grailsApplication
        plugin.applicationContext = applicationContext
        plugin.setPluginManager(new MockGrailsPluginManager(grailsApplication))
        this.defineBeans(plugin)

        expect:
        applicationContext.containsBean("grailsDomainClassMappingContext")
        applicationContext.containsBean("neo4jMappingContext")
        applicationContext.getBean("grailsDomainClassMappingContext") == applicationContext.getBean("neo4jMappingContext")
    }
}
