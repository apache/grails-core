/*
 * Copyright 2014 the original author or authors.
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
package org.grails.charsequences;

/**
 * Wraps a single char and implements CharSequence interface
 * 
 * 
 * @author Lari Hotari
 * @since 2.3.10
 *
 */
class SingleCharCharSequence implements CharSequence, CharArrayAccessible {
    private final char ch;

    SingleCharCharSequence(int c) {
        this((char)c);
    }
    
    SingleCharCharSequence(char ch) {
        this.ch = ch;
    }

    public char charAt(int index) {
        if ((index < 0) || (index > 0))
            throw new StringIndexOutOfBoundsException(index);
        return ch;
    }

    public int length() {
        return 1;
    }

    public CharSequence subSequence(int start, int end) {
        if (start < 0)
            throw new StringIndexOutOfBoundsException(start);
        if (end > 1)
            throw new StringIndexOutOfBoundsException(end);
        if (start > end)
            throw new StringIndexOutOfBoundsException(end - start);
        return this;
    }

    @Override
    public String toString() {
        return new String(new char[]{ch});
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (srcBegin < 0)
            throw new StringIndexOutOfBoundsException(srcBegin);
        if ((srcEnd < 0) || (srcEnd > 1))
            throw new StringIndexOutOfBoundsException(srcEnd);
        if (srcBegin > srcEnd)
            throw new StringIndexOutOfBoundsException("srcBegin > srcEnd");
        dst[dstBegin] = ch;
    }
}