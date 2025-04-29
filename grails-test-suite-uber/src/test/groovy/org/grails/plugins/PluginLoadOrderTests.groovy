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

import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class PluginLoadOrderTests {

    @Test
    void testPluginLoadBeforeAfter() {
        def gcl = new GroovyClassLoader()

        gcl.parseClass '''
class FiveGrailsPlugin {
    def version = 0.1
    def loadAfter = ['one']
}

class FourGrailsPlugin {
    def version = 0.1
    def loadAfter = ['two']
}
class OneGrailsPlugin {
    def version = 0.1
    def loadBefore = ['two']
}
class TwoGrailsPlugin {
    def version = 0.1
    def loadAfter = ['three']
}
class ThreeGrailsPlugin {
    def version = 0.1
}
'''

        def one = gcl.loadClass("OneGrailsPlugin")
        def two = gcl.loadClass("TwoGrailsPlugin")
        def three = gcl.loadClass("ThreeGrailsPlugin")
        def four = gcl.loadClass("FourGrailsPlugin")
        def five = gcl.loadClass("FiveGrailsPlugin")
        def pluginManager = new DefaultGrailsPluginManager([one,two,three, four,five] as Class[],
            new DefaultGrailsApplication())

        pluginManager.loadCorePlugins = false
        pluginManager.loadPlugins()

        assertEquals "one", pluginManager.pluginList[0].name
        assertEquals "three", pluginManager.pluginList[1].name
        assertEquals "five", pluginManager.pluginList[2].name
        assertEquals "two", pluginManager.pluginList[3].name
        assertEquals "four", pluginManager.pluginList[4].name
    }
}
