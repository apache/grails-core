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

package org.grails.forge.feature.grails

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType

class GrailsCompileStaticSpec extends ApplicationContextSpec {

    void 'test grails-compile-static compiles artefacts and GSP views statically'() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['grails-compile-static'])
                .render()

        then:
        template.contains('''\
            grails {
                compileStatic {
                    controllers = true
                    services = true
                    tagLibs = true
                    gsp = true
                }
            }
            '''.stripIndent(12))

        and: 'each artefact type is opted in on its own, so one a later release adds to all is not'
        !template.contains('all = true')
    }

    void 'test grails-compile-static leaves out gsp for an application without GSP'() {
        when:
        String template = new BuildBuilder(beanContext)
                .applicationType(ApplicationType.REST_API)
                .features(['grails-compile-static'])
                .render()

        then:
        template.contains('controllers = true')
        template.contains('services = true')
        template.contains('tagLibs = true')
        !template.contains('gsp = true')
        !template.contains('all = true')
    }

    void 'test no compileStatic block without grails-compile-static'() {
        when:
        String template = new BuildBuilder(beanContext).render()

        then:
        !template.contains('compileStatic')
    }
}
