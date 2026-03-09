package org.apache.grails.testing.http.client.utils

import spock.lang.Specification

class XmlUtilsSpec extends Specification {

    void 'toXml builds expected XML using markup DSL'() {
        when:
        def xml = XmlUtils.toXml {
            product {
                id('1')
                name('Widget')
            }
        }

        then:
        xml.contains('<product>')
        xml.contains('<id>1</id>')
        xml.contains('<name>Widget</name>')
    }
}
