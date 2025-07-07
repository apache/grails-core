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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration
class EndToEndSpec extends ContainerGebSpec {

    def 'simple layout'() {
        when:
        go('endToEnd/simpleLayout')

        then:
        pageSource == """<html><head><title>Decorated This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
<body><h1>Hello</h1>body text
</body></html>"""
    }

    def 'title in subtemplate'() {
        when:
        go('endToEnd/titleInSubtemplate')

        then:
        pageSource == """<html><head><title>Decorated This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
</head>
<body><h1>Hello</h1>body text
</body></html>"""
    }

    def 'multiple levels of layouts'() {
        when:
        go('endToEnd/multipleLevelsOfLayouts')

        then:
        pageSource == """<html><head><title>Decorated Base - Dialog - This is the title</title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8"></head>
<body><h1>Hello</h1><div id="base"><div id="dialog">body text</div></div>
</body></html>"""
    }

    def 'parameters'() {
        when:
        go('endToEnd/parameters')

        then:
        pageSource == """<html><head></head><body><h1>pageProperty: here!</h1></body></html>"""
    }

    def 'parameters with logic'() {
        when:
        go('endToEnd/parametersWithLogic')

        then:
        pageSource == "<html><head></head><body>good</body></html>"
    }

    def 'multiline title'() {
        when:
        go('endToEnd/multilineTitle')

        then:
        pageSource == """<html><head><title>Decorated 
    This is the title
    </title><meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    </head>
<body><h1>Hello</h1>body text
</body></html>"""
    }
}
