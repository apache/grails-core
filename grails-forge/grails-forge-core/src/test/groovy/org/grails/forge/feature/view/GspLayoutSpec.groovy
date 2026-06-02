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

package org.grails.forge.feature.view

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import spock.lang.Unroll

class GspLayoutSpec extends ApplicationContextSpec {

    @Unroll
    void "web #applicationType apps use SiteMesh 3 (grails-sitemesh3) by default"() {
        when:
        final String build = new BuildBuilder(beanContext)
            .features(["gsp"])
            .applicationType(applicationType)
            .render()

        then:
        build.contains('implementation "org.apache.grails:grails-sitemesh3"')
        !build.contains('implementation "org.apache.grails:grails-layout"')

        and: "a SNAPSHOT build exposes the SiteMesh 3 snapshot repo so org.sitemesh:*-SNAPSHOT resolves"
        build.contains("https://central.sonatype.com/repository/maven-snapshots")
        build.contains("includeVersionByRegex('org[.]sitemesh.*'")

        where:
        applicationType << [ApplicationType.WEB, ApplicationType.WEB_PLUGIN]
    }

    void "selecting grails-layout replaces the default SiteMesh 3 decorator"() {
        when:
        final String build = new BuildBuilder(beanContext)
            .features(["gsp", "grails-layout"])
            .applicationType(ApplicationType.WEB)
            .render()

        then:
        build.contains('implementation "org.apache.grails:grails-layout"')
        !build.contains('implementation "org.apache.grails:grails-sitemesh3"')
    }

    void "sitemesh3 and grails-layout cannot both be selected"() {
        when:
        new BuildBuilder(beanContext)
            .features(["gsp", "sitemesh3", "grails-layout"])
            .applicationType(ApplicationType.WEB)
            .render()

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("There can only be one of the following features selected")
    }
}
