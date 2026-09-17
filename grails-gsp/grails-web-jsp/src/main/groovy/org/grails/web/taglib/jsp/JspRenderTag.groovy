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
 * JSP facade onto the GSP render tag.
 *
 * @author Graeme Rocher
 */
@CompileStatic
class JspRenderTag extends JspInvokeGrailsTagLibTag {

    private static final long serialVersionUID = -3650113799207644153L
    private static final String TAG_NAME = 'render'
    private String template
    private String bean
    private String collection
    private String model

    JspRenderTag() {
        setTagName(TAG_NAME)
    }

    String getTemplate() {
        return template
    }

    void setTemplate(String template) {
        this.template = template
    }

    String getBean() {
        return bean
    }

    void setBean(String bean) {
        this.bean = bean
    }

    String getCollection() {
        return collection
    }

    void setCollection(String collection) {
        this.collection = collection
    }

    String getModel() {
        return model
    }

    void setModel(String model) {
        this.model = model
    }
}
