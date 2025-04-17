package grails.artefact

import spock.lang.Specification

class TagLibraryTraitSpec extends Specification {

    void 'test that a class marked with @Artefact("TagLib") is enhanced with grails.artefact.TagLibrary'() {
        expect:
        TagLibrary.isAssignableFrom SomeTagLib
    }
    
    void 'test that a class marked with @Artefact("TagLibrary") is enhanced with grails.artefact.TagLibrary'() {
        expect:
            TagLibrary.isAssignableFrom SomeOtherTagLib
    }
}

@Artefact('TagLib')
class SomeTagLib {}

@Artefact('TagLibrary')
class SomeOtherTagLib {}

