/* Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.taglib

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.ApplicationTagLib
import spock.lang.Specification

class ApplyCodecTagSpec extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    def "applyCodec tag should apply codecs to values"() {
        when:
            def output=applyTemplate('<g:applyCodec name="html">${"<script>"}</g:applyCodec>')
        then:
            output=='&lt;script&gt;'
    }

    def "applyCodec tag should apply codecs to scriptlets"() {
        when:
            def output=applyTemplate('<g:applyCodec name="html"><%= "<script>" %></g:applyCodec>')
        then:
            output=='&lt;script&gt;'
    }

    def "applyCodec tag should not re-apply codecs"() {
        when:
            def output=applyTemplate('<g:applyCodec name="html"><g:applyCodec name="html"><%= "<script>" %></g:applyCodec></g:applyCodec>')
        then:
            output=='&lt;script&gt;'
    }

    def "applyCodec tag should support changing staticParts codec"() {
        when:
            def output=applyTemplate('''<g:applyCodec staticPartsCodec="html"><script></g:applyCodec>''')
        then:
            output=='&lt;script&gt;'
    }

    def "encodeAs attribute should escape values"() {
        when:
            def output=applyTemplate('<g:formatBoolean boolean="${true}" true="${"<script>"}" false="false" encodeAs="html"/>')
        then:
            output=='&lt;script&gt;'
    }

    def "encodeAs attribute in taglib function call should escape values"() {
        when:
            def output=applyTemplate('${g.formatBoolean(boolean:true, true:"<script>", false:"false", encodeAs:"html")}')
        then:
            output=='&lt;script&gt;'
    }
}
