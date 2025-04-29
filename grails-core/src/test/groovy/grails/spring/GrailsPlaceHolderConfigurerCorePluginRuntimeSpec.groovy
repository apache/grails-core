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
package grails.spring

import grails.core.DefaultGrailsApplication
import org.grails.plugins.CoreGrailsPlugin
import org.grails.spring.context.support.GrailsPlaceholderConfigurer
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class GrailsPlaceHolderConfigurerCorePluginRuntimeSpec extends Specification{

    @Issue('GRAILS-10130')
    void "Test that system properties are used to replace values at runtime with GrailsPlaceHolderConfigurer"() {
        given:"A configured application context"
            def parent = new BeanBuilder()
            parent.beans {
                grailsApplication(DefaultGrailsApplication)
            }
            def bb = new BeanBuilder(parent.createApplicationContext())

            final beanBinding = new Binding()

            def app = new DefaultGrailsApplication()
            beanBinding.setVariable('application', app)
            bb.setBinding(beanBinding)

            def plugin = new CoreGrailsPlugin()
            plugin.grailsApplication = app
            bb.beans plugin.doWithSpring()
            bb.beans {
                propertySourcesPlaceholderConfigurer(GrailsPlaceholderConfigurer)
                testBean(ReplacePropertyBean) {
                    foo = '${foo.bar}'
                }
            }

        when:"A system property is used in a bean property"
            System.setProperty('foo.bar', "test")
            final appCtx = bb.createApplicationContext()
            def bean = appCtx.getBean("testBean")

        then:"The system property is ready"
            appCtx != null
            bean.foo == 'test'
    }

}
class ReplacePropertyBean {
    String foo
}

