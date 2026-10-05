/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.feature.api

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture

class OpenApiSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'the feature is selectable'() {
        when:
        Features features = getFeatures(['openapi'])

        then:
        features.contains('openapi')
    }

    void 'adds the Grails module and the springdoc UI starter'() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['openapi'])
                .render()

        then: 'the module that contributes the URL mappings to the document'
        template.contains('implementation "org.apache.grails:grails-openapi"')

        and: 'springdoc, which serves the document and Swagger UI'
        template.contains('implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui"')
    }

    void 'does not serve the document or Swagger UI in production'() {
        when:
        GeneratorContext ctx = buildGeneratorContext(['openapi'])

        then:
        ctx.configuration.get('environments.production.springdoc.api-docs.enabled') == false
        ctx.configuration.get('environments.production.springdoc.swagger-ui.enabled') == false
    }

    void 'is offered for an application but not for a plugin'() {
        when:
        def feature = beanContext.getBean(OpenApi)

        then:
        feature.supports(ApplicationType.WEB)
        feature.supports(ApplicationType.REST_API)
        !feature.supports(ApplicationType.WEB_PLUGIN)
        !feature.supports(ApplicationType.PLUGIN)
    }

    void 'the generated README links to the guide'() {
        when:
        def output = generate(ApplicationType.REST_API, ['openapi'])

        then:
        output['README.md'].contains('/guide/REST.html#openApi')
    }
}
