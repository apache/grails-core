/*
 * Copyright 2013 the original author or authors.
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
package org.grails.gsp.compiler;

import java.util.Stack;

/**
 * Parses an expression in a GSP.
 *
 * Used by GroovyPageScanner and GroovyPageParser to search for the end of an expression.
 *
 * @author Lari Hotari
 */
class GroovyPageExpressionParser {
    private static enum ParsingState {
        NORMAL, EXPRESSION, QUOTEDVALUE_SINGLE, QUOTEDVALUE_DOUBLE, TRIPLEQUOTED_SINGLE, TRIPLEQUOTED_DOUBLE;
    }
    String scriptTokens;
    int startPos;
    char terminationChar;
    char nextTerminationChar;
    Stack<ParsingState> parsingStateStack = new Stack<ParsingState>();
    boolean containsGstrings=false;
    int terminationCharPos = -1;
    int relativeCharIndex=0;

    public GroovyPageExpressionParser(String scriptTokens, int startPos, char terminationChar,
            char nextTerminationChar, boolean startInExpression) {
        this.scriptTokens = scriptTokens;
        this.startPos = startPos;
        this.terminationChar = terminationChar;
        this.nextTerminationChar = nextTerminationChar;
        if (startInExpression) {
            parsingStateStack.push(ParsingState.EXPRESSION);
        } else {
            parsingStateStack.push(ParsingState.NORMAL);
        }
    }

    /**
     * Finds the ending position of an expression.
     *
     * @return end position of expression
     */
    int parse() {
        int currentPos = startPos;
        char previousChar = 0;
        char previousPreviousChar = 0;

        while(currentPos < scriptTokens.length() && terminationCharPos==-1) {
            ParsingState parsingState = parsingStateStack.peek();
            char ch = scriptTokens.charAt(currentPos++);
            char nextChar = (currentPos < scriptTokens.length()) ? scriptTokens.charAt(currentPos) : 0;

            if (parsingStateStack.size()==1 && ch==terminationChar && (nextTerminationChar==0 || nextTerminationChar==nextChar)) {
                terminationCharPos = currentPos-1;
            } else if (parsingState==ParsingState.EXPRESSION || parsingState==ParsingState.NORMAL) {
                switch(ch) {
                    case '{':
                        if (previousChar=='$' && parsingState==ParsingState.EXPRESSION) {
                            // invalid expression, starting new ${} expression inside expression
                            return -1;
                        }
                        if (previousChar=='$' || parsingState==ParsingState.EXPRESSION) {
                            changeState(ParsingState.EXPRESSION);
                        }
                        break;
                    case '[':
                        if (relativeCharIndex==0 || parsingState==ParsingState.EXPRESSION) {
                            changeState(ParsingState.EXPRESSION);
                        }
                        break;
                    case '}':
                    case ']':
                        if (parsingState==ParsingState.EXPRESSION) {
                            parsingStateStack.pop();
                        }
                        break;
                    case '\'':
                    case '"':
                        if (parsingState==ParsingState.EXPRESSION) {
                            if (nextChar != ch && previousChar != ch) {
                                changeState(ch=='"' ? ParsingState.QUOTEDVALUE_DOUBLE : ParsingState.QUOTEDVALUE_SINGLE);
                            } else if (previousChar==ch && previousPreviousChar==ch) {
                                changeState(ch=='"' ? ParsingState.TRIPLEQUOTED_DOUBLE : ParsingState.TRIPLEQUOTED_SINGLE);
                            }
                        }
                        break;
                }
            } else if (ch=='"' || ch=='\'') {
                if (nextChar != ch && (previousChar != ch || previousPreviousChar=='\\') && (previousChar != '\\' || (previousChar=='\\' && previousPreviousChar=='\\'))
                        && ((parsingState == ParsingState.QUOTEDVALUE_DOUBLE && ch == '"') || (parsingState == ParsingState.QUOTEDVALUE_SINGLE && ch == '\''))) {
                    parsingStateStack.pop();
                }
                else if ((previousChar == ch && previousPreviousChar == ch)
                        && ((parsingState == ParsingState.TRIPLEQUOTED_DOUBLE && ch == '"') || (parsingState == ParsingState.TRIPLEQUOTED_SINGLE && ch == '\''))) {
                    parsingStateStack.pop();
                }
            }
            previousPreviousChar = previousChar;
            previousChar=ch;
            relativeCharIndex++;
        }
        return terminationCharPos;
    }

    private void changeState(ParsingState newState) {
        ParsingState currentState = parsingStateStack.peek();
        // check if expression contains GStrings
        if (relativeCharIndex > 1 && newState==ParsingState.EXPRESSION && (currentState==ParsingState.QUOTEDVALUE_DOUBLE || currentState==ParsingState.TRIPLEQUOTED_DOUBLE || currentState==ParsingState.NORMAL)) {
            containsGstrings=true;
        }
        parsingStateStack.push(newState);
    }

    public boolean isContainsGstrings() {
        return containsGstrings;
    }

    public int getTerminationCharPos() {
        return terminationCharPos;
    }
}
