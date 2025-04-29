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

import geb.Module
import geb.Page
import geb.content.Navigable
import geb.content.TemplateDerivedPageContent
import geb.frame.FrameSupport
import geb.interaction.InteractDelegate
import geb.interaction.InteractionsSupport
import geb.js.AlertAndConfirmSupport
import geb.navigator.Navigator
import geb.textmatching.TextMatchingSupport
import geb.url.UrlFragment
import geb.waiting.WaitingSupport
import grails.plugin.geb.ContainerGebSpec
import groovy.transform.CompileStatic
import groovy.transform.SelfType
import org.openqa.selenium.By
import org.openqa.selenium.WebElement

/**
 * Handles delegation to the page instance so that the Geb API can be used directly in the test.
 * <p>
 * As method parameter names are not available in the Geb artifacts we are delegating manually to
 * get the best possible IDE support and user experience.
 *
 * @author Mattias Reichel
 * @since 4.2
 */
@CompileStatic
@SelfType(ContainerGebSpec)
trait PageDelegate implements Navigable, AlertAndConfirmSupport, WaitingSupport, FrameSupport, InteractionsSupport {

    @Delegate
    private final TextMatchingSupport textMatchingSupport = new TextMatchingSupport()

    Page getPage() {
        testManager.browser.page
    }

    void to(Map params, UrlFragment fragment = null, Object[] args) {
        page.to(params, fragment, args)
    }

    UrlFragment getPageFragment() {
        page.pageFragment
    }

    String getPageUrl(String path) {
        page.getPageUrl(path)
    }

    String getTitle() {
        page.title
    }

    <T> T refreshWaitFor(Map params = [:], Closure<T> block) {
        page.refreshWaitFor(params, block)
    }

    <T> T refreshWaitFor(Map params = [:], String waitPreset, Closure<T> block) {
        page.refreshWaitFor(params, waitPreset, block)
    }

    <T> T refreshWaitFor(Map params = [:], Number timeoutSeconds, Closure<T> block) {
        page.refreshWaitFor(params, timeoutSeconds, block)
    }

    <T> T refreshWaitFor(Map params = [:], Number timeoutSeconds, Number intervalSeconds, Closure<T> block) {
        page.refreshWaitFor(params, timeoutSeconds, intervalSeconds, block)
    }

    @Override
    Navigator find() {
        page.find()
    }

    @Override
    Navigator $() {
        page.$()
    }

    @Override
    Navigator find(int index) {
        page.find(index)
    }

    @Override
    Navigator find(Range<Integer> range) {
        page.find(range)
    }

    @Override
    Navigator $(int index) {
        page.$(index)
    }

    @Override
    Navigator $(Range<Integer> range) {
        page.$(range)
    }

    @Override
    Navigator $(Navigator... navigators) {
        page.$(navigators)
    }

    @Override
    Navigator $(WebElement... elements) {
        page.$(elements)
    }

    @Override
    Navigator focused() {
        page.focused()
    }

    @Override
    <T extends Module> T module(Class<T> moduleClass) {
        page.module(moduleClass)
    }

    @Override
    <T extends Module> T module(T module) {
        page.module(module)
    }

    @Override
    Navigator find(String selector) {
        page.find(selector)
    }

    @Override
    Navigator $(String selector) {
        page.$(selector)
    }

    @Override
    Navigator find(Map<String, Object> attributes) {
        page.find(attributes)
    }

    @Override
    Navigator find(String selector, int index) {
        page.find(selector, index)
    }

    @Override
    Navigator find(String selector, Range<Integer> range) {
        page.find(selector, range)
    }

    @Override
    Navigator $(String selector, int index) {
        page.$(selector, index)
    }

    @Override
    Navigator $(String selector, Range<Integer> range) {
        page.$(selector, range)
    }

    @Override
    Navigator $(Map<String, Object> attributes, By bySelector) {
        page.$(attributes, bySelector)
    }

    @Override
    Navigator $(Map<String, Object> attributes, By bySelector, int index) {
        page.$(attributes, bySelector, index)
    }

    @Override
    Navigator $(Map<String, Object> attributes, By bySelector, Range<Integer> range) {
        page.$(attributes, bySelector, range)
    }

    @Override
    Navigator $(By bySelector) {
        page.$(bySelector)
    }

    @Override
    Navigator $(By bySelector, int index) {
        page.$(bySelector, index)
    }

    @Override
    Navigator $(By bySelector, Range<Integer> range) {
        page.$(bySelector, range)
    }

    @Override
    Navigator find(By bySelector, Range<Integer> range) {
        page.find(bySelector, range)
    }

    @Override
    Navigator $(Map<String, Object> attributes) {
        page.$(attributes)
    }

    @Override
    Navigator $(Map<String, Object> attributes, int index) {
        page.$(attributes, index)
    }

    @Override
    Navigator $(Map<String, Object> attributes, Range<Integer> range) {
        page.$(attributes, range)
    }

    @Override
    Navigator $(Map<String, Object> attributes, String selector) {
        page.$(attributes, selector)
    }

