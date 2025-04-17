package org.grails.taglib.encoder

import spock.lang.Specification

class WithCodecHelperSpec extends Specification {
    def "should allow configuring all but one encoder with specified codec"() {
        when:
        def canonicalCodecInfo = WithCodecHelper.makeSettingsCanonical(codecInfo)
        then:
        with(canonicalCodecInfo) {
            taglib == 'raw'
            expression == 'html'
            scriptlet == 'html'
            staticparts == 'html'
            taglibdefault == 'html'
            _canonical_ == true
        }
        canonicalCodecInfo.size() == 6
        where:
        codecInfo << [[all: 'html', taglib:'raw'], [taglib:'raw', all:'html']]
    }

    def "should allow configuring one encoder with specified codec when others are none"() {
        given:
        def codecInfo = [taglib:'raw']
        when:
        def canonicalCodecInfo = WithCodecHelper.makeSettingsCanonical(codecInfo)
        then:
        with(canonicalCodecInfo) {
            taglib == 'raw'
            expression == null
            scriptlet == null
            staticparts == null
            taglibdefault == null
            _canonical_ == true
        }
        canonicalCodecInfo.size() == 2
    }

}
