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

abstract class GroovyConditionalTag extends GroovySyntaxTag {

    static final String ATTRIBUTE_TEST = "test";
    static final String ATTRIBUTE_ENV = "env";

    public void doStartTag() {
        String env = attributeValueOrNull(ATTRIBUTE_ENV);
        String test = attributeValueOrNull(ATTRIBUTE_TEST);

        if ((env == null) && (test == null)) {
            throw new GrailsTagException("Tag [" + getName() +
                    "] must have one or both of the attributes [" +
                    ATTRIBUTE_TEST + "] or [" + ATTRIBUTE_ENV + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }

        String envExpression = environmentExpressionOrTrue(env);
        String testExpression = testExpressionOrTrue(test);

        outputStartTag(envExpression, testExpression);
    }

    protected abstract void outputStartTag(String envExpression, String testExpression);

    public void doEndTag() {
        out.println("}");
    }

    protected String attributeValueOrNull(String attributeName) {
        String attributeValue = attributes.get(attributeName);
        return GrailsStringUtils.isBlank(attributeValue) ? null : attributeValue;
    }

    private String environmentExpressionOrTrue(String envAttributeValue) {
        String expression = "true";
        if (envAttributeValue != null) {
            expression = "(grails.util.Environment.current.name == '" + calculateExpression(envAttributeValue) + "')";
        }
        return expression;
    }

    private String testExpressionOrTrue(String testAttributeValue) {
        String expression = "true";
        if (testAttributeValue != null) {
            expression = "(" + testAttributeValue + ")";
        }
        return expression;
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