    @Override
    Navigator $(Map<String, Object> attributes, String selector, int index) {
        page.$(attributes, selector, index)
    }

    @Override
    Navigator $(Map<String, Object> attributes, String selector, Range<Integer> range) {
        page.$(attributes, selector, range)
    }

    @Override
    Navigator find(By bySelector) {
        page.find(bySelector)
    }

    @Override
    Navigator find(By bySelector, int index) {
        page.find(bySelector, index)
    }

    @Override
    Navigator find(Map<String, Object> attributes, By bySelector) {
        page.find(attributes, bySelector)
    }

    @Override
    Navigator find(Map<String, Object> attributes, int index) {
        page.find(attributes, index)
    }

    @Override
    Navigator find(Map<String, Object> attributes, Range<Integer> range) {
        page.find(attributes, range)
    }

    @Override
    Navigator find(Map<String, Object> attributes, By bySelector, int index) {
        page.find(attributes, bySelector, index)
    }

    @Override
    Navigator find(Map<String, Object> attributes, By bySelector, Range<Integer> range) {
        page.find(attributes, bySelector, range)
    }

    @Override
    Navigator find(Map<String, Object> attributes, String selector) {
        page.find(attributes, selector)
    }

    @Override
    Navigator find(Map<String, Object> attributes, String selector, int index) {
        page.find(attributes, selector, index)
    }

    @Override
    Navigator find(Map<String, Object> attributes, String selector, Range<Integer> range) {
        page.find(attributes, selector, range)
    }

    @Override
    Object withAlert(Closure actions) {
        page.withAlert(actions)
    }

    @Override
    Object withAlert(Map params, Closure actions) {
         page.withAlert(params, actions)
    }

    @Override
    void withNoAlert(Closure actions) {
        page.withNoAlert(actions)
    }

    @Override
    Object withConfirm(boolean ok, Closure actions) {
        page.withConfirm(ok, actions)
    }

    @Override
    Object withConfirm(Closure actions) {
        page.withConfirm(actions)
    }

    @Override
    Object withConfirm(Map attributes, Closure actions) {
        page.withConfirm(attributes, actions)
    }

    @Override
    Object withConfirm(Map attributes, boolean ok, Closure actions) {
        page.withConfirm(attributes, ok, actions)
    }

    @Override
    void withNoConfirm(Closure actions) {
        page.withNoConfirm(actions)
    }

    @Override
    <T> T waitFor(String waitPreset, Closure<T> block) {
        page.waitFor(waitPreset, block)
    }

    @Override
    <T> T waitFor(Map params, String waitPreset, Closure<T> block) {
        page.waitFor(params, waitPreset, block)
    }

    @Override
    <T> T waitFor(Closure<T> block) {
        page.waitFor(block)
    }

    @Override
    <T> T waitFor(Map params, Closure<T> block) {
        page.waitFor(params, block)
    }

    @Override
    <T> T waitFor(Number timeoutSeconds, Closure<T> block) {
        page.waitFor(timeoutSeconds, block)
    }

    @Override
    <T> T waitFor(Map params, Number timeoutSeconds, Closure<T> block) {
        page.waitFor(params, timeoutSeconds, block)
    }

    @Override
    <T> T waitFor(Number timeoutSeconds, Number intervalSeconds, Closure<T> block) {
        page.waitFor(timeoutSeconds, intervalSeconds, block)
    }

    @Override
    <T> T waitFor(Map params, Number timeoutSeconds, Number intervalSeconds, Closure<T> block) {
        page.waitFor(params, timeoutSeconds, intervalSeconds, block)
    }

    @Override
    <T> T withFrame(Object frame, Closure<T> block) {
        page.withFrame(frame, block)
    }

    @Override
    <P extends Page, T> T withFrame(Object frame, @DelegatesTo.Target Class<P> page, @DelegatesTo(strategy = 1, genericTypeIndex = 0) Closure<T> block) {
        this.page.withFrame(frame, page, block)
    }

    @Override
    <P extends Page, T> T withFrame(Object frame, @DelegatesTo.Target P page, @DelegatesTo(strategy = 1) Closure<T> block) {
        this.page.withFrame(frame, page, block)
    }

    @Override
    <P extends Page, T> T withFrame(Navigator frame, @DelegatesTo.Target Class<P> page, @DelegatesTo(strategy = 1, genericTypeIndex = 0) Closure<T> block) {
        this.page.withFrame(frame, page, block)
    }

    @Override
    <P extends Page, T> T withFrame(Navigator frame, @DelegatesTo.Target P page, @DelegatesTo(strategy = 1) Closure<T> block) {
        this.page.withFrame(frame, page, block)
    }

    @Override
    <T> T withFrame(Navigator frame, Closure<T> block) {
        page.withFrame(frame, block)
    }

    @Override
    <T> T withFrame(TemplateDerivedPageContent frame, Closure<T> block) {
        page.withFrame(frame, block)
    }

    @Override
    void interact(@DelegatesTo(strategy = 1, value = InteractDelegate.class) Closure interactionClosure) {
        page.interact(interactionClosure)
    }
}