/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.testing.web

import grails.core.GrailsControllerClass
import grails.web.UrlConverter
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import groovy.transform.CompileDynamic
import junit.framework.AssertionFailedError
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.UrlMappingsArtefactHandler
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.testing.ParameterizedGrailsUnitTest
import org.grails.web.mapping.UrlMappingsHolderFactoryBean
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo
import junit.framework.ComparisonFailure

import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertNotNull

trait UrlMappingsUnitTest<T> implements ParameterizedGrailsUnitTest<T>, GrailsWebUnitTest {

    public static final String KEY_EXCEPTION = 'exception'
    private final List<String> assertionKeys = ["controller", "action", "view"]

    Class[] getControllersToMock() {
        []
    }

    void configuredMockedControllers() {
        for(Class c : controllersToMock) {
            final GrailsControllerClass controllerArtefact = (GrailsControllerClass)grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, c)
            controllerArtefact.initialize()
            defineBeans {
                "$controllerArtefact.name"(c) { bean ->
                    bean.scope = 'prototype'
                    bean.autowire = true
                }
            }
        }
        getArtefactInstance()
    }

    /**
     * @return The {@link UrlMappingsHolder} bean
     */
    UrlMappingsHolder getUrlMappingsHolder() {
        applicationContext.getBean("grailsUrlMappingsHolder", UrlMappingsHolder)
    }

    /**
     * Maps a URI and returns the appropriate controller instance
     *
     * @param uri The URI to map
     * @return The controller instance
     */
    Object mapURI(String uri) {
        UrlMappingsHolder mappingsHolder = getUrlMappingsHolder()

        UrlMappingInfo[] mappingInfos = mappingsHolder.matchAll(uri, request.method)
        for (UrlMappingInfo info in mappingInfos) {
            def backupParams = new HashMap(webRequest.params)
            info.configure(webRequest)

            webRequest.params.putAll(backupParams)
            if (info.viewName == null && info.URI == null) {
                if(info instanceof GrailsControllerUrlMappingInfo) {
                    def controller = info.controllerClass
                    if (controller != null) {
                        return applicationContext.getBean(controller.name)
                    }
                }
            }
        }
    }

    private boolean checkController(String controller, boolean throwEx) {
        final controllerClass = getControllerClass(controller)
        if (!controllerClass && throwEx) {
            throw new AssertionFailedError("Url mapping assertion failed, '$controller' is not a valid controller")
        }
        return controllerClass != null
    }

    /**
     * asserts a controller exists for the specified name and url
     *
     * @param controller The controller name
     * @param url The url
     */
    void assertController(String controller) {
        checkController(controller, true)
    }

    /**
     * @param controller The controller name
     * @param url The url
     * @return true If a controller exists for the specified name and url
     */
    boolean verifyController(String controller) {
        checkController(controller, false)
    }

    private boolean checkAction(String controller, String action, boolean throwEx) {
        final controllerClass = getControllerClass(controller)
        boolean valid = controllerClass?.mapsToURI("/$controller/$action")
        if (!valid && throwEx) {
            throw new AssertionFailedError("Url mapping assertion failed, '$action' is not a valid action of controller '$controller'")
        }
        valid
    }

    /**
     * Asserts an action exists for the specified controller name, action name and url
     *
     * @param controller The controller name
     * @param action The action name
     */
    void assertAction(String controller, String action) {
        checkAction(controller, action, true)
    }

    /**
     * @param controller The controller name
     * @param action The action name
     * @return true If an action exists for the specified controller name and action name
     */
    boolean verifyAction(String controller, String action) {
        checkAction(controller, action, false)
    }

    private boolean checkView(String controller, String view, boolean throwEx) {
        def pathPattern =  ((controller) ? "$controller/" : "") + "${view}.gsp"
        if (!pathPattern.startsWith('/')) {
            pathPattern = "/$pathPattern"
        }
        GroovyPagesTemplateEngine templateEngine = applicationContext.getBean("groovyPagesTemplateEngine", GroovyPagesTemplateEngine)

        def t = templateEngine.createTemplate(pathPattern)
        if (!t && throwEx) {
            throw new AssertionFailedError(
                    (controller) ? "Url mapping assertion failed, '$view' is not a valid view of controller '$controller'" : "Url mapping assertion failed, '$view' is not a valid view")
        }
        t != null
    }

    /**
     * Asserts a view exists for the specified controller name and view name
     *
     * @param controller The controller name
     * @param view The view name
     */
    void assertView(String controller, String view) {
        checkView(controller, view, true)
    }

    /**
     *
     * @param controller The controller name
     * @param view The view name
     * @param url The url
     * @return true If a view exists for the specified controller and view
     */
    boolean verifyView(String controller, String view) {
        checkView(controller, view, false)
    }


    /**
     * Asserts a URL mapping maps to the specified controller, action, and optionally also parameters. Example:
     *
     * <pre>
     * <code>
     *           assertUrlMapping("/action1", controller: "grailsUrlMappingsTestCaseFake", action: "action1") {
     *              param1 = "value1"
     *              param2 = "value2"
     *           }
     * </code>
     * </pre>
     * @param assertions The assertions as named parameters
     * @param url The URL as a string
     * @param paramAssertions The parameters to assert defined in the body of the closure
     */
    void assertUrlMapping(Map<String, String> assertions, String url, Closure paramAssertions = null) {
        assertForwardUrlMapping(assertions, url, paramAssertions)
        if (assertions.controller && !(url instanceof Integer)) {
            assertReverseUrlMapping(assertions, url, paramAssertions)
        }
    }

    /**
     * Verifies a URL mapping maps to the specified controller, action, and optionally also parameters. Example:
     *
     * <pre>
     * <code>
     *           verifyUrlMapping("/action1", controller: "grailsUrlMappingsTestCaseFake", action: "action1") {
     *              param1 = "value1"
     *              param2 = "value2"
     *           }
     * </code>
     * </pre>
     * @param assertions The assertions as named parameters
     * @param url The URL as a string
     * @param paramAssertions The parameters to assert defined in the body of the closure
     *
     * @return True if the url matches the assertions
     */
    boolean verifyUrlMapping(Map<String, String> assertions, String url, Closure paramAssertions = null) {
        boolean returnValue = verifyForwardUrlMapping(assertions, url, paramAssertions)
        if (assertions.controller && !(url instanceof Integer)) {
            returnValue = returnValue && verifyReverseUrlMapping(assertions, url, paramAssertions)
        }
        returnValue
    }

    private boolean checkForwardUrlMapping(Map<String, Object> assertions, Object url, Closure paramAssertions, boolean throwEx) {

        UrlMappingsHolder mappingsHolder = getUrlMappingsHolder()
        if (assertions.action && !assertions.controller) {
            throw new IllegalArgumentException("Cannot assert action for url mapping without asserting controller")
        }

        if (assertions.controller) {
            if (!checkController((String)assertions.controller, throwEx)) {
                return false
            }
        }
        if (assertions.action) {
            if (!checkAction((String)assertions.controller, (String)assertions.action, throwEx)) {
                return false
            }
        }
        if (assertions.view) {
            if (!checkView((String) assertions.controller, (String) assertions.view, throwEx)) {
                return false
            }
        }

        List<UrlMappingInfo> mappingInfos
        if (url instanceof Integer) {
            mappingInfos = []
            def mapping
            if (assertions."$KEY_EXCEPTION") {
                mapping = mappingsHolder.matchStatusCode(url, assertions."$KEY_EXCEPTION" as Throwable)
            } else {
                mapping = mappingsHolder.matchStatusCode(url)
            }
            if (mapping) mappingInfos << mapping
        }
        else {
            mappingInfos = mappingsHolder.matchAll((String)url, request.method).toList()
        }

        if (mappingInfos.size() == 0) {
            if (throwEx) {
                throw new AssertionFailedError("url '$url' did not match any mappings")
            }   else {
                return false
            }
        }

        boolean returnVal = true

        def mappingMatched = mappingInfos.any {mapping ->
            mapping.configure(webRequest)
            for (key in assertionKeys) {
                if (assertions.containsKey(key)) {
                    String expected = (String)assertions[key]
                    String actual = mapping."${key}Name"

                    switch (key) {
                        case "controller":
                            if (actual && !getControllerClass(actual)) return false
                            break
                        case "view":
                            if (actual[0] == "/") actual = actual.substring(1)
                            if (expected[0] == "/") expected = expected.substring(1)
                            break
                        case "action":
                            if (key == "action" && actual == null) {
                                final controllerClass = getControllerClass(assertions.controller)
                                actual = controllerClass?.defaultAction
                            }
                            break
                    }

                    if (expected != actual) {
                        if (throwEx) {
                            throw new ComparisonFailure("Url mapping $key assertion for '$url' failed".toString(), expected, actual)
                        } else {
                            returnVal = false
                        }
                    }
                }
            }
            if (paramAssertions) {
                def params = [:]
                paramAssertions.delegate = params
                paramAssertions.resolveStrategy = Closure.DELEGATE_ONLY
                paramAssertions.call()
                params.each {name, value ->
                    String actual = mapping.parameters[name]
                    String expected = value

                    if (expected != actual) {
                        if (throwEx) {
                            throw new ComparisonFailure("Url mapping $name assertion for '$url' failed".toString(), expected, actual)
                        } else {
                            returnVal = false
                        }
                    }
                }
            }

            return true
        }

        if (!mappingMatched) throw new IllegalArgumentException("url '$url' did not match any mappings")

        returnVal
    }

    void assertForwardUrlMapping(Map<String, Object> assertions, Object url, Closure paramAssertions = null) {
        checkForwardUrlMapping(assertions, url, paramAssertions, true)
    }

    boolean verifyForwardUrlMapping(Map<String, Object> assertions, Object url, Closure paramAssertions = null) {
        checkForwardUrlMapping(assertions, url, paramAssertions, false)
    }


    private boolean checkReverseUrlMapping(Map<String, String> assertions, String url, Closure paramAssertions, boolean throwEx) {
        UrlMappingsHolder mappingsHolder = applicationContext.getBean("grailsUrlMappingsHolder", UrlMappingsHolder)
        UrlConverter urlConverter = applicationContext.getBean(UrlConverter.BEAN_NAME, UrlConverter)
        def controller = assertions.controller
        def action = assertions.action
        def method = assertions.method
        def plugin = assertions.plugin
        def namespace = assertions.namespace

        String convertedControllerName = null, convertedActionName = null

        if(controller) convertedControllerName = urlConverter.toUrlElement(controller) ?: controller
        if(action) convertedActionName = urlConverter.toUrlElement(action) ?: action

        def params = [:]
        if (paramAssertions) {
            paramAssertions.delegate = params
            paramAssertions.resolveStrategy = Closure.DELEGATE_ONLY
            paramAssertions.call()
        }
        UrlCreator urlCreator = mappingsHolder.getReverseMapping(controller, action, namespace, plugin, method, params)
        if (urlCreator == null) {
            if (throwEx) {
                throw new AssertionFailedError("could not create reverse mapping of '$url' for {controller = $controller, action = $action, params = $params}")
            } else {
                return false
            }
        }
        String createdUrl = urlCreator.createRelativeURL(convertedControllerName, convertedActionName, params, "UTF-8")

        if (url != createdUrl) {
            if (throwEx) {
                throw new ComparisonFailure("reverse mapping assertion for {controller = $controller, action = $action, params = $params}", url, createdUrl)
            } else {
                return false
            }
        }
        true
    }

    /**
     * Asserts the given controller and action produce the given reverse URL mapping
     *
     * <pre>
     * <code>
     *           assertReverseUrlMapping("/action1", controller: "grailsUrlMappingsTestCaseFake", action: "action1")
     * </code>
     * </pre>
     * @param assertions The assertions as named parameters
     * @param url The URL as a string
     * @param paramAssertions The parameters to assert defined in the body of the closure
     */
    void assertReverseUrlMapping(Map<String, String> assertions, String url, Closure paramAssertions = null) {
        checkReverseUrlMapping(assertions, url, paramAssertions, true)
    }

    /**
     * Asserts the given controller and action produce the given reverse URL mapping
     *
     * <pre>
     * <code>
     *           verifyReverseUrlMapping("/action1", controller: "grailsUrlMappingsTestCaseFake", action: "action1")
     * </code>
     * </pre>
     * @param assertions The assertions as named parameters
     * @param url The URL as a string
     * @param paramAssertions The parameters to assert defined in the body of the closure
     *
     * @return True if the url matches the assertions
     */
    boolean verifyReverseUrlMapping(Map<String, String> assertions, String url, Closure paramAssertions = null) {
        checkReverseUrlMapping(assertions, url, paramAssertions, false)
    }

    GrailsControllerClass getControllerClass(String controller) {
        (GrailsControllerClass)grailsApplication.getArtefactByLogicalPropertyName(ControllerArtefactHandler.TYPE, controller)
    }

    @CompileDynamic
    void mockArtefact(Class<?> urlMappingsClass) {
        grailsApplication.addArtefact(UrlMappingsArtefactHandler.TYPE, urlMappingsClass)

        defineBeans {
            grailsUrlMappingsHolder(UrlMappingsHolderFactoryBean) {
                getDelegate().grailsApplication = grailsApplication
            }
        }
    }

    String getBeanName(Class<?> urlMappingsClass) {
        null
    }
}
