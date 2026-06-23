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
package org.grails.gsp

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource

import spock.lang.Specification

/**
 * Tests the {@code gsp.compile} observation on {@link GroovyPagesTemplateEngine}. The real GSP
 * compilation is overridden so the test stays a unit.
 */
class GroovyPagesTemplateEngineObservationSpec extends Specification {

    private List<Observation.Context> recorded = []

    /** Engine whose actual compilation returns a stub, wired with a recording observation registry. */
    private GroovyPagesTemplateEngine engine() {
        ObservationRegistry observationRegistry = ObservationRegistry.create()
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override boolean supportsContext(Observation.Context context) { true }
            @Override void onStop(Observation.Context context) { recorded << context }
        })

        def engine = new GroovyPagesTemplateEngine() {
            @Override
            protected GroovyPageMetaInfo buildPageMetaInfo(InputStream inputStream, Resource res, String pageName) {
                return new GroovyPageMetaInfo()
            }
        }
        engine.setReloadEnabled(false)

        def ctx = new GenericApplicationContext()
        ctx.getBeanFactory().registerSingleton('observationRegistry', observationRegistry)
        ctx.refresh()
        engine.setApplicationContext(ctx)
        return engine
    }

    private Resource gsp() {
        new ByteArrayResource('<html><body>hi</body></html>'.bytes)
    }

    void "compiling a page records a gsp.compile observation tagged with the page name"() {
        when:
        engine().buildPageMetaInfo(gsp(), '/book/show')

        then:
        recorded.size() == 1
        recorded[0].name == 'gsp.compile'
        recorded[0].contextualName == 'gsp.compile /book/show'

        and: "gsp.name is high-cardinality (span only); no error on a successful compile"
        recorded[0].highCardinalityKeyValues.find { it.key == 'gsp.name' }?.value == '/book/show'
        recorded[0].lowCardinalityKeyValues.find { it.key == 'gsp.name' } == null
        recorded[0].error == null
    }
}
