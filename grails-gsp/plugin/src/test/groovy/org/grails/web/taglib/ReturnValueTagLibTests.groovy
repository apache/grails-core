package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class ReturnValueTagLibTests extends Specification implements TagLibUnitTest<ReturnValueTagLib> {

    void testReturnValue() {
        expect:
        applyTemplate('${g.numberretval()}') == '123'
        applyTemplate('<g:numberretval />') == 'this output should be discarded in function call.123'
        applyTemplate('${(g.numberretval()==123 && g.numberretval() instanceof Integer)}<g:numberretval />') == 'truethis output should be discarded in function call.123'
        applyTemplate('${(numberretval()==123 && numberretval() instanceof Integer)}<g:numberretval />') == 'truethis output should be discarded in function call.123'
    }

    void testOutputNotUsed() {
        expect:
        applyTemplate('${g.outputnotused()}<g:outputnotused />') == ''
        applyTemplate('${outputnotused()}<g:outputnotused />') == ''
    }

    void testDiscardReturnValue() {
        expect:
        applyTemplate('${g.discardreturnvalue()}<g:discardreturnvalue />') == 'hellohello'
        applyTemplate('${discardreturnvalue()}<g:discardreturnvalue />') == 'hellohello'
    }
}

@Artefact('TagLib')
class ReturnValueTagLib {
    static returnObjectForTags = ['numberretval']

    Closure numberretval = { attrs ->
        // out shouldn't be used in returnObjectForTags tags, but we don't prevent it
        out << 'this output should be discarded in function call.'
        return 123
    }

    Closure outputnotused = { attrs -> return 'dontshowup' }

    Closure discardreturnvalue = { attrs ->
        out << 'hello'
        return 'dontshowup'
    }
}

