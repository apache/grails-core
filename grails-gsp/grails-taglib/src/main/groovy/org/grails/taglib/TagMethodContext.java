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
package org.grails.taglib;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import groovy.lang.Closure;

public final class TagMethodContext {

    private static final ThreadLocal<Deque<TagMethodContextEntry>> CONTEXT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private TagMethodContext() {
    }

    public static void push(Map<?, ?> attrs, Closure<?> body) {
        CONTEXT_STACK.get().push(new TagMethodContextEntry(attrs, body));
    }

    public static void pop() {
        Deque<TagMethodContextEntry> stack = CONTEXT_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            CONTEXT_STACK.remove();
        }
    }

    public static Map<?, ?> currentAttrs() {
        Deque<TagMethodContextEntry> stack = CONTEXT_STACK.get();
        return stack.isEmpty() ? null : stack.peek().attrs();
    }

    public static Closure<?> currentBody() {
        Deque<TagMethodContextEntry> stack = CONTEXT_STACK.get();
        return stack.isEmpty() ? null : stack.peek().body();
    }

    private record TagMethodContextEntry(Map<?, ?> attrs, Closure<?> body) { }
}
