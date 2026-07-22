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
package grails.plugin.geb

import java.util.function.Supplier

import groovy.transform.CompileStatic

import geb.Browser
import geb.ConfigurationLoader
import geb.direct.PlaywrightDriver
import geb.spock.SpockGebTestManagerBuilder
import geb.test.GebTestManager
import org.spockframework.runtime.extension.IMethodInvocation

import grails.util.Holders

@CompileStatic
class PlaywrightBrowserHolder {

    Browser browser
    GebTestManager testManager

    void stop() {
        browser?.quit()
        browser = null
        testManager = null
    }

    void initialize() {
        stop()

        def configuration = new ConfigurationLoader().conf
        configuration.cacheDriver = false
        configuration.quitDriverOnBrowserReset = false
        if (!configuration.driverConf) {
            configuration.driverConf = PlaywrightDriver.config {
                browserType = 'chromium'
                headless = true
            }
        }
        configuration.baseUrl = 'http://localhost'

        browser = new Browser(configuration)
        browser.driver
        testManager = new SpockGebTestManagerBuilder()
                .withBrowserCreator(new Supplier<Browser>() {
                    @Override
                    Browser get() {
                        browser
                    }
                })
                .build()
    }

    void setupBrowserUrl(IMethodInvocation methodInvocation) {
        int serverPort = findServerPort(methodInvocation)
        String contextPath = findServerContextPath()
        String baseUrl = "http://localhost:$serverPort"
        if (contextPath && contextPath != '/') {
            if (!contextPath.startsWith('/')) {
                contextPath = "/$contextPath"
            }
            baseUrl += contextPath.endsWith('/') ? contextPath : "$contextPath/"
        }
        browser.baseUrl = baseUrl
    }

    private static int findServerPort(IMethodInvocation methodInvocation) {
        try {
            return methodInvocation.instance.metaClass.getProperty(methodInvocation.instance, 'serverPort') as int
        } catch (ignored) {
            throw new IllegalStateException(
                    'The `serverPort` property that should have been injected by the @Integration annotation was not found.'
            )
        }
    }

    private static String findServerContextPath() {
        try {
            return Holders.findApplicationContext()?.environment?.getProperty('server.servlet.context-path', '/') ?: '/'
        } catch (ignored) {
            return '/'
        }
    }
}
