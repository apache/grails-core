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
package grails.util

import grails.config.Config
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import spock.lang.Specification

class HoldersSpec extends Specification {

    void cleanup() {
        // clear(), unlike reset(), guards its servlet-context write and is therefore safe to call
        // outside a servlet environment (grails-core has no grails.web.context.WebRequestServletHolder
        // on its classpath, so Holders.servletContexts is never populated here)
        Holders.clear()
    }

    void 'getConfig returns whatever was set with setConfig'() {
        given:
        Config config = Stub(Config)

        when:
        Holders.setConfig(config)

        then:
        Holders.getConfig().is(config)
    }

    void 'getPluginManager returns whatever was set with setPluginManager'() {
        given:
        GrailsPluginManager pluginManager = Stub(GrailsPluginManager)

        when:
        Holders.setPluginManager(pluginManager)

        then:
        Holders.getPluginManager().is(pluginManager)
        Holders.currentPluginManager().is(pluginManager)
    }

    void 'currentPluginManager throws when no plugin manager has been set'() {
        when:
        Holders.currentPluginManager()

        then:
        thrown(IllegalArgumentException)
    }

    void 'setGrailsApplication makes findApplication and getGrailsApplication return it when no discovery strategy claims it'() {
        given:
        GrailsApplication application = Stub(GrailsApplication)

        when:
        Holders.setGrailsApplication(application)

        then:
        Holders.findApplication().is(application)
        Holders.getGrailsApplication().is(application)
    }

    void 'getGrailsApplication throws when no application has been set and no strategy finds one'() {
        when:
        Holders.getGrailsApplication()

        then:
        thrown(IllegalArgumentException)
    }

    void 'a registered discovery strategy is preferred over the plain application singleton'() {
        given:
        GrailsApplication fromSingleton = Stub(GrailsApplication)
        GrailsApplication fromStrategy = Stub(GrailsApplication)
        Holders.setGrailsApplication(fromSingleton)
        Holders.addApplicationDiscoveryStrategy(Stub(GrailsApplicationDiscoveryStrategy) {
            findGrailsApplication() >> fromStrategy
        })

        expect:
        Holders.findApplication().is(fromStrategy)
    }

    void 'clear resets the config, plugin manager, and application singleton'() {
        given:
        Holders.setConfig(Stub(Config))
        Holders.setPluginManager(Stub(GrailsPluginManager))
        Holders.setGrailsApplication(Stub(GrailsApplication))

        when:
        Holders.clear()

        then:
        Holders.getConfig() == null
        Holders.getPluginManager() == null
        Holders.findApplication() == null
    }

    void 'reset unconditionally touches the servlet context holder and fails outside a servlet environment'() {
        // documents existing (surprising) behaviour rather than exercising the servlet code path:
        // reset() calls setServletContext(null) unguarded, unlike clear(), so it NPEs whenever the
        // servlet context holder was never created (e.g. grails-core's own unit tests)
        when:
        Holders.reset()

        then:
        thrown(NullPointerException)
    }

    void 'getFlatConfig returns an empty map rather than null when no config has been set'() {
        expect:
        Holders.getFlatConfig() == [:]
    }
}
