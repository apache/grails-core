/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.encoder.impl

import org.grails.encoder.Decoder
import org.grails.encoder.Encoder
import org.grails.encoder.impl.StandaloneCodecLookup
import spock.lang.Specification

class BasicCodecLookupSpec extends Specification {

    def "should support dynamic chained codecs"() {
        given:
            StandaloneCodecLookup codecLookup=new StandaloneCodecLookup()
            codecLookup.afterPropertiesSet()
            Encoder encoder = codecLookup.lookupEncoder("html,js")
            Decoder decoder = codecLookup.lookupDecoder("html,js")
        expect:
            encoder != null
            decoder != null
            encoder.encode("<1>Hello;") == "\\u0026lt\\u003b1\\u0026gt\\u003bHello\\u003b"
            decoder.decode("\\u0026lt\\u003b1\\u0026gt\\u003bHello\\u003b") == "<1>Hello;"
    }    

}
