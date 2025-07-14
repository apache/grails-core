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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Stack;

/**
 * TODO Proof of concept
 * Should capture the JSON Path to the current element
 *
 * @author Siegfried Puchbauer
 */
public class PathCapturingJSONWriterWrapper extends JSONWriter {

    private final Log log = LogFactory.getLog(getClass());

    private final boolean debugCurrentStack = true;
    private JSONWriter delegate;
    private Stack<PathElement> pathStack = new Stack<PathElement>();

    public PathCapturingJSONWriterWrapper(JSONWriter delegate) {
        super(null);
        this.delegate = delegate;
    }

    @Override
    public JSONWriter append(String s) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("append(%s)", s));
        }
        delegate.append(s);
        return this;
    }

    @Override
    public void comma() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug("comma()");
        }
        delegate.comma();
    }

    @Override
    public JSONWriter array() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("array()"));
        }
        pathStack.push(new IndexElement(-1));
        delegate.array();
        return this;
    }

    @Override
    public JSONWriter end(Mode m, char c) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("end(%s, %s)", m, c));
        }
        delegate.end(m, c);
        return this;
    }

    @Override
    public JSONWriter endArray() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("endArray()"));
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
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("endObject()"));
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
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("key(%s)", s));
        }
        pathStack.push(new PropertyElement(s));
        delegate.key(s);
        return this;
    }

    @Override
    public JSONWriter object() {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("object()"));
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
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("pop(%s)", c));
        }
        delegate.pop(c);
    }

    @Override
    public void push(Mode c) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("push(%s)", c));
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
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("Next index: " + x));
        }
        return x;
    }

    @Override
    public JSONWriter value(boolean b) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("value(boolean %b)", b));
        }
        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        } else {
            pathStack.pop();
        }
        delegate.value(b);
        return this;
    }

    @Override
    public JSONWriter value(double d) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("value(double %s)", d));
        }
        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        } else {
            pathStack.pop();
        }
        delegate.value(d);
        return this;
    }

    @Override
    public JSONWriter value(long l) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("value(long %s)", l));
        }
        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        } else {
            pathStack.pop();
        }
        delegate.value(l);
        return this;
    }

    @Override
    public JSONWriter value(Object o) {
        if (log.isDebugEnabled()) {
            if (debugCurrentStack) {
                log.debug(delegate.mode.name() + " > " + String.format(">> " + getCurrentStrackReference()));
            }
            log.debug(delegate.mode.name() + " > " + String.format("value(Object %s)", o));
        }

        if (delegate.mode == Mode.ARRAY) {
            pushNextIndex();
        } else {
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
