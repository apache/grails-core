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

package functionaltests.layout


import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class NoLayoutControllerSpec extends ContainerGebSpec {

    def 'no layout'() {
        when:
        go('/noLayout/index')

        then: "The title is correct"
        pageSource == """<html><head>
    <title>No Default Title</title>
</head>

<body>
    <p>No layout should be applied to this page.</p>

</body></html>"""
    }
}
