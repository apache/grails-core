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

package grails.web.mapping


import groovy.transform.CompileStatic
import org.grails.web.util.WebUtils
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
@CompileStatic
abstract class AbstractUrlMappingsSpec extends Specification {

    static final String CONTEXT_PATH = 'app-context'

    def setup() {
        WebUtils.clearGrailsWebRequest()
    }

    LinkGenerator getLinkGeneratorWithContextPath(Closure mappingsClosure) {
        LinkGeneratorFactory linkGeneratorFactory = new LinkGeneratorFactory()
        linkGeneratorFactory.contextPath = CONTEXT_PATH
        linkGeneratorFactory.create(mappingsClosure)
    }

    LinkGenerator getLinkGenerator(Closure mappingsClosure) {
        new LinkGeneratorFactory().create(mappingsClosure)
    }

    UrlMappings getUrlMappingsHolder(Closure mappingsClosure) {
        new UrlMappingsFactory().create(mappingsClosure)
    }
}
