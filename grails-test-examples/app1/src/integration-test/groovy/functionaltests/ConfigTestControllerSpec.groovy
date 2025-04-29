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

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
@Rollback
class ConfigTestControllerSpec extends ContainerGebSpec {

    void "Test that configuration properties are correctly read"() {
        when:"When evaluting configuration values"
        go '/configTest/index'

        then:"The values are correct"
        $('div', 0).text() == 'test'
        $('div', 1).text() == '1'
        $('div', 2).text() == 'test'
        $('div', 3).text() == '1'
        $('div', 4).text() == 'test'
        $('div', 5).text() == '1'
        String text = $('div', 6).text()
        text.contains('baz=1')
        text.contains('bax=2')
        text.contains('bar=test')
    }
}
