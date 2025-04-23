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
package grails.plugin.geb.support.delegate

import geb.Browser
import geb.Page
import geb.js.JavascriptInterface
import geb.url.UrlFragment
import geb.webstorage.WebStorage
import grails.plugin.geb.ContainerGebSpec
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.SelfType
import org.openqa.selenium.WebDriver

import java.time.Duration

import static groovy.lang.Closure.DELEGATE_FIRST

/**
 * Handles delegation to the browser instance so that the Geb API can be used directly in the test.
 * <p>
 * As method parameter names are not available in the Geb artifacts we are delegating manually,
 * instead of using @Delegate AST transform, to get the best possible end user IDE support and user experience.
 *
 * @author Mattias Reichel
 * @since 4.2
 */
@CompileStatic
@SelfType(ContainerGebSpec)
trait BrowserDelegate {

    /**
     * Accessor to the Geb {@link geb.Browser} instance.
     */
    Browser getBrowser() {
        testManager.browser
    }

    /**
     * Delegates to {@link geb.Browser#getDriver()}.
     */
    WebDriver getDriver() {
        browser.driver
    }

    /**
     * Delegates to {@link geb.Browser#getCurrentUrl()}.
     */
    String getCurrentUrl() {
        browser.currentUrl
    }

    /**
     * Delegates to {@link geb.Browser#getPage()}.
     */
    Page getPage() {
        browser.page
    }

    /**
     * Delegates to {@link geb.Browser#at(Class)}.
     */
    <T extends Page> T at(Class<T> pageType) {
        browser.at(pageType)
    }

    /**
     * Delegates to {@link geb.Browser#at(Class, Closure)}.
     */
    <T extends Page, R> R at(Class<T> pageType, @DelegatesTo(strategy = DELEGATE_FIRST, genericTypeIndex = 0) Closure<R> assertions) {
        browser.at(at(pageType), assertions)
    }

    /**
     * Delegates to {@link geb.Browser#at(Page)}.
     */
    <T extends Page> T at(T page) {
        browser.at(page)
    }

    /**
     * Delegates to {@link geb.Browser#isAt(Class, boolean)}.
     */
    boolean isAt(Class<? extends Page> pageType, boolean honourGlobalAtCheckWaiting = true) {
        browser.isAt(pageType, honourGlobalAtCheckWaiting)
    }

    /**
     * Delegates to {@link geb.Browser#isAt(Page, boolean)}.
     */
    boolean isAt(Page page, boolean honourGlobalAtCheckWaiting = true) {
        browser.isAt(page, honourGlobalAtCheckWaiting)
    }

    /**
     * Delegates to {@link geb.Browser#checkIfAtAnUnexpectedPage(Class[])}.
     */
    void checkIfAtAnUnexpectedPage(Class<? extends Page>[] expectedPages) {
        browser.checkIfAtAnUnexpectedPage(expectedPages)
    }

    /**
     * Delegates to {@link geb.Browser#checkIfAtAnUnexpectedPage(Page[])}.
     */
    void checkIfAtAnUnexpectedPage(Page[] expectedPages) {
        browser.checkIfAtAnUnexpectedPage(expectedPages)
    }

    /**
     * Delegates to {@link geb.Browser#go(String)}.
     */
    void go(String url) {
        browser.go(url)
    }

    /**
     * Delegates to {@link geb.Browser#go(String, UrlFragment)}.
     */
    void go(String url, UrlFragment fragment) {
        browser.go(url, fragment)
    }

    /**
     * Delegates to {@link geb.Browser#go(Map, UrlFragment)}.
     */
    void go(Map params = [:], UrlFragment fragment) {
        browser.go(params, fragment)
    }

    /**
     * Delegates to {@link geb.Browser#go(Map, String, UrlFragment)}.
     */
    void go(Map params = [:], String url = null, UrlFragment fragment = null) {
        browser.go(params, url, fragment)
    }

    /**
     * Delegates to {@link geb.Browser#to(Map, Class, Object[])}.
     */
    <T extends Page> T to(Map params = [:], Class<T> pageType, Object[] args) {
        browser.to(params, pageType, args)
    }

