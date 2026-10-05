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
package testing.renamed

import org.springframework.boot.autoconfigure.AutoConfiguration

import grails.compiler.beans.GrailsBeans
import grails.plugins.Plugin

/**
 * A plugin whose beans block compiles to a class it names itself, in another package, where the
 * default name could never find it. Only named, not discovered: the build writes one plugin
 * descriptor per output directory, and this module's is IncludedBeansGrailsPlugin's.
 */
@GrailsBeans(autoConfigurationName = 'testing.renamed.generated.RenamedGreetingConfiguration')
@AutoConfiguration
class RenamedBeansPlugin extends Plugin {

    String version = '1.0'

    def beans = {
        bean('renamedGreeting', String) {
            'from a renamed configuration'
        }
    }
}

/** Renamed within its own package; not discovered, only named. */
@GrailsBeans(autoConfigurationName = 'BareRenamedConfiguration')
@AutoConfiguration
class BareRenamedBeansPlugin extends Plugin {

    def beans = {
        bean('bareRenamedGreeting', String) {
            'from a bare renamed configuration'
        }
    }
}
