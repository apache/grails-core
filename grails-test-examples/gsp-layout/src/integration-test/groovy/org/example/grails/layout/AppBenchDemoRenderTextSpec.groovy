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
package org.example.grails.layout

import spock.lang.IgnoreIf
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport
import org.apache.grails.testing.http.client.bench.AppHttpBench

/**
 * App-level bench: controller HTML text render through the layout app stack.
 * Gated by {@code -PappBench=true}.
 */
@Integration
@IgnoreIf({ !AppHttpBench.enabled() })
class AppBenchDemoRenderTextSpec extends Specification implements HttpClientSupport {

    void 'bench GET /demo/renderText'() {
        when:
        AppHttpBench.measureAndWrite(
                'appbench.gsplayout.DemoRenderText.httpGet',
                AppHttpBench.outputPath('gsp-layout-appbench.json')
        ) {
            def response = http('/demo/renderText')
            response.assertStatus(200)
            response.assertContains('Hello World')
        }

        then:
        noExceptionThrown()
    }
}
