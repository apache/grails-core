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

package org.grails.forge.feature.micronaut

import org.grails.forge.BeanContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.options.JdkVersion

class GrailsMicronautSpec extends BeanContextSpec {

    void "test grails-micronaut adds the dependency when JDK 25 is selected"() {
        when:
        final String template = new BuildBuilder(beanContext)
                .features(["grails-micronaut"])
                .jdkVersion(JdkVersion.JDK_25)
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-micronaut')
    }

    void "test grails-micronaut is rejected when the selected JDK is below 25"() {
        when:
        // micronaut-core's ScopedValues references java.lang.ScopedValue.CallableOp
        // (JEP 506, finalized in JDK 25), so the feature must refuse older JDKs.
        new BuildBuilder(beanContext)
                .features(["grails-micronaut"])
                .jdkVersion(JdkVersion.JDK_21)
                .render()

        then:
        IllegalArgumentException e = thrown()
        e.message == 'grails-micronaut requires JDK 25 or later (selected: JDK 21).'
    }
}
