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
 * JSP facade onto the GSP renderErrors tag
 *
 * @author Graeme Rocher
 * @since 28-Feb-2006
 */
public class JspRenderErrorsTag extends JspInvokeGrailsTagLibTag {
    private static final long serialVersionUID = 6077206372374860458L;

    private static final String TAG_NAME = "renderErrors";

    private String bean;
    private String model;
    private String field;
    private String as;

    public JspRenderErrorsTag() {
        setTagName(TAG_NAME);
    }

    public String getAs() {
        return as;
    }

    public void setAs(String as) {
        this.as = as;
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
