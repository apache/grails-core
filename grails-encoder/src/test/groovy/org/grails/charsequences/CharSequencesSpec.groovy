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
package org.grails.charsequences

import spock.lang.Specification

class CharSequencesSpec extends Specification {
    def "should support writing String instance to writer"() {
        given:
            StringWriter writer=new StringWriter()
        when:
            CharSequences.writeCharSequence(writer, input)
        then:
            writer.toString()==input
        where:
            input << ['Hello world','','1','12','123']
    }

    def "should support writing StringBuilder instance to writer"() {
        given:
            StringWriter writer=new StringWriter()
        when:
            StringBuilder sb=new StringBuilder()
            sb.append(input)
            CharSequences.writeCharSequence(writer, sb)
        then:
            writer.toString()==input
        where:
            input << ['Hello world','','1','12','123']
    }

    def "should support writing StringBuffer instance to writer"() {
        given:
            StringWriter writer=new StringWriter()
        when:
            StringBuffer sb=new StringBuffer()
            sb.append(input)
            CharSequences.writeCharSequence(writer, sb)
        then:
            writer.toString()==input
        where:
            input << ['Hello world','','1','12','123']
    }
    
    def "should support writing CharArrayAccessible instance to writer"() {
        given:
            StringWriter writer=new StringWriter()
        when:
            CharArrayAccessible charArrayAccessible = new CharArrayCharSequence(input.toCharArray(), 0, input.length())
            CharSequences.writeCharSequence(writer, charArrayAccessible)
        then:
            writer.toString()==input
        where:
            input << ['Hello world','','1','12','123']
    }
    
    def "should support writing CharSequence instance to writer"() {
        given:
            StringWriter writer=new StringWriter()
        when:
            CharSequence charSequence = new CustomCharSequence(input)
            CharSequences.writeCharSequence(writer, charSequence)
        then:
            writer.toString()==input
        where:
            input << ['Hello world','','1','12','123']
    }
    
    class CustomCharSequence implements CharSequence {
        String source
        
        CustomCharSequence(String source) {
            this.source = source
        }
        
        
        @Override
        public int length() {
            return source.length();
        }

        @Override
        public char charAt(int index) {
            return source.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return source.subSequence(start, end);
        }
    }
}
