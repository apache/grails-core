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
package grails.plugins.mail

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

/**
 * {@code text:newLine} is declared as a method rather than as a closure field.
 *
 * <p>Both forms dispatch, and this tag was not broken by compile-time tag resolution. It was converted
 * because declaring a tag as a closure is deprecated as of this release and now warns when compiled,
 * and the framework's own tag libraries should not trip a warning the framework introduces.
 *
 * <p>The tag had no test either way, which is why this exists: converting a published tag's signature
 * without one is how a working tag stops working unnoticed.
 */
class PlainTextMailTagLibSpec extends Specification implements TagLibUnitTest<PlainTextMailTagLib> {

    void 'the tag library declares the text namespace'() {
        expect:
        PlainTextMailTagLib.namespace == 'text'
    }

    void 'newLine renders a newline'() {
        expect:
        applyTemplate('<text:newLine/>') == '\n'
    }

    void 'newLine renders between surrounding content'() {
        expect:
        applyTemplate('a<text:newLine/>b') == 'a\nb'
    }
}
