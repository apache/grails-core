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
package org.grails.web.json;

import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO Proof of concept
 * Should capture the JSON Path to the current element
 *
 * @author Siegfried Puchbauer
 */
public class PathCapturingJSONWriterWrapper extends JSONWriter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final boolean debugCurrentStack = true;
    private JSONWriter delegate;
    private Stack<PathElement> pathStack = new Stack<>();

    public PathCapturingJSONWriterWrapper(JSONWriter delegate) {
        super(null);
        this.delegate = delegate;
    }

    @Override
    public JSONWriter append(String s) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > append({})", delegate.mode.name(), s);
        }
        delegate.append(s);
        return this;
    }

    @Override
    public void comma() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("comma()");
        }
        delegate.comma();
    }

    @Override
    public JSONWriter array() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > array()", delegate.mode.name());
        }
        pathStack.push(new IndexElement(-1));
        delegate.array();
        return this;
    }

    @Override
    public JSONWriter end(Mode m, char c) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > end({}, {})", delegate.mode.name(), m, c);
        }
        delegate.end(m, c);
        return this;
    }

    @Override
    public JSONWriter endArray() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > endArray()", delegate.mode.name());
        }
        pathStack.pop();
        delegate.endArray();
        if (delegate.mode == Mode.KEY) {
            pathStack.pop();
        }
        return this;
    }

    @Override
    public JSONWriter endObject() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > endObject()", delegate.mode.name());
        }
        delegate.endObject();
        if (delegate.mode != Mode.ARRAY && pathStack.size() > 0) {
            pathStack.pop();
        }
        return this;
    }

    @Override
    public JSONWriter key(String s) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > key({})", delegate.mode.name(), s);
        }
        pathStack.push(new PropertyElement(s));
        delegate.key(s);
        return this;
    }

    @Override
    public JSONWriter object() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > object()", delegate.mode.name());
        }
        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        }
        delegate.object();
        return this;
    }

    @Override
    public void pop(Mode c) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > pop({})", delegate.mode.name(), c);
        }
        delegate.pop(c);
    }

    @Override
    public void push(Mode c) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > push({})", delegate.mode.name(), c);
        }
        delegate.push(c);
    }

    private void pushNextIndex() {
        int x = nextIndex();
        pathStack.pop();
        pathStack.push(new IndexElement(x));
    }

    private int nextIndex() {
        int x = ((IndexElement) pathStack.peek()).index + 1;
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > Next index: {}", delegate.mode.name(), x);
        }
        return x;
    }

    @Override
    public JSONWriter value(boolean b) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > value(boolean {})", delegate.mode.name(), b);
        }
        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        }
        else {
            pathStack.pop();
        }
        delegate.value(b);
        return this;
    }

    @Override
    public JSONWriter value(double d) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > value(double {})", delegate.mode.name(), d);
        }
        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        }
        else {
            pathStack.pop();
        }
        delegate.value(d);
        return this;
    }

    @Override
    public JSONWriter value(long l) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > value(long {})", delegate.mode.name(), l);
        }
        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        }
        else {
            pathStack.pop();
        }
        delegate.value(l);
        return this;
    }

    @Override
    public JSONWriter value(Object o) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) log.debug("{} > >> {}", delegate.mode.name(), getCurrentStrackReference());
            log.debug("{} > value(Object {})", delegate.mode.name(), o);
        }

        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        }
        else {
            pathStack.pop();
        }
        delegate.value(o);
        return this;
    }

    private class PathElement {
        // ??
    }

    private class PropertyElement extends PathElement {
        private String property;

        private PropertyElement(String property) {
            this.property = property;
        }

        @Override
        public String toString() {
            return "." + property;
        }
    }

    private class IndexElement extends PathElement {
        private int index;

        private IndexElement(int index) {
            this.index = index;
        }

        @Override
        public String toString() {
            return "[" + index + "]";
        }
    }

    public String getStackReference(int depth) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            PathElement el = pathStack.get(i);
            out.append(el.toString());
        }
        return out.toString();
    }

    public String getCurrentStrackReference() {
        StringBuilder out = new StringBuilder();
        for (PathElement el : pathStack) {
            out.append(el.toString());
        }
        return out.toString();
    }
}
