package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.*

@IgnoreIf({System.getenv('CI')})
class FormTagLibResourceTests extends Specification implements UrlMappingsUnitTest<TestFormTagUrlMappings> {


    def testResourceSave() {
        when:
        def template = '<g:form resource="book" action="save"/>'
        String output = applyTemplate(template)

        then:
        output =='<form action="/books" method="post" ></form>'
    }

    def testResourceUpdate() {
        when:
        def template = '<g:form resource="book" action="update" id="1"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1" method="post" ><input type="hidden" name="_method" value="PUT" id="_method" /></form>'
    }

    def testResourceUpdateIdInParams() {
        when:
        def template = '<g:form resource="book" action="update" params="[id:1]"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1" method="post" ><input type="hidden" name="_method" value="PUT" id="_method" /></form>'
    }

    def testResourcePatch() {
        when:
        def template = '<g:form resource="book" action="patch" id="1"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1" method="post" ><input type="hidden" name="_method" value="PATCH" id="_method" /></form>'
    }

    def testResourcePatchIdInParams() {
        when:
        def template = '<g:form resource="book" action="patch" params="[id:1]"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1" method="post" ><input type="hidden" name="_method" value="PATCH" id="_method" /></form>'
    }

    def testResourceNestedSave() {
        when:
        def template = '<g:form resource="book/author" action="save" params="[bookId:1]"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1/authors" method="post" ></form>'
    }

    def testResourceNestedUpdate() {
        // We'd really like to suppoer this format <g:form resource="book/author" action="update" id="2" bookId="1"/>
        // but the form tag limits the set of attributes it hands to the linkGenerator and the dynamic parameters like 'bookId' get filtered out
        // instead we make do with putting bookId in the params attribute
        when:
        def template = '<g:form resource="book/author" action="update" id="2" params="[bookId:1]"/>'
        String output = applyTemplate(template)

        then:
        output =='<form action="/books/1/authors/2" method="post" ><input type="hidden" name="_method" value="PUT" id="_method" /></form>'
    }

    def testResourceNestedUpdateIdInParams() {
        when:
        def template = '<g:form resource="book/author" action="update" params="[bookId:1, id:2]"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1/authors/2" method="post" ><input type="hidden" name="_method" value="PUT" id="_method" /></form>'
    }

    def testResourceNestedPatch() {
        when:
        def template = '<g:form resource="book/author" action="patch" id="2" params="[bookId:1]"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1/authors/2" method="post" ><input type="hidden" name="_method" value="PATCH" id="_method" /></form>'
    }

    void testResourceNestedPatchIdInParams() {
        when:
        def template = '<g:form resource="book/author" action="patch" params="[bookId:1, id:2]"/>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/books/1/authors/2" method="post" ><input type="hidden" name="_method" value="PATCH" id="_method" /></form>'
    }


}

@Artefact("UrlMappings")
class TestFormTagUrlMappings {

    static mappings = {
        "/books"(resources:"book") {
            "/authors"(resources:"author")
        }
    }

}


