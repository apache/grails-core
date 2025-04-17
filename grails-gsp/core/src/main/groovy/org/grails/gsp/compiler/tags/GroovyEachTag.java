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
 * @author Graeme Rocher
 */
public class GroovyEachTag extends GroovySyntaxTag {

    public static final String TAG_NAME = "each";

    public void doStartTag() {
        String in = attributes.get(ATTRIBUTE_IN);
        if (GrailsStringUtils.isBlank(in)) {
            throw new GrailsTagException("Tag [" + TAG_NAME + "] missing required attribute [" + ATTRIBUTE_IN + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }

        doEachMethod(in);
    }

    public void doEndTag() {
        endEachMethod();
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
