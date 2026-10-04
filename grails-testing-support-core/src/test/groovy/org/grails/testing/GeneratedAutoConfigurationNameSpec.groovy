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
package org.grails.testing

import spock.lang.Specification
import testing.included.IncludedBeansGrailsPlugin
import testing.renamed.BareRenamedBeansPlugin
import testing.renamed.RenamedBeansPlugin

/**
 * The name the harness looks an included plugin's generated class up by, which has to be the one the
 * build gave it. Registering it once found is IncludedPluginBeansSpec's.
 */
class GeneratedAutoConfigurationNameSpec extends Specification {

    void "a plugin's generated class is named as @GrailsBeans named it: #description"() {
        expect:
        GrailsApplicationBuilder.generatedAutoConfigurationName(pluginClass) == generated

        and: 'the build generated a class of that name'
        Class.forName(generated, false, getClass().classLoader)

        where:
        description                            | pluginClass               || generated
        'the default for a *GrailsPlugin'      | IncludedBeansGrailsPlugin || 'testing.included.IncludedBeansAutoConfiguration'
        'a qualified autoConfigurationName'    | RenamedBeansPlugin        || 'testing.renamed.generated.RenamedGreetingConfiguration'
        'a bare one, in the plugin\'s package' | BareRenamedBeansPlugin    || 'testing.renamed.BareRenamedConfiguration'
    }
}
