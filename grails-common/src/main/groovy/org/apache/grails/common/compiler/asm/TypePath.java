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
package org.apache.grails.common.compiler.asm;

/**
 * The path to a type argument, wildcard bound, array element type, or static
 * inner type within an enclosing type.
 *
 * @author Eric Bruneton
 */
class TypePath {

    /**
     * A type path step that steps into the element type of an array type. See
     * {@link #getStep getStep}.
     */
    public static final int ARRAY_ELEMENT = 0;

    /**
     * A type path step that steps into the nested type of a class type. See
     * {@link #getStep getStep}.
     */
    public static final int INNER_TYPE = 1;

    /**
     * A type path step that steps into the bound of a wildcard type. See
     * {@link #getStep getStep}.
     */
    public static final int WILDCARD_BOUND = 2;

    /**
     * A type path step that steps into a type argument of a generic type. See
     * {@link #getStep getStep}.
     */
    public static final int TYPE_ARGUMENT = 3;

    /**
     * The byte array where the path is stored, in Java class file format.
     */
    byte[] b;

    /**
     * The offset of the first byte of the type path in 'b'.
     */
    int offset;

    /**
     * Creates a new type path.
     *
     * @param b      the byte array containing the type path in Java class file
     *               format.
     * @param offset the offset of the first byte of the type path in 'b'.
     */
    TypePath(byte[] b, int offset) {
        this.b = b;
        this.offset = offset;
    }

    /**
     * Returns the length of this path.
     *
     * @return the length of this path.
     */
    public int getLength() {
        return b[offset];
    }

    /**
     * Returns the value of the given step of this path.
     *
     * @param index an index between 0 and {@link #getLength()}, exclusive.
     * @return {@link #ARRAY_ELEMENT ARRAY_ELEMENT}, {@link #INNER_TYPE
     * INNER_TYPE}, {@link #WILDCARD_BOUND WILDCARD_BOUND}, or
     * {@link #TYPE_ARGUMENT TYPE_ARGUMENT}.
     */
    public int getStep(int index) {
        return b[offset + 2 * index + 1];
    }

    /**
     * Returns the index of the type argument that the given step is stepping
     * into. This method should only be used for steps whose value is
     * {@link #TYPE_ARGUMENT TYPE_ARGUMENT}.
     *
     * @param index an index between 0 and {@link #getLength()}, exclusive.
     * @return the index of the type argument that the given step is stepping
     * into.
     */
    public int getStepArgument(int index) {
        return b[offset + 2 * index + 2];
    }

    /**
     * Converts a type path in string form, in the format used by
     * {@link #toString()}, into a TypePath object.
     *
     * @param typePath a type path in string form, in the format used by
     *                 {@link #toString()}. May be null or empty.
     * @return the corresponding TypePath object, or null if the path is empty.
     */
    public static TypePath fromString(final String typePath) {
        if (typePath == null || typePath.length() == 0) {
            return null;
        }
        int n = typePath.length();
        ByteVector out = new ByteVector(n);
        out.putByte(0);
        for (int i = 0; i < n; ) {
            char c = typePath.charAt(i++);
            if (c == '[') {
                out.put11(ARRAY_ELEMENT, 0);
            } else if (c == '.') {
                out.put11(INNER_TYPE, 0);
            } else if (c == '*') {
                out.put11(WILDCARD_BOUND, 0);
            } else if (c >= '0' && c <= '9') {
                int typeArg = c - '0';
                while (i < n && (c = typePath.charAt(i)) >= '0' && c <= '9') {
                    typeArg = typeArg * 10 + c - '0';
                    i += 1;
                }
                if (i < n && typePath.charAt(i) == ';') {
                    i += 1;
                }
                out.put11(TYPE_ARGUMENT, typeArg);
            }
        }
        out.data[0] = (byte) (out.length / 2);
        return new TypePath(out.data, 0);
    }

    /**
     * Returns a string representation of this type path. {@link #ARRAY_ELEMENT
     * ARRAY_ELEMENT} steps are represented with '[', {@link #INNER_TYPE
     * INNER_TYPE} steps with '.', {@link #WILDCARD_BOUND WILDCARD_BOUND} steps
     * with '*' and {@link #TYPE_ARGUMENT TYPE_ARGUMENT} steps with their type
     * argument index in decimal form followed by ';'.
     */
    @Override
    public String toString() {
        int length = getLength();
        StringBuilder result = new StringBuilder(length * 2);
        for (int i = 0; i < length; ++i) {
            switch (getStep(i)) {
                case ARRAY_ELEMENT:
                    result.append('[');
                    break;
                case INNER_TYPE:
                    result.append('.');
                    break;
                case WILDCARD_BOUND:
                    result.append('*');
                    break;
                case TYPE_ARGUMENT:
                    result.append(getStepArgument(i)).append(';');
                    break;
                default:
                    result.append('_');
            }
        }
        return result.toString();
    }
}
