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

package functional.tests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
class AuthorControllerSpec extends ContainerGebSpec {

    void "Test list authors"() {
        when:"The home page is visited"
        go '/author/index'

        then:"The name is correct"
        title == "Author List"
    }

    void "Test save author"() {
        when:
        go "/author/create"
        $('form').name = "Stephen King"
        $('input.save').click()

        then:"The author is correct"
        title == "Show Author"
        $('li.fieldcontain div').text() == 'Stephen King'

    }
}
