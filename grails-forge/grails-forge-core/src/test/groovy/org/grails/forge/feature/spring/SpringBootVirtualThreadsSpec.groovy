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

package org.grails.forge.feature.spring

import org.grails.forge.BeanContextSpec
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class SpringBootVirtualThreadsSpec extends BeanContextSpec implements CommandOutputFixture {

    void "test spring boot virtual threads not enabled for JDK 21, when optional feature selected"() {
        when:
        GeneratorContext commandContext = buildGeneratorContext(['spring-boot-virtual-threads'], new Options(DevelopmentReloading.DEFAULT_OPTION, JdkVersion.JDK_21))

        then:
        commandContext.configuration.get('spring.threads.virtual.enabled'.toString()) == false
    }

    void "test spring boot virtual threads enabled for JDK 25+, when optional feature selected"() {
        when:
        GeneratorContext commandContext = buildGeneratorContext(['spring-boot-virtual-threads'], new Options(DevelopmentReloading.DEFAULT_OPTION, JdkVersion.JDK_25))

        then:
        commandContext.configuration.get('spring.threads.virtual.enabled'.toString()) == true
    }
}
