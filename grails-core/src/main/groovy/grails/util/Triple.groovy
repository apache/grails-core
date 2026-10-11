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
class Triple<A, B, C> {

    final A aValue
    final B bValue
    final C cValue

    Triple(A aValue, B bValue, C cValue) {
        this.aValue = aValue
        this.bValue = bValue
        this.cValue = cValue
    }

    A getaValue() {
        return aValue
    }

    B getbValue() {
        return bValue
    }

    C getcValue() {
        return cValue
    }

    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((aValue == null) ? 0 : aValue.hashCode())
        result = prime * result + ((bValue == null) ? 0 : bValue.hashCode())
        result = prime * result + ((cValue == null) ? 0 : cValue.hashCode())
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
        Triple other = (Triple) obj
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
        if (cValue == null) {
            if (other.cValue != null)
                return false
        }
        else if (!cValue.equals(other.cValue))
            return false
        return true
    }

    @Override
    String toString() {
        return 'Triple [aValue=' + aValue + ', bValue=' + bValue + ', cValue=' + cValue + ']'
    }

}
