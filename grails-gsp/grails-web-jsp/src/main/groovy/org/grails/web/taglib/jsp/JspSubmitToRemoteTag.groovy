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
package org.grails.web.taglib.jsp

import groovy.transform.CompileStatic

/**
 * @author Graeme Rocher
 * @since 28-Feb-2006
 */
@CompileStatic
class JspSubmitToRemoteTag extends JspInvokeGrailsTagLibTag {

    private static final long serialVersionUID = -5463152702299747113L

    private static final String TAG_NAME = 'submitToRemote'

    private String name
    private String value
    private String controller
    private String action
    private String id
    private String update
    private String before
    private String after
    private String method
    private String asynchronous
    private String url
    private String params
    private String onSuccess
    private String onFailure
    private String onComplete
    private String onLoading
    private String onLoaded
    private String onInteractive

    JspSubmitToRemoteTag() {
        setTagName(TAG_NAME)
    }

    String getParams() {
        return params
    }

    void setParams(String params) {
        this.params = params
    }

    String getOnSuccess() {
        return onSuccess
    }

    void setOnSuccess(String onSuccess) {
        this.onSuccess = onSuccess
    }

    String getOnFailure() {
        return onFailure
    }

    void setOnFailure(String onFailure) {
        this.onFailure = onFailure
    }

    String getOnComplete() {
        return onComplete
    }

    void setOnComplete(String onComplete) {
        this.onComplete = onComplete
    }

    String getOnLoading() {
        return onLoading
    }

    void setOnLoading(String onLoading) {
        this.onLoading = onLoading
    }

    String getOnLoaded() {
        return onLoaded
    }

    void setOnLoaded(String onLoaded) {
        this.onLoaded = onLoaded
    }

    String getOnInteractive() {
        return onInteractive
    }

    void setOnInteractive(String onInteractive) {
        this.onInteractive = onInteractive
    }

    String getUrl() {
        return url
    }

    void setUrl(String url) {
        this.url = url
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getValue() {
        return value
    }

    void setValue(String value) {
        this.value = value
    }

    String getController() {
        return controller
    }

    void setController(String controller) {
        this.controller = controller
    }

    String getAction() {
        return action
    }

    void setAction(String action) {
        this.action = action
    }

    @Override
    String getId() {
        return id
    }

    @Override
    void setId(String id) {
        this.id = id
    }

    String getUpdate() {
        return update
    }

    void setUpdate(String update) {
        this.update = update
    }

    String getBefore() {
        return before
    }

    void setBefore(String before) {
        this.before = before
    }

    String getAfter() {
        return after
    }

    void setAfter(String after) {
        this.after = after
    }

    String getMethod() {
        return method
    }

    void setMethod(String method) {
        this.method = method
    }

    String getAsynchronous() {
        return asynchronous
    }

    void setAsynchronous(String asynchronous) {
        this.asynchronous = asynchronous
    }
}
