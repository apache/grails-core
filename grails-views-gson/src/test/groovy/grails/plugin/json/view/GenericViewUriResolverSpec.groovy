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
package grails.plugin.json.view

import grails.views.resolve.GenericViewUriResolver
import spock.lang.Specification

/**
 * Created by graemerocher on 25/08/15.
 */
class GenericViewUriResolverSpec extends Specification{

    void "test resolve template URIs"() {
        given:
            def resolver = new GenericViewUriResolver(".gson")

        expect:
            resolver.resolveTemplateUri("foo", "bar") == '/foo/_bar.gson'
    }

    void "test resolve template URIs with a namespace"() {
        given:
            def resolver = new GenericViewUriResolver(".gson")

        expect:
            resolver.resolveTemplateUri("namespace", "foo", "bar") == '/namespace/foo/_bar.gson'
    }
}
