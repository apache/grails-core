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

import io.micrometer.common.KeyValues
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource

import spock.lang.Specification

/**
 * Tests the {@code gsp.compile} observation and {@code gsp.template.cache} hit/miss counters on
 * {@link GroovyPagesTemplateEngine}. The real GSP compilation is overridden so the test stays a unit.
 */
class GroovyPagesTemplateEngineObservationSpec extends Specification {

    private List<Observation.Context> recorded = []
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()

    /** Engine whose actual compilation returns a stub, wired with a recording registry + meter registry. */
    private GroovyPagesTemplateEngine engine(Closure compileHook = null) {
        ObservationRegistry observationRegistry = ObservationRegistry.create()
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override boolean supportsContext(Observation.Context context) { true }
            @Override void onStop(Observation.Context context) { recorded << context }
        })

        GroovyPagesTemplateEngine engine = new GroovyPagesTemplateEngine() {
            @Override
            protected GroovyPageMetaInfo buildPageMetaInfo(InputStream inputStream, Resource res, String pageName) {
                // Let a test re-enter createTemplate mid-compile to exercise the reentrancy path.
                if (compileHook != null) {
                    compileHook.call(pageName)
                }
                return new GroovyPageMetaInfo()
            }
        }
        engine.setReloadEnabled(false)

        GenericApplicationContext ctx = new GenericApplicationContext()
        ctx.getBeanFactory().registerSingleton('observationRegistry', observationRegistry)
        ctx.getBeanFactory().registerSingleton('simpleMeterRegistry', meterRegistry)
        ctx.refresh()
        engine.setApplicationContext(ctx)
        return engine
    }

    private Resource gsp() {
        new ByteArrayResource('<html><body>hi</body></html>'.bytes)
    }

    private double cacheCount(String result) {
        def c = meterRegistry.find('gsp.template.cache').tag('result', result).counter()
        c != null ? c.count() : 0d
    }

    void "compiling a page records a gsp.compile observation tagged with the page name"() {
        when:
        engine().buildPageMetaInfo(gsp(), '/book/show')

        then:
        recorded.size() == 1
        recorded[0].name == 'gsp.compile'
        recorded[0].contextualName == 'gsp.compile /book/show'

        and:
        KeyValues kvs = recorded[0].lowCardinalityKeyValues
        kvs.find { it.key == 'gsp.name' }?.value == '/book/show'
        kvs.find { it.key == 'error' }?.value == 'none'
    }

    void "the template cache records a miss then a hit for the same page"() {
        given:
        GroovyPagesTemplateEngine engine = engine()
        Resource resource = gsp()

        when: "the page is requested twice (cacheable)"
        engine.createTemplate(resource, '/book/list', true)
        engine.createTemplate(resource, '/book/list', true)

        then: "first access misses (and compiles), second hits"
        cacheCount('miss') == 1d
        cacheCount('hit') == 1d
        recorded.count { it.name == 'gsp.compile' } == 1
    }

    void "a reentrant cache hit during compilation does not mask the outer miss"() {
        given: "an engine that, while compiling the outer page, re-renders an already-cached inner page"
        Resource innerResource = gsp()
        List<GroovyPagesTemplateEngine> engineRef = []
        GroovyPagesTemplateEngine engine = engine({ String pageName ->
            if (pageName == '/page/outer') {
                engineRef[0].createTemplate(innerResource, '/layout/inner', true)
            }
        })
        engineRef << engine

        and: "the inner page is already cached, so its reentrant lookup will be a hit"
        engine.createTemplate(innerResource, '/layout/inner', true)

        when: "the outer page is compiled (a miss) and re-enters the cached inner lookup mid-compile"
        engine.createTemplate(gsp(), '/page/outer', true)

        then: "the outer compile is still counted as a miss; only the reentrant inner lookup is a hit"
        cacheCount('miss') == 2d
        cacheCount('hit') == 1d
        recorded.count { it.name == 'gsp.compile' } == 2
    }
}
