/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.taglib.jsp;

/**
 * JSP facade onto the GSP eachError tag
 *
 * @author Graeme Rocher
 * @since 28-Feb-2006
 */
public class JspEachErrorTag extends JspInvokeGrailsTagLibTag {
    private static final long serialVersionUID = 4905214290442171610L;

    private static final String TAG_NAME = "eachError";

    private String bean;
    private String model;
    private String field;

    public JspEachErrorTag() {
        setTagName(TAG_NAME);
    }

    public String getBean() {
        return bean;
    }

    public void setBean(String bean) {
        this.bean = bean;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
