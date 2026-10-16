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
 * JSP facade onto the GSP form tag
 *
 * @author Graeme Rocher
 */
@CompileStatic
class JspFormTag extends JspInvokeGrailsTagLibTag {

    private static final long serialVersionUID = -3045238592311090749L

    private static final String TAG_NAME = 'form'

    private String controller
    private String action
    private String id
    private String url
    private String params
    private String method

    JspFormTag() {
        setTagName(TAG_NAME)
    }

    String getMethod() {
        return method
    }

    void setMethod(String method) {
        this.method = method
    }

    String getParams() {
        return params
    }

    void setParams(String params) {
        this.params = params
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

    String getUrl() {
        return url
    }

    void setUrl(String url) {
        this.url = url
    }
}
