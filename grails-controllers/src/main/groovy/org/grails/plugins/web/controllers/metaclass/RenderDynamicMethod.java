/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.web.controllers.metaclass;

/**
 * Allows rendering of text, views, and templates to the response
 *
 * @author Graeme Rocher
 * @since 0.2
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class RenderDynamicMethod {
    public static final String METHOD_SIGNATURE = "render";
    public static final String ARGUMENT_TEXT = "text";
    public static final String ARGUMENT_STATUS = "status";
    public static final String ARGUMENT_LAYOUT = "layout";
    public static final String ARGUMENT_CONTENT_TYPE = "contentType";
    public static final String ARGUMENT_ENCODING = "encoding";
    public static final String ARGUMENT_VIEW = "view";
    public static final String ARGUMENT_MODEL = "model";
    public static final String ARGUMENT_TEMPLATE = "template";
    public static final String ARGUMENT_CONTEXTPATH = "contextPath";
    public static final String ARGUMENT_BEAN = "bean";
    public static final String ARGUMENT_COLLECTION = "collection";
    public static final String ARGUMENT_BUILDER = "builder";
    public static final String ARGUMENT_VAR = "var";
    public static final String DISPOSITION_HEADER_PREFIX = "attachment;filename=";
    public static final String ARGUMENT_PLUGIN = "plugin";
    public static final String DEFAULT_ARGUMENT = "it";
    public static final String BUILDER_TYPE_JSON = "json";
    public static final String TEXT_HTML = "text/html";
    public static final String APPLICATION_XML = "application/xml";
    public static final String DEFAULT_ENCODING = "utf-8";
    public static final String ARGUMENT_FILE = "file";
    public static final String ARGUMENT_FILE_NAME = "fileName";
}
