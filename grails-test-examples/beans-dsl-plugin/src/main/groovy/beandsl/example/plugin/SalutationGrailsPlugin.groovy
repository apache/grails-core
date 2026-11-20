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
package beandsl.example.plugin

import org.springframework.boot.autoconfigure.AutoConfiguration

import grails.compiler.beans.GrailsBeans
import grails.plugins.Plugin

/**
 * Demonstrates a qualified {@code autoConfigurationName}. The generated sibling lands in
 * {@code beandsl.example.plugin.web} rather than this class's own package, which is the case a
 * conversion needs: a descriptor conventionally sits in the package its implementation classes sit
 * beneath, so the {@code @AutoConfiguration} class being replaced - the one other modules already
 * name in an {@code excludeName}, a {@code before=}/{@code after=}, or a test import - is usually
 * in another package, and only the qualified name preserves that identity.
 *
 * <p>{@code Salutation} stays in this package deliberately: the {@code bean(...)} below names it
 * unqualified, so compiling it onto a sibling in another package also exercises that the generated
 * class still resolves types from the source file's own package.
 */
@GrailsBeans(autoConfigurationName = 'beandsl.example.plugin.web.SalutationAutoConfiguration')
@AutoConfiguration
class SalutationGrailsPlugin extends Plugin {

    String version = '1.0'

    def beans = {
        bean('salutation', Salutation) {
            new Salutation('greetings from another package')
        }
    }

}
