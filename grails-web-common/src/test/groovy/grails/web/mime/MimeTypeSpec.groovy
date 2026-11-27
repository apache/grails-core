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
package grails.web.mime

import spock.lang.Specification

class MimeTypeSpec extends Specification {

    void 'a media type is equal to one whose name differs only in case, as RFC 9110 compares them'() {
        given:
        def mixed = new MimeType('Application/JSON')

        expect:
        mixed == MimeType.JSON
        mixed.hashCode() == MimeType.JSON.hashCode()
        [(MimeType.JSON): 'json'][mixed] == 'json'
    }

    void 'media types with different versions are not equal'() {
        expect:
        new MimeType('application/vnd.acme+json;v=1') != new MimeType('application/vnd.acme+json;v=2')
    }
}
