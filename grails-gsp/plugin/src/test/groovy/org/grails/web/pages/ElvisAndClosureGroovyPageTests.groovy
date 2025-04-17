package org.grails.web.pages

import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.Test

class ElvisAndClosureGroovyPageTests extends AbstractGrailsTagTests {

    @Test
    void testElvisOperaturUsedWithClosure() {

        def template = '<g:set var="finder" value="${myList.find{ it == \'a\' } ?: \'default\'}"/>${finder}'

        def content = applyTemplate(template, [myList:['b','d','a', 'c']])

        assert content == 'a'
    }
}

