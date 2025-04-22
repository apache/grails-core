/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import org.springframework.core.io.Resource
import org.codehaus.groovy.grails.plugins.orm.hibernate.*

/**
* A plug-in that handles the configuration of Hibernate within Grails
*
* @author Graeme Rocher
* @since 0.4
*/
class HibernateGrailsPlugin {
	def author = "Graeme Rocher"
	def title = "Hibernate for Grails"
	def description = "A plugin that provides integration between Grails and Hibernate through GORM"

	
    def version = "1.2-SNAPSHOT"
    def documentation = "http://grails.org/doc/$version"

    def dependsOn = [dataSource: "1.1 > *",
                     i18n: "1.1 > *",
                     core: "1.1 > *",
                     domainClass: "1.1 > *"]

    def loadAfter = ['controllers', 'domainClass']

    def watchedResources = ["file:./grails-app/conf/hibernate/**.xml"]

    def doWithSpring = HibernatePluginSupport.doWithSpring

    def doWithDynamicMethods = HibernatePluginSupport.doWithDynamicMethods

    def onChange = {event ->
        if (event.source instanceof Resource) {
            restartContainer()
        }
    }
}
