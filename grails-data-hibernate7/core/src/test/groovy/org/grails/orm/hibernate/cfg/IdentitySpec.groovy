package org.grails.orm.hibernate.cfg

import spock.lang.Specification
import spock.lang.Unroll
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum

class IdentitySpec extends Specification {

    @Unroll
    def "test determineGeneratorName with generator=#generatorName and useSequence=#useSequence"() {
        given:
        Identity identity = new Identity(generator: generatorName)

        expect:
        identity.determineGeneratorName(useSequence) == expected

        where:
        generatorName | useSequence | expected
        'native'      | false       | 'native'
        'native'      | true        | 'sequence-identity'
        'identity'    | false       | 'identity'
        'identity'    | true        | 'identity'
        'sequence'    | true        | 'sequence'
        'increment'   | false       | 'increment'
        null          | false       | 'native'
        null          | true        | 'sequence-identity'
    }

    @Unroll
    def "test static determineGeneratorName with mappedId=#mappedIdPresent and useSequence=#useSequence"() {
        given:
        Identity identity = mappedIdPresent ? new Identity(generator: generatorName) : null

        expect:
        Identity.determineGeneratorName(identity, useSequence) == expected

        where:
        mappedIdPresent | generatorName | useSequence | expected
        true            | 'native'      | false       | 'native'
        true            | 'native'      | true        | 'sequence-identity'
        true            | 'uuid'        | false       | 'uuid'
        false           | null          | false       | 'native'
        false           | null          | true        | 'sequence-identity'
    }
}
