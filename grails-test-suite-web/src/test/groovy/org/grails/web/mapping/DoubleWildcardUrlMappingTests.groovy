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
package org.grails.web.mapping

import grails.testing.web.UrlMappingsUnitTest
import grails.web.mapping.UrlMappingInfo
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class DoubleWildcardUrlMappingTests extends Specification implements UrlMappingsUnitTest<UrlMappings> {



    void testDoubleWildcardWithMatchingController() {
        given:
        def holder = urlMappingsHolder
        assert webRequest

        when:
        def infos = holder.matchAll('/someOther/index')

        then:
        assert infos

        when:
        UrlMappingInfo info = infos[0]
        info.configure webRequest

        then:
        'someOther' == info.getControllerName()

        when:
        infos = holder.matchAll('/someOther/1+2')

        then:
        assert infos

        when:
        info = infos[0]
        info.configure webRequest

        then:
        'someOther' == info.getControllerName()
        '1+2' == info.getActionName()

    }

    void testDoubleWildcardInParam() {

        given:
        def holder = urlMappingsHolder
        assert webRequest

        when:
        request.addParameter("d", "1")
        def infos = holder.matchAll("/cow/wiki/show/2/doc/?d=1")
        infos[0].configure(webRequest)
        def c = new DoubleWildCardController()

        then:
        "doc/" == c.params.path
        "1" == c.params.d
    }

    void testDoubleWildCardMappingWithSuffix() {

        given:
        def m = urlMappingsHolder.urlMappings.find { it.controllerName == 'userImage'}
        assert m

        when:
        def info = m.match("/images/foo.jpg")
        info?.configure(webRequest)
        //assert !mappings[1].match("/stuff/image")
        then:
        info
        "userImage" == info.controllerName
        "download" == info.actionName


        when:
        "foo" == info.params.image

        info = m.match("/images/foo/bar.jpg")
        //assert !mappings[1].match("/stuff/image")
        info?.configure(webRequest)

        then:
        info
        "userImage" == info.controllerName
        "download" == info.actionName
        "foo/bar" == info.params.image
    }

    void testDoubleWildCardMatching() {

        given:
        def m = urlMappingsHolder.urlMappings.find { it.toString().startsWith("/components")}
        def m2 = urlMappingsHolder.urlMappings.find { it.toString().startsWith("/stuff")}
        assert m

        when:
        def info = m.match("/components/image")
        info.configure(webRequest)

        then:
        "components" == info.controllerName
        "image" == info.actionName
        !webRequest.params.path

        when:
        info = m.match("/components/image/")
        info.configure(webRequest)

        then:
        "components" == info.controllerName
        "image" == info.actionName
        '' == webRequest.params.path

        when:
        info = m.match("/components/image/foo.bar")
        assert info
        info.configure(webRequest)

        then:
        "components" == info.controllerName
        "image" == info.actionName
        'foo.bar' == webRequest.params.path

        when:
        info = m.match('/components/image/asdf/foo.bar')
        assert info
        info.configure(webRequest)

        then:
        "components" == info.controllerName
        "image" == info.actionName
        'asdf/foo.bar' == webRequest.params.path

        !m2.match("/stuff/image")

        when:
        info = m2.match("/stuff/image/foo.bar")
        assert info
        info.configure(webRequest)

        then:
        "components" == info.controllerName
        "image" == info.actionName
        'foo.bar' == webRequest.params.path
    }

    static class UrlMappings {
        static mappings = {
            "/components/image/$path**?" {
                controller = "components"
                action = "image"
            }
            "/stuff/image/$path**" {
                controller = "components"
                action = "image"
            }

            "/cow/$controller/$action?/$id?/$path**?"()

            "/$controller/$action?/$id?"()

            "/images/$image**.jpg" (controller: 'userImage', action: 'download')
            "/**"{
                controller = 'doubleWildcard'
                action = 'otherAction'
            }
            "500"(view:'/error')
        }
    }



}

class SomeOtherController {
    def index() {}
}

@grails.artefact.Artefact('Controller')
class DoubleWildCardController {
    def index(){ params.path }
    def otherAction() {}
}
