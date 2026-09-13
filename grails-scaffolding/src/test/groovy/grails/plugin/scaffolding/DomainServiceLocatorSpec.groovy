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
package grails.plugin.scaffolding

import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import grails.util.Holders
import org.grails.datastore.gorm.GormEntity

class DomainServiceLocatorSpec extends Specification {

    StaticApplicationContext ctx = new StaticApplicationContext()

    void setup() {
        DefaultGrailsApplication application = new DefaultGrailsApplication()
        application.mainContext = ctx
        Holders.grailsApplication = application
        DomainServiceLocator.clear()
    }

    void cleanup() {
        DomainServiceLocator.clear()
        Holders.clear()
    }

    void 'the service whose resource matches the domain class is resolved'() {
        given:
        GormService<DslWidget> widgetService = new GormService<DslWidget>(DslWidget, false)
        ctx.beanFactory.registerSingleton('widgetService', widgetService)
        ctx.beanFactory.registerSingleton('gadgetService', new GormService<DslGadget>(DslGadget, false))
        ctx.refresh()

        expect:
        DomainServiceLocator.resolve(DslWidget).is(widgetService)
        DomainServiceLocator.resolve(DslWidget).is(widgetService)
    }

    void 'ambiguous and missing services are reported'() {
        given:
        ctx.beanFactory.registerSingleton('one', new GormService<DslWidget>(DslWidget, false))
        ctx.beanFactory.registerSingleton('two', new GormService<DslWidget>(DslWidget, true))
        ctx.refresh()

        when:
        DomainServiceLocator.resolve(DslWidget)

        then:
        IllegalStateException ambiguous = thrown()
        ambiguous.message == 'Multiple GormService beans match domain ' + DslWidget.name + ': [one, two]'

        when:
        DomainServiceLocator.resolve(DslGadget)

        then:
        IllegalStateException missing = thrown()
        missing.message == 'No GormService bean found for domain ' + DslGadget.name + '. Scanned 2 GormService beans.'
    }

}

class DslWidget implements GormEntity<DslWidget> {

    Long id

}

class DslGadget implements GormEntity<DslGadget> {

    Long id

}
