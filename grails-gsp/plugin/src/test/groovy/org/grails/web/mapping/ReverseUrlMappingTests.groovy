package org.grails.web.mapping

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
class ReverseUrlMappingTests extends Specification implements UrlMappingsUnitTest<CustomUrlMappings> {


    def testLinkTagRendering() {
        when:
        def template1 = '<g:link controller="product" action="create" params="[mslug:mslug]">New Product</g:link>'
        String output = applyTemplate(template1, [mslug:"acme"])

        then:
        output == '<a href="/acme/product/create">New Product</a>'

        when:
        def template2 = '<g:link controller="controller_name" action="action_name" params="[mslug:mslug,nslug:nslug]">New Product</g:link>'
        output = applyTemplate(template2, [mslug:"acme",nslug:"Coyote"])

        then:
        output == '<a href="/controller_name/acme/action_name/Coyote">New Product</a>'

        when:
        def template3 = '<g:link controller="controller_name" action="action_name" params="[mslug:mslug,nslug:nslug,extra:extra]">New Product</g:link>'
        output = applyTemplate(template3, [mslug:"acme",nslug:"Coyote",extra:"RoadRunner"])

        then:
        output == '<a href="/controller_name/acme/action_name/Coyote?extra=RoadRunner">New Product</a>'

        when:
        def template4 = '<g:link controller="controller_name" action="action_name" params="[mslug:mslug,extra:extra,nslug:nslug]">New Product</g:link>'
        output = applyTemplate(template4, [mslug:"acme",nslug:"Coyote",extra:"RoadRunner"])

        then:
        output == '<a href="/controller_name/acme/action_name/Coyote?extra=RoadRunner">New Product</a>'

        when:
        def template5 = '<g:link controller="controller_name" action="action_name" params="[mslug:mslug,nslug:nslug,oslug:oslug]">New Product</g:link>'
        output = applyTemplate(template5, [mslug:"acme",nslug:"Coyote",oslug:"RoadRunner"])

        then:
        output == '<a href="/controller_name/acme/action_name/Coyote/RoadRunner">New Product</a>'

        when:
        def template6 = '<g:link mapping="myNamedMapping">List People</g:link>'
        output = applyTemplate(template6)

        then:
        output == '<a href="/people/list">List People</a>'

        when:
        def template7 = '<g:link mapping="myOtherNamedMapping" params="[lastName:\'Keenan\']">List People</g:link>'
        output = applyTemplate(template7)

        then:
        output == '<a href="/showPeople/Keenan">List People</a>'

        when:
        def template8 = '<g:link controller="namespaced" namespace="primary">Link To Primary</g:link>'
        output = applyTemplate(template8)

        then:
        output == '<a href="/invokePrimaryController">Link To Primary</a>'

        when:
        def template9 = '<g:link controller="namespaced" namespace="secondary">Link To Secondary</g:link>'
        output = applyTemplate(template9)

        then:
        output == '<a href="/invokeSecondaryController">Link To Secondary</a>'

        when:
        def template10 = '<g:link controller="namespaced">Link To Non Namespaced</g:link>'
        output = applyTemplate(template10)

        then:
        output == '<a href="/nonNamespacedController">Link To Non Namespaced</a>'
    }

    def testPaginateWithNamedUrlMapping() {
        when:
        def template = '<g:paginate mapping="showBooks" total="15" max="5" />'
        String output = applyTemplate(template)

        then:
        output == '<span class="currentStep">1</span><a href="/showSomeBooks?offset=5&amp;max=5" class="step">2</a><a href="/showSomeBooks?offset=10&amp;max=5" class="step">3</a><a href="/showSomeBooks?offset=5&amp;max=5" class="nextLink">Next</a>'
    }

    def testSortableColumnWithNamedUrlMapping() {
        when:
        webRequest.controllerName = 'book'

        def template1 = '<g:sortableColumn property="releaseDate" title="Release Date" mapping="showBooks2" />'
        String output = applyTemplate(template1)

        then:
        output == '<th class="sortable" ><a href="/showSomeOtherBooks?sort=releaseDate&amp;order=asc">Release Date</a></th>'

        when:
        def template2 = '<g:sortableColumn property="releaseDate" title="Release Date" mapping="showBooksWithAction" action="action_name"/>'
        output = applyTemplate(template2)

        then:
        output == '<th class="sortable" ><a href="/showSomeOtherBooks/action_name?sort=releaseDate&amp;order=asc">Release Date</a></th>'
    }

    def testSortableColumnWithNamespaceAttribute() {
        when:
        webRequest.controllerName = 'book'
        def template = '<g:sortableColumn property="id" title="ID" action="index" namespace="grails" />'
        String output = applyTemplate(template)

        then:
        output == '<th class="sortable" ><a href="/grails/book/index?sort=id&amp;order=asc">ID</a></th>'
    }
}

@Artefact("UrlMappings")
public class CustomUrlMappings {
    static mappings = {
        "/$mslug/$controller/$action/$id?" {}

        "/controller_name/$mslug/action_name/$nslug" {
            controller = "controller_name"
            action = "action_name"
        }

        "/controller_name/$mslug/action_name/$nslug/$oslug" {
            controller = "controller_name"
            action = "action_name"
        }

        name myNamedMapping: '/people/list' {
            controller = 'person'
            action = 'list'
        }

        name myOtherNamedMapping: "/showPeople/$lastName" {
            controller = 'person'
            action = 'byLastName'
        }

        name showBooks: '/showSomeBooks' {
            controller = 'book'
            action = 'list'
        }

        name showBooks2: '/showSomeOtherBooks' {
            controller = 'book'
            action = 'list'
        }

        name showBooksWithAction: "/showSomeOtherBooks/$action" {
            controller = 'book'
        }

        "/$namespace/$controller/$action?"()

        "/grails/$controller/$action?" {
            namespace = "grails"
        }

        "/invokePrimaryController" {
            controller = 'namespaced'
            namespace = 'primary'
        }

        "/invokeSecondaryController" {
            controller = 'namespaced'
            namespace = 'secondary'
        }

        "/nonNamespacedController/$action?" {
            controller = 'namespaced'
        }
    }
}

@Artefact("Controller")
class ProductController {
    def create = {}
    def save = {}
}
