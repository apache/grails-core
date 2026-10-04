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
package org.apache.grails.scaffolding.aot

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification

class ScaffoldingRuntimeHintsSpec extends Specification {

    void 'an image carries the scaffolding template #template, which a compiled page is found by'() {
        given:
        RuntimeHints hints = new RuntimeHints()

        when:
        new ScaffoldingRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.resource().forResource(template).test(hints)

        where:
        template << ['META-INF/templates/scaffolding/show.gsp', 'META-INF/templates/scaffolding/admin/show.gsp']
    }

    void 'the controller serving a scaffolded resource is kept for reflection'() {
        given:
        RuntimeHints hints = new RuntimeHints()

        when:
        new ScaffoldingRuntimeHints().registerHints(hints, getClass().classLoader)

        then:
        RuntimeHintsPredicates.reflection().onType(grails.plugin.scaffolding.RestfulServiceController).test(hints)
    }
}
