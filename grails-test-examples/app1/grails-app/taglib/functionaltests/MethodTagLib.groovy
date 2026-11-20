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
package functionaltests
import grails.compiler.GrailsCompileStatic
import grails.gsp.Tag

@GrailsCompileStatic
class MethodTagLib {

    @Tag
    def implicitTag() {
        Map tagAttrs = (Map) propertyMissing('attrs')
        out << "${tagAttrs.blah} - implicit"
    }

    @Tag
    def typedTag(String blah) {
        out << "${blah} - typed"
    }

    @Tag
    def multiTypedTag(String first, String second) {
        out << "${first}-${second}"
    }

    def attrsMapTag(Map attrs) {
        out << "attrs-${attrs.blah}"
    }

    def closureBodyTag(Closure body) {
        out << "closure-${body.call()}"
    }

    @Tag
    def bodyTag() {
        Closure tagBody = (Closure) propertyMissing('body')
        out << "before-${tagBody?.call()}-after"
    }

    def formatDate(Date date) {
        date?.toString() ?: ''
    }

    def buildInternalUrl(String path) {
        "/internal${path}"
    }

    Closure legacyTag = { Map attrs ->
        out << "legacy-${attrs.blah}"
    }
}
