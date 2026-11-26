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
package org.grails.gsp.compiler;

import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Array;

import grails.util.GrailsStringUtils;
import org.grails.gsp.GroovyPage;

/**
 * A PrintWriter used in the generation of GSP pages that allows printing to the target output
 * stream and maintains a record of the current line number during usage.
 *
 * @author Graeme Rocher
 */
public class GSPWriter extends PrintWriter {

    private int lineNumber = 1;
    private int[] lineNumbers = new int[1000];
    //private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\n|\\r");
    private GroovyPageParser parse;
    private final boolean carriageReturnEndsLine;
    private boolean afterCarriageReturn;

    public GSPWriter(Writer out, GroovyPageParser parse) {
        this(out, parse, GroovyCompilerLines.carriageReturnEndsLine());
    }

    /**
     * @param carriageReturnEndsLine whether the compiler of the generated source takes a carriage
     * return that no line feed follows for the end of a line, which is what {@link GroovyCompilerLines} asks it
     */
    GSPWriter(Writer out, GroovyPageParser parse, boolean carriageReturnEndsLine) {
        super(out);
        this.parse = parse;
        this.carriageReturnEndsLine = carriageReturnEndsLine;
    }

    @Override
    public void write(char buf[], int off, int len) {
        for (int i = off; i < off + len; i++) {
            written(buf[i]);
        }
        super.write(buf, off, len);
    }

    @Override
    public void write(String s, int off, int len) {
        for (int i = off; i < off + len; i++) {
            written(s.charAt(i));
        }
        super.write(s, off, len);
    }

    @Override
    public void write(int c) {
        written((char) c);
        super.write(c);
    }

    /**
     * What is printed can have line breaks of its own, an expression or a scriptlet of several lines
     * has: each ends a line of the generated source as {@link #println()} does, and is recorded as one.
     * A line is what the Groovy compiler counts as one when it names the line of an error or of a
     * frame. Every version of it counts a line feed, and a carriage return in front of a line feed
     * ends no line of its own. A carriage return that no line feed follows ends a line for some
     * versions and none for others, so the compiler that is there is asked. No other character ends a line.
     */
    private void written(char c) {
        if (c == '\n') {
            // after a carriage return that was counted, the line feed is the rest of the same line break
            if (!afterCarriageReturn) {
                addLineNumber();
            }
            afterCarriageReturn = false;
        } else if (c == '\r' && carriageReturnEndsLine) {
            addLineNumber();
            afterCarriageReturn = true;
        } else {
            afterCarriageReturn = false;
        }
    }

    public void printlnToResponse(String s) {
        printlnToResponse(GroovyPage.OUT_STATEMENT, s);
    }

    public void printlnToResponse(String outVarName, String s) {
        if (GrailsStringUtils.isEmpty(s)) {
            return;
        }

        parse.flushTagBuffering();
        super.print(outVarName);
        super.print(".print(");
        super.print(s);
        super.print(")");
        println();
    }

    public void printlnToBuffer(String s, int index) {
        if (s == null) s = "''";
        super.print("buf" + index + " << ");
        super.print(s);
        println();
    }

    @Override
    public void println() {
        // the line separator goes to the target as it is, and not through the methods that count
        if (!(afterCarriageReturn && System.lineSeparator().startsWith("\n"))) {
            addLineNumber();
        }
        afterCarriageReturn = false;
        super.println();
    }

    private void addLineNumber() {
        if (lineNumber > lineNumbers.length) {
            lineNumbers = (int[]) resizeArray(lineNumbers, lineNumbers.length * 2);
        }
        lineNumbers[lineNumber - 1] = parse.getCurrentOutputLineNumber();
        lineNumber++;
    }

    private Object resizeArray(Object oldArray, int newSize) {
        int oldSize = java.lang.reflect.Array.getLength(oldArray);
        Class<?> elementType = oldArray.getClass().getComponentType();
        Object newArray = Array.newInstance(elementType, newSize);
        int preserveLength = Math.min(oldSize, newSize);
        if (preserveLength > 0) {
            System.arraycopy(oldArray, 0, newArray, 0, preserveLength);
        }
        return newArray;
    }

    public int getCurrentLineNumber() {
        return lineNumber;
    }

    public int[] getLineNumbers() {
        return lineNumbers;
    }
}
