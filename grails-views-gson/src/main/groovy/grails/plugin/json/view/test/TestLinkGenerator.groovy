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

package grails.plugin.json.view.test

import grails.util.GrailsNameUtils
import grails.web.mapping.LinkGenerator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.util.WebUtils

/**
 * A test link generator
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
class TestLinkGenerator implements LinkGenerator {

    final MappingContext mappingContext

    TestLinkGenerator(MappingContext mappingContext) {
        this.mappingContext = mappingContext
    }

    @Override
    String resource(Map params) {
        return null
    }

    @Override
    String link(Map params, String encoding = "UTF-8") {
        final boolean absolute = params.absolute ? true : false
        final String base = absolute ? "${serverBaseURL}${contextPath}" : contextPath
        StringBuilder url = new StringBuilder(base)
        if (params.uri) {
            url.append(params.uri.toString())
        } else {
            Map urlObject = params
            if (params.url instanceof Map) {
                urlObject = (Map) params.url
            }


            def resource = urlObject.resource
            if (resource != null && !(resource instanceof CharSequence)) {
                Class clazz
                if (resource instanceof Class) {
                    clazz = resource
                } else {
                    clazz = mappingContext.getProxyFactory().getProxiedClass(resource)
                }
                resource = GrailsNameUtils.getPropertyName(clazz)
            }
            String controller = resource ?: urlObject.controller
            if (controller) {
                url.append("/$controller")

                if (urlObject.action) {
                    url.append("/$urlObject.action")
                }
                if (urlObject.id) {
                    url.append("/$urlObject.id")
                } else if (urlObject.resource?.hasProperty('id')) {
                    appendObjectId(url, urlObject)
                }

            }
        }
        if (params.params instanceof Map) {
            url.append WebUtils.toQueryString((Map) params.params, encoding)
        }
        return url.toString()
    }

    @CompileDynamic
    protected StringBuilder appendObjectId(StringBuilder url, Map urlObject) {
        def obj = urlObject.resource
        if (obj?.id) {
            url.append("/${obj.id}")
        }
    }

    @Override
    String getContextPath() {
        return ''
    }

    @Override
    String getServerBaseURL() {
        return "http://localhost:8080"
    }
}
