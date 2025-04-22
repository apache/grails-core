/*
 * Copyright 2004-2008 the original author or authors.
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
package org.grails.web.xml;

import groovy.xml.streamingmarkupsupport.StreamingMarkupWriter;
import static org.grails.web.xml.XMLStreamWriter.Mode.*;

import java.io.IOException;
import java.io.Writer;
import java.util.Stack;

/**
 * A simple XML Stream Writer that leverages the StreamingMarkupWriter of Groovy
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class XMLStreamWriter {

    protected StreamingMarkupWriter writer;

    protected Mode mode = INIT;

    protected Stack<String> tagStack = new Stack<String>();

    private char quoteChar = '"';

    public XMLStreamWriter(StreamingMarkupWriter writer) {
        this.writer = writer;
    }

    public XMLStreamWriter startDocument(String encoding, String version) throws IOException {
        if (mode != INIT) {
            throw new IllegalStateException();
        }
        writer.unescaped().write(String.format("<?xml version=\"%s\" encoding=\"%s\"?>", version, encoding));
        return this;
    }

    protected void startTag() throws IOException {
        writer.unescaped().write('<');
    }

    public XMLStreamWriter startNode(String tag) throws IOException {
        if (mode == TAG)
            endStartTag();

        startTag();
        writer.unescaped().write(tag);

        tagStack.push(tag);
        mode = TAG;
        return this;
    }

    public XMLStreamWriter end() throws IOException {
        Writer ue = writer.unescaped();
        if (mode == TAG) {
            ue.write(" />");
            if (tagStack.pop() == null) {
                throw new IllegalStateException();
            }
        } else if (mode == CONTENT) {
            ue.write('<');
            ue.write('/');
            String t = tagStack.pop();
            if (t == null) {
                throw new IllegalStateException();
            }
            ue.write(t);
            ue.write('>');
        }
        mode = CONTENT;
        return this;
    }

    public XMLStreamWriter attribute(String name, String value) throws IOException {
        if (mode != TAG) {
            throw new IllegalStateException();
        }
        Writer ue = writer.unescaped();
        ue.write(" ");
        ue.write(name);
        ue.write('=');
        ue.write(quoteChar);
        writer.setWritingAttribute(true);
        writer.escaped().write(value);
        writer.setWritingAttribute(false);
        ue.write(quoteChar);

        return this;
    }

    protected void endStartTag() throws IOException {
        writer.unescaped().write('>');
    }

    public XMLStreamWriter characters(String data) throws IOException {
        if (mode == TAG) {
            endStartTag();
        }
        mode = CONTENT;
        writer.escaped().write(data);

        return this;
    }

    protected enum Mode {
        INIT,
        TAG,
        CONTENT
    }
}
