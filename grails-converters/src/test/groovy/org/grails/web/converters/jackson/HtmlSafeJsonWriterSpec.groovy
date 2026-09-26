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
package org.grails.web.converters.jackson

import spock.lang.Specification

/**
 * {@link HtmlSafeJsonWriter} escapes {@code </} and the line and paragraph separators, as Grails has always
 * escaped them in JSON strings.
 */
class HtmlSafeJsonWriterSpec extends Specification {

    void "#description"() {
        given:
        def out = new StringWriter()
        def writer = new HtmlSafeJsonWriter(out)

        when:
        chunks.each { writer.write(it) }
        writer.close()

        then:
        out.toString() == expected

        where:
        description                                 | chunks             || expected
        'escapes </'                                | ['"</script>"']    || '"<\\u002fscript>"'
        'escapes </ split across two writes'        | ['"a<', '/b>"']    || '"a<\\u002fb>"'
        'keeps < that does not start </'            | ['"a<b"', '"<<c"'] || '"a<b""<<c"'
        'keeps a < ending one write and not </'     | ['"a<', 'b"']      || '"a<b"'
        'escapes the line and paragraph separators' | ['"x\u2028y\u2029z"']        || '"x\\u2028y\\u2029z"'
        'writes a trailing < on close'              | ['a<']             || 'a<'
    }
}