    /**
     * Delegates to {@link geb.Browser#to(Map, Class, UrlFragment, Object[])}.
     */
    <T extends Page> T to(Map params = [:], Class<T> pageType, UrlFragment fragment, Object[] args) {
        browser.to(params, pageType, fragment, args)
    }

    /**
     * Delegates to {@link geb.Browser#to(Map, Page, Object[])}.
     */
    <T extends Page> T to(Map params = [:], T page, Object[] args) {
        browser.to(params, page, args)
    }

    /**
     * Delegates to {@link geb.Browser#to(Map, Page, UrlFragment, Object[])}.
     */
    <T extends Page> T to(Map params = [:], T page, UrlFragment fragment, Object[] args) {
        browser.to(params, page, fragment, args)
    }

    /**
     * Delegates to {@link geb.Browser#via(Map, Class, Object[])}.
     */
    <T extends Page> T via(Map params = [:], Class<T> pageType, Object[] args) {
        browser.via(params, pageType, args)
    }

    /**
     * Delegates to {@link geb.Browser#via(Map, Class, UrlFragment, Object[])}.
     */
    <T extends Page> T via(Map params = [:], Class<T> pageType, UrlFragment fragment, Object[] args) {
        browser.via(params, pageType, fragment, args)
    }

    /**
     * Delegates to {@link geb.Browser#via(Map, Page, Object[])}.
     */
    <T extends Page> T via(Map params = [:], T page, Object[] args) {
        browser.via(params, page, args)
    }

    /**
     * Delegates to {@link geb.Browser#via(Map, Page, UrlFragment, Object[])}.
     */
    <T extends Page> T via(Map params = [:], T page, UrlFragment fragment, Object[] args) {
        browser.via(params, page, fragment, args)
    }

    /**
     * Delegates to {@link geb.Browser#clearCookies(String[])}.
     */
    void clearCookies(String... additionalUrls) {
        browser.clearCookies(additionalUrls)
    }

    /**
     * Delegates to {@link geb.Browser#clearCookies()}.
     */
    void clearCookies() {
        browser.clearCookies()
    }

    /**
     * Delegates to {@link geb.Browser#clearCookiesQuietly()}.
     */
    void clearCookiesQuietly() {
        browser.clearCookiesQuietly()
    }

    /**
     * Delegates to {@link geb.Browser#clearWebStorage()}.
     */
    void clearWebStorage() {
        browser.clearWebStorage()
    }

    /**
     * Delegates to {@link geb.Browser#clearWebStorageQuietly()}.
     */
    void clearWebStorageQuietly() {
        browser.clearWebStorageQuietly()
    }

    /**
     * Delegates to {@link geb.Browser#quit()}.
     */
    void quit() {
        browser.quit()
    }

    /**
     * Delegates to {@link geb.Browser#close()}.
     */
    void close() {
        browser.close()
    }

    /**
     * Delegates to {@link geb.Browser#getAvailableWindows()}.
     */
    Set<String> getAvailableWindows() {
        browser.getAvailableWindows()
    }

    /**
     * Delegates to {@link geb.Browser#withWindow(String, Closure)}.
     */
    <T> T withWindow(String window, @DelegatesTo(value = Browser, strategy = DELEGATE_FIRST) Closure<T> block) {
        browser.withWindow(window, block)
    }

    /**
     * Delegates to {@link geb.Browser#withWindow(Closure, Closure)}.
     */
    <T> List<T> withWindow(Closure specification, @DelegatesTo(value = Browser, strategy = DELEGATE_FIRST) Closure<T> block) {
        browser.withWindow(specification, block)
    }

    /**
     * Delegates to {@link geb.Browser#withWindow(Map, Closure, Closure)}.
     */
    <T> List<T> withWindow(Map options, Closure specification, @DelegatesTo(value = Browser, strategy = DELEGATE_FIRST) Closure<T> block) {
        browser.withWindow(options, specification, block)
    }

