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
class JspSelectTag extends JspInvokeGrailsTagLibTag {

    private static final long serialVersionUID = 294858160471737590L

    private static final String TAG_NAME = 'select'

    private String name
    private String from
    private String optionKey
    private String optionValue

    JspSelectTag() {
        setTagName(TAG_NAME)
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getFrom() {
        return from
    }

    void setFrom(String from) {
        this.from = from
    }

    String getOptionKey() {
        return optionKey
    }

    void setOptionKey(String optionKey) {
        this.optionKey = optionKey
    }

    String getOptionValue() {
        return optionValue
    }

    void setOptionValue(String optionValue) {
        this.optionValue = optionValue
    }
}
