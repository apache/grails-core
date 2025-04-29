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
package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class NamespacedNamedUrlMappingTests extends Specification implements UrlMappingsUnitTest<TestUrlMappings> {

    def testLinkAttributes() {
        when:
        def template = '<link:productDetail attrs="[class: \'fancy\']" productName="licorice" flavor="strawberry">Strawberry Licorice</link:productDetail>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/showProduct/licorice/strawberry" class="fancy">Strawberry Licorice</a>'
    }

    def testLinkAttributesPlusAdditionalRequestParameters() {
        when:
        def template = '<link:productDetail attrs="[class: \'fancy\']" packaging="boxed" size="large" productName="licorice" flavor="strawberry">Strawberry Licorice</link:productDetail>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/showProduct/licorice/strawberry?packaging=boxed&amp;size=large" class="fancy">Strawberry Licorice</a>'
    }

    def testNoParameters() {
        when:
        def template = '<link:productListing>Product Listing</link:productListing>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/products/list">Product Listing</a>'
    }

    def testAttributeForParameter() {
        when:
        def template = '<link:productDetail productName="Scotch">Scotch Details</link:productDetail>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/showProduct/Scotch">Scotch Details</a>'
    }

    def testMultipleAttributesForParameters() {
        when:
        def template = '<link:productDetail productName="licorice" flavor="strawberry">Strawberry Licorice</link:productDetail>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/showProduct/licorice/strawberry">Strawberry Licorice</a>'
    }
}


@Artefact('UrlMappings')
class TestUrlMappings {
    static mappings = {
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }

        name productListing: "/products/list" {
            controller = "product"
            action = "list"
        }

        name productDetail: "/showProduct/$productName/$flavor?" {
            controller = "product"
            action = "show"
        }
    }
}