    /**
     * Delegates to {@link geb.Browser#withWindow(Map, String, Closure)}.
     */
    <T> T withWindow(Map options, String window, @DelegatesTo(value = Browser, strategy = DELEGATE_FIRST) Closure<T> block) {
        browser.withWindow(options, window, block)
    }

    /**
     * Delegates to {@link geb.Browser#withWindow(Map, Closure, Closure)}.
     */
    <T> T withNewWindow(Map options, Closure windowOpeningBlock, @DelegatesTo(value = Browser, strategy = DELEGATE_FIRST) Closure<T> block) {
        browser.withNewWindow(options, windowOpeningBlock, block)
    }

    /**
     * Delegates to {@link geb.Browser#withNewWindow(Closure, Closure)}.
     */
    <T> T withNewWindow(Closure windowOpeningBlock, @DelegatesTo(value = Browser, strategy = DELEGATE_FIRST) Closure<T> block) {
        browser.withNewWindow(windowOpeningBlock, block)
    }

    /**
     * Delegates to {@link geb.Browser#createPage(Class)}.
     */
    <T extends Page> T createPage(Class<T> pageType) {
        browser.createPage(pageType)
    }

    /**
     * Delegates to {@link geb.Browser#getJs()}.
     */
    JavascriptInterface getJs() {
        browser.js
    }

    /**
     * Delegates to {@link geb.Browser#getReportGroupDir()}.
     */
    File getReportGroupDir() {
        browser.reportGroupDir
    }

    /**
     * Delegates to {@link geb.Browser#reportGroup(String)}.
     */
    void reportGroup(String path) {
        browser.reportGroup(path)
    }

    /**
     * Delegates to {@link geb.Browser#reportGroup(Class)}.
     */
    void reportGroup(Class clazz) {
        browser.reportGroup(clazz)
    }

    /**
     * Delegates to {@link geb.Browser#cleanReportGroupDir()}.
     */
    void cleanReportGroupDir() {
        browser.cleanReportGroupDir()
    }

    /**
     * Delegates to {@link geb.Browser#pause()}.
     */
    void pause() {
        browser.pause()
    }

    /**
     * Delegates to {@link geb.Browser#getLocalStorage()}.
     */
    WebStorage getLocalStorage() {
        browser.localStorage
    }

    /**
     * Delegates to {@link geb.Browser#getSessionStorage()}.
     */
    WebStorage getSessionStorage() {
        browser.sessionStorage
    }

    /**
     * Delegates to {@link geb.Browser#verifyAtImplicitly(Class)}.
     */
    void verifyAtImplicitly(Class<? extends Page> targetPage) {
        browser.verifyAtImplicitly(targetPage)
    }

    /**
     * Delegates to {@link geb.Browser#verifyAtImplicitly(Page)}.
     */
    void verifyAtImplicitly(Page targetPage) {
        browser.verifyAtImplicitly(targetPage)
    }

    /**
     * Delegates to {@link geb.Browser#setNetworkLatency(Duration)}.
     */
    void setNetworkLatency(Duration duration) {
        browser.setNetworkLatency(duration)
    }

    /**
     * Delegates to {@link geb.Browser#resetNetworkLatency()}.
     */
    void resetNetworkLatency() {
        browser.resetNetworkLatency()
    }

    /**
     * Delegates to {@link geb.Browser#driverAs(Class)}.
     */
    <T> Optional<T> driverAs(Class<T> castType) {
        browser.driverAs(castType)
    }

    /**
     * Delegates missing method calls to the current {@link geb.Page} instance.
     */
    @CompileDynamic
    def methodMissing(String name, args) {
        browser.page."$name"(*args)
    }

    /**
     * Delegates missing property accesses to the current {@link geb.Page} instance.
     */
    @CompileDynamic
    def propertyMissing(String name) {
        browser.page."$name"
    }

    /**
     * Delegates missing property mutations to the current {@link geb.Page} instance.
     */
    @CompileDynamic
    def propertyMissing(String name, value) {
        browser.page."$name" = value
    }
}