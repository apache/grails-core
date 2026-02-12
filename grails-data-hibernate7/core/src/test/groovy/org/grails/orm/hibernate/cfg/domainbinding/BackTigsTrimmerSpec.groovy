package org.grails.orm.hibernate.cfg.domainbinding

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.BackTigsTrimmer

class BackTigsTrimmerSpec extends Specification {

    @Unroll
    def "Test that trimBackTigs correctly trims '#input' to '#expected'"() {
        given:
        def trimmer = new BackTigsTrimmer()

        when:
        def result = trimmer.trimBackTigs(input)

        then:
        result == expected

        where:
        input      | expected
        '`table`'  | 'table'
        'table'    | 'table'
        '`table'   | '`table'
        'table`'   | 'table`'
        '``'       | ''
        '`'        | '`'
        ''         | ''
        null       | null
    }
}
