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
package org.apache.grails.benchmarks.urlmappings

import grails.web.mapping.UrlMapping
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder

class UrlMappingsFixture {

    static DefaultUrlMappingsHolder createHolder() {
        DefaultUrlMappingEvaluator evaluator = new DefaultUrlMappingEvaluator(null)
        // These MUST be double-quoted GStrings, exactly as a real UrlMappings.groovy is written.
        // The DSL captures variables by letting its delegate resolve $category and friends; with
        // single quotes they stay literal text, every mapping becomes a fixed path, and match()
        // silently returns null so the benchmark measures failed lookups.
        List<UrlMapping> mappings = evaluator.evaluateMappings {
            "/catalog/$category/$id"(controller: 'catalog', action: 'show')
            "/catalog/search/$query"(controller: 'catalog', action: 'search')
            "/articles/$year/$month?/$slug"(controller: 'article', action: 'show')
            "/$controller/$action?/$id?"()
        }
        new DefaultUrlMappingsHolder(mappings)
    }
}
