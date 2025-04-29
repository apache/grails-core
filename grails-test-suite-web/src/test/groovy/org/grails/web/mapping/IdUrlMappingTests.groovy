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
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class IdUrlMappingTests extends Specification implements UrlMappingsUnitTest<UrlMappings> {


    void testIdInURL() {

        when:
        def infos = urlMappingsHolder.matchAll("/emailConfirmation/foo")
        assert infos
        infos[0].configure(webRequest)

        def c = new EmailConfirmationController()

        then:
        "foo" == c.params.id
    }

    void testIdInParam() {
        when:
        assert webRequest

        def infos = urlMappingsHolder.matchAll("/emailConfirmation/foo")
        assert infos

        infos[0].configure(webRequest)

        def c = new EmailConfirmationController()

        then:
        "foo" == c.params.id
    }

    void testMappingWithUrlEncodedCharsInId() {
        when:
        assert webRequest

        def infos = urlMappingsHolder.matchAll("/emailConfirmation/my%20foo")
        assert infos

        infos[0].configure(webRequest)

        def c = new EmailConfirmationController()

        then:
        "my foo" == c.params.id

        when:
        infos = urlMappingsHolder.matchAll("/emailConfirmation/my%2Ffoo")
        assert infos
        infos[0].configure(webRequest)

        c = new EmailConfirmationController()

        then:
        "my/foo" == c.params.id
    }

    static class UrlMappings {
        static mappings = {
            "/emailConfirmation/$id?" {
                controller = "emailConfirmation"
                action = "index"
            }
            "/$id?" {
                controller = "content"
                action = "index"
            }
        }
    }
}
@grails.artefact.Artefact('Controller')
class EmailConfirmationController {
    def index() {
        [result: "ID = " + params.id]
    }
}
class ContentController {
    def index() {}
}