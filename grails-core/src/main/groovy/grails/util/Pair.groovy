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

package grails.util

import groovy.transform.CompileStatic

@CompileStatic
class Pair<A, B> {

    final A aValue
    final B bValue

    Pair(A aValue, B bValue) {
        this.aValue = aValue
        this.bValue = bValue
    }

    A getaValue() {
        return aValue
    }

    B getbValue() {
        return bValue
    }

    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((aValue == null) ? 0 : aValue.hashCode())
        result = prime * result + ((bValue == null) ? 0 : bValue.hashCode())
        return result
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        Pair other = (Pair) obj
        if (aValue == null) {
            if (other.aValue != null)
                return false
        }
        else if (!aValue.equals(other.aValue))
            return false
        if (bValue == null) {
            if (other.bValue != null)
                return false
        }
        else if (!bValue.equals(other.bValue))
            return false
        return true
    }

    @Override
    String toString() {
        return 'TupleKey [aValue=' + aValue + ', bValue=' + bValue + ']'
    }

}
