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
 * JSP facade onto the GSP message tag
 *
 * @author Graeme Rocher
 * @since 28-Feb-2006
 */
@CompileStatic
class JspMessageTag extends JspInvokeGrailsTagLibTag {

    private static final long serialVersionUID = -3098229619044871773L

    private static final String TAG_NAME = 'message'

    private String code
    private String error

    JspMessageTag() {
        setTagName(TAG_NAME)
    }

    String getCode() {
        return code
    }

    void setCode(String code) {
        this.code = code
    }

    String getError() {
        return error
    }

    void setError(String error) {
        this.error = error
    }
}
