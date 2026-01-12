package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.MappingException
import spock.lang.Specification
import spock.lang.Unroll

class CascadeBehaviorEnumSpec extends Specification {

    @Unroll
    void "test isSaveUpdate for #behavior"() {
        expect:
        behavior.isSaveUpdate() == expected

        where:
        behavior                         | expected
        CascadeBehavior.ALL              | true
        CascadeBehavior.ALL_DELETE_ORPHAN | true
        CascadeBehavior.SAVE_UPDATE      | true
        CascadeBehavior.MERGE            | false
        CascadeBehavior.PERSIST          | false
        CascadeBehavior.DELETE           | false
        CascadeBehavior.LOCK             | false
        CascadeBehavior.EVICT            | false
        CascadeBehavior.REPLICATE        | false
        CascadeBehavior.NONE             | false
    }

    @Unroll
    void "test fromString for #value"() {
        expect:
        CascadeBehavior.fromString(value) == expected

        where:
        value               | expected
        "all"               | CascadeBehavior.ALL
        "all-delete-orphan" | CascadeBehavior.ALL_DELETE_ORPHAN
        "save-update"       | CascadeBehavior.SAVE_UPDATE
        "persist,merge"     | CascadeBehavior.SAVE_UPDATE
        "merge"             | CascadeBehavior.MERGE
        "persist"           | CascadeBehavior.PERSIST
        "none"              | CascadeBehavior.NONE
    }

    void "test fromString with invalid value"() {
        when:
        CascadeBehavior.fromString("invalid")

        then:
        thrown(MappingException)
    }
}
