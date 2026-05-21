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

package org.grails.scaffolding.registry.input

import grails.web.mapping.LinkGenerator
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.scaffolding.ClosureCapture
import org.grails.scaffolding.ClosureCaptureSpecification
import org.grails.scaffolding.model.property.DomainProperty
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Subject

@Subject(BidirectionalToManyInputRenderer)
class BidirectionalToManyInputRendererSpec extends ClosureCaptureSpecification {

    @Shared
    BidirectionalToManyInputRenderer renderer

    void setup() {
        renderer = new BidirectionalToManyInputRenderer(Mock(LinkGenerator))
    }

    void "test supports"() {
        given:
        DomainProperty property

        when:
        property = Mock(DomainProperty) {
            1 * getPersistentProperty() >> Mock(ToMany) {
                1 * isBidirectional() >> true
            }
        }

        then:
        renderer.supports(property)
    }

    @PendingFeature
    void "test render"() {
        given:
        DomainProperty property
        renderer.linkGenerator = Mock(LinkGenerator) {
            1 * link([resource: Calendar, action: "create", params: ["timeZone.id": ""]]) >> "http://www.google.com"
        }

        when:
        property = Mock(DomainProperty) {
            1 * getRootBeanType() >> TimeZone
            2 * getAssociatedType() >> Calendar
        }
        ClosureCapture closureCapture = getClosureCapture(renderer.renderInput([required: "", readonly: ""], property))

        then:
        closureCapture.calls[0].name == "a"
        closureCapture.calls[0].args[0] == "Add Calendar"
        closureCapture.calls[0].args[1] == [href: "http://www.google.com"]
    }
}
