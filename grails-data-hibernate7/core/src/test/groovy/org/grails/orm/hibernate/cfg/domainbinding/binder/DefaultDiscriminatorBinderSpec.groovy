package org.grails.orm.hibernate.cfg.domainbinding.binder

import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests for DefaultDiscriminatorBinder focusing on logic rather than Hibernate integration
 * since many Hibernate 7 classes are sealed and cannot be mocked.
 */
class DefaultDiscriminatorBinderSpec extends Specification {

    @Subject
    DefaultDiscriminatorBinder binder

    SimpleValueColumnBinder simpleValueColumnBinder = Mock()

    def setup() {
        binder = new DefaultDiscriminatorBinder(simpleValueColumnBinder)
    }

    def "test constructor sets dependencies correctly"() {
        expect:
        binder.simpleValueColumnBinder == simpleValueColumnBinder
    }
}
