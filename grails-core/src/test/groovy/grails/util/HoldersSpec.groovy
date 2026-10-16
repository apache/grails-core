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
import org.grails.core.io.support.GrailsFactoriesLoader
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import spock.lang.Specification

class HoldersSpec extends Specification {

    void cleanup() {
        // clear() is the only public way to drop a strategy a feature method registered, but it also drops the
        // strategies Holders loaded from grails.factories when it initialised. Test forks are shared between
        // specs, so re-register those from the same source Holders used to leave later specs the discovery
        // chain they would have seen without this one.
        Holders.clear()
        GrailsFactoriesLoader.loadFactories(GrailsApplicationDiscoveryStrategy, Holders.classLoader).each {
            Holders.addApplicationDiscoveryStrategy(it)
        }
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

    void 'reset clears the config, plugin manager and application and is safe outside a servlet environment'() {
        given:
        Holders.setConfig(Stub(Config))
        Holders.setPluginManager(Stub(GrailsPluginManager))
        Holders.setGrailsApplication(Stub(GrailsApplication))

        when: 'grails-core has no servlet context holder on its classpath, so the servlet write has to be skipped'
        Holders.reset()

        then:
        Holders.getConfig() == null
        Holders.getPluginManager() == null
        Holders.findApplication() == null
    }

    void 'reset ends a plugin manager creation phase so getPluginManager no longer waits for it'() {
        given:
        Holders.setPluginManagerInCreation(true)

        when:
        Holders.reset()

        then:
        Holders.getPluginManager() == null
    }

    void 'reset keeps the registered discovery strategies, unlike clear'() {
        given:
        GrailsApplication fromStrategy = Stub(GrailsApplication)
        Holders.addApplicationDiscoveryStrategy(Stub(GrailsApplicationDiscoveryStrategy) {
            findGrailsApplication() >> fromStrategy
        })

        when:
        Holders.reset()

        then:
        Holders.findApplication().is(fromStrategy)
    }

    void 'getFlatConfig returns an empty map rather than null when no config has been set'() {
        expect:
        Holders.getFlatConfig() == [:]
    }
}
