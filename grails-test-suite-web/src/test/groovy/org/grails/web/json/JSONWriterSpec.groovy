/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.json

import groovy.transform.CompileStatic
import spock.lang.Issue
import spock.lang.Specification

class JSONWriterSpec extends Specification {
    
    @Issue('GRAILS-10823')
    void 'Test rendering a forward slash'() {
        given:
        def writer = new StringWriter()
        def jsonWriter = new JSONWriter(writer)
        
        when:
        jsonWriter.object().key('namespace').value('alpha/beta').endObject()
        
        then:
        '{"namespace":"alpha/beta"}' == writer.toString()
    }

    void 'should handle nulls'() {
        given:
        def writer = new StringWriter()
        def jsonWriter = new JSONWriter(writer)
        when:
        jsonWriter.array()
        writeNumber(jsonWriter, null)
        writeObject(jsonWriter, null)
        jsonWriter.endArray()
        then:
        '[{"key":null},{"key":null}]' == writer.toString()
    }

    @CompileStatic
    private writeNumber(JSONWriter jsonWriter, Number n) {
        jsonWriter.object().key('key').value(n).endObject()
    }

    @CompileStatic
    private writeObject(JSONWriter jsonWriter, Object o) {
        jsonWriter.object().key('key').value(o).endObject()
    }
}
