package org.grails.orm.hibernate.cfg.domainbinding

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Specification for the BackticksRemover utility.
 *
 * Verifies that it correctly removes surrounding backticks from a string
 * while handling various edge cases.
 */
class BackticksRemoverSpec extends Specification {

    @Subject
    BackticksRemover remover = new BackticksRemover()

    @Unroll
    def "should correctly process input string '#input'"() {
        when: "the remover is called with the input string"
        def result = remover.apply(input)

        then: "the output matches the expected result"
        result == expectedOutput

        where:
        input                  | expectedOutput         | _ // Description for clarity in test reports
        '`quoted_name`'        | 'quoted_name'          | "Removes surrounding backticks"
        'unquotedName'         | 'unquotedName'         | "Does not change a string with no backticks"
        '`malformed'            | '`malformed'            | "Does not change a string with only a leading backtick"
        'malformed`'            | 'malformed`'            | "Does not change a string with only a trailing backtick"
        'with`middle`ticks'    | 'with`middle`ticks'    | "Does not change a string with middle backticks"
        '``'                   | ''                     | "Returns an empty string for just two backticks"
        ''                     | ''                     | "Does not change an empty string"
        null                   | null                   | "Returns null for a null input"
        '`'                    | '`'                    | "Does not change a single backtick string"
    }

}