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
package org.grails.gsp.compiler.tags;

import grails.util.GrailsStringUtils;
import org.grails.taglib.GrailsTagException;

/**
 * Allows defining of variables within the page context.
 *
 * @author Graeme Rocher
 */
public class GroovyDefTag extends GroovySyntaxTag {

    public static final String TAG_NAME = "def";
    private static final String ATTRIBUTE_VALUE = "value";
    private static final String ATTRIBUTE_TYPE = "type";

    public void doStartTag() {
        String expr = attributes.get(ATTRIBUTE_VALUE);
        if (GrailsStringUtils.isBlank(expr)) {
            throw new GrailsTagException("Tag [" + TAG_NAME + "] missing required attribute [" + ATTRIBUTE_VALUE + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }
        expr = calculateExpression(expr);

        String var = attributes.get(ATTRIBUTE_VAR);
        if (GrailsStringUtils.isBlank(var)) {
            throw new GrailsTagException("Tag [" + TAG_NAME + "] missing required attribute [" + ATTRIBUTE_VAR + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }
        var = extractAttributeValue(var);

        String typeName = attributes.get(ATTRIBUTE_TYPE);
        if (GrailsStringUtils.isBlank(typeName)) {
            typeName = "def";
        } else {
            typeName = extractAttributeValue(typeName);
        }

        out.print(typeName + " ");
        out.print(var);
        out.print('=');

        if (typeName.equals("def") || typeName.equals("Object")) {
            out.println(expr);
        } else {
            out.println(typeName + ".cast(" + expr + ")");
        }
    }

    public void doEndTag() {
        // do nothing
    }

    public String getName() {
        return TAG_NAME;
    }

    @Override
    public boolean isKeepPrecedingWhiteSpace() {
        return true;
    }

    @Override
    public boolean isAllowPrecedingContent() {
        return true;
    }
}
