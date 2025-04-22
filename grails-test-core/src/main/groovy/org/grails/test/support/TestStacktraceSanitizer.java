/*
 * Copyright 2009 the original author or authors.
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
package org.grails.test.support;

import java.util.ArrayList;
import java.util.List;

import org.grails.exceptions.reporting.DefaultStackTraceFilterer;

public class TestStacktraceSanitizer {

    private static final String TEST_RUNNING_CLASS = "_GrailsTest";

    private TestStacktraceSanitizer() {
    }

    public static Throwable sanitize(Throwable t) {
        new DefaultStackTraceFilterer().filter(t, true);
        StackTraceElement[] trace = t.getStackTrace();
        List<StackTraceElement> newTrace = new ArrayList<StackTraceElement>();
        for (StackTraceElement stackTraceElement : trace) {
            if (stackTraceElement.getClassName().startsWith(TEST_RUNNING_CLASS)) {
                break;
            }

            newTrace.add(stackTraceElement);
        }

        t.setStackTrace(newTrace.toArray(new StackTraceElement[newTrace.size()]));
        return t;
    }
}
