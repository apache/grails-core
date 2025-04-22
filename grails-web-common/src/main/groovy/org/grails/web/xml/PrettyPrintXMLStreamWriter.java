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

import java.io.IOException;
import java.io.Writer;

/**
 * A XMLStreamWriter dedicated to create indented/pretty printed output.
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class PrettyPrintXMLStreamWriter extends XMLStreamWriter{

    public static final String DEFAULT_INDENT_STR = "  ";

    public static final String NEWLINE;
    static {
        String nl = System.getProperty("line.separator");
        NEWLINE = nl != null ? nl : "\n";
    }

    private final String indent;

    private int level = 0;

    private boolean doIndent = false;

    public PrettyPrintXMLStreamWriter(StreamingMarkupWriter writer) {
        this(writer, DEFAULT_INDENT_STR);
    }

    public PrettyPrintXMLStreamWriter(StreamingMarkupWriter writer, String indent) {
        super(writer);
        this.indent = indent;
    }

    private void newline() throws IOException {
        writer.unescaped().write(NEWLINE);
    }

    private void indent() throws IOException {
        Writer ue = writer.unescaped();
        for (int i=0; i<level; i++) {
            ue.write(indent);
        }
    }

    @Override
    protected void endStartTag() throws IOException {
        super.endStartTag();
        newline();
        if (doIndent)
            indent();
    }

    @Override
    protected void startTag() throws IOException {
        indent();
        super.startTag();
    }

    @Override
    public XMLStreamWriter startNode(String tag) throws IOException {
        doIndent = false;
        super.startNode(tag);
        level++;
        return this;
    }

    @Override
    public XMLStreamWriter end() throws IOException {
        level--;
        if (mode != Mode.TAG) {
            indent();
        }
        super.end();
        newline();
//        indent();
        return this;
    }

    @Override
    public XMLStreamWriter characters(String data) throws IOException {
        doIndent = true;
        super.characters(data);
        newline();
        return this;
    }

    @Override
    public XMLStreamWriter startDocument(String encoding, String version) throws IOException {
        super.startDocument(encoding, version);
        newline();
        return this;
    }
}
