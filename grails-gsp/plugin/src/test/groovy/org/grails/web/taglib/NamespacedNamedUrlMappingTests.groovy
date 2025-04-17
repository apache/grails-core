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