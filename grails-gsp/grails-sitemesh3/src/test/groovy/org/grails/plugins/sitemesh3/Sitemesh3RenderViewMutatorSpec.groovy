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
package org.grails.plugins.sitemesh3

import java.util.Locale

import jakarta.servlet.ServletContext

import org.sitemesh.DecoratorSelector
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.ContentProcessor

import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver

import spock.lang.Specification

class Sitemesh3RenderViewMutatorSpec extends Specification {

    Sitemesh3RenderViewMutator mutator = new Sitemesh3RenderViewMutator()
    View innerView = Mock(View)

    GrailsSiteMeshView siteMeshView() {
        new GrailsSiteMeshView(innerView, Mock(ContentProcessor),
                Mock(DecoratorSelector<SiteMeshContext>), Mock(ServletContext), Mock(ViewResolver))
    }

    void 'unwraps the SiteMesh view for partial renders without an explicit layout'() {
        when:
        View result = mutator.mutateView(false, '/book/_details', Locale.ENGLISH, siteMeshView())

        then: 'render template: partials are not decorated'
        result.is(innerView)
    }

    void 'keeps the SiteMesh view when an explicit layout was requested'() {
        given:
        GrailsSiteMeshView wrapped = siteMeshView()

        when:
        View result = mutator.mutateView(true, '/book/_details', Locale.ENGLISH, wrapped)

        then: 'render template: x, layout: y is still decorated'
        result.is(wrapped)
    }

    void 'passes through views that are not SiteMesh-wrapped'() {
        given:
        View plain = Mock(View)

        when:
        View result = mutator.mutateView(false, '/book/_details', Locale.ENGLISH, plain)

        then:
        result.is(plain)
    }
}
