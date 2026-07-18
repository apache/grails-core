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
package org.grails.forge.feature.mail

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.fixture.CommandOutputFixture

class MailSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test gradle mail feature adds the plugin dependency'() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['mail'])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-mail"')
    }

    void 'test mail feature configures the smtp defaults'() {
        when:
        GeneratorContext commandContext = buildGeneratorContext(['mail'])

        then:
        commandContext.configuration.get('grails.mail.host') == 'localhost'
        commandContext.configuration.get('grails.mail.port') == 25
    }

    void 'test readme.md with feature mail contains links to documentation'() {
        when:
        def output = generate(['mail'])
        def readme = output['README.md']

        then:
        readme
        readme.contains('guide/mail.html')
    }

}
