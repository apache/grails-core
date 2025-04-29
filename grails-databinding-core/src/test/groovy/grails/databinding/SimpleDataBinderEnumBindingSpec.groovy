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
package grails.databinding

import grails.databinding.SimpleDataBinder;
import grails.databinding.SimpleMapDataBindingSource;
import spock.lang.Issue
import spock.lang.Specification

class SimpleDataBinderEnumBindingSpec extends Specification {
    @Issue('GRAILS-10979')
    void 'Test binding to a List of enum'() {
        given:
        def binder = new SimpleDataBinder()
        def holder = new HatSizeHolder()
        
        when:
        binder.bind holder, ['sizes[0]': 'LARGE', 'sizes[1]': 'SMALL'] as SimpleMapDataBindingSource
        
        then:
        holder.sizes?.size() == 2
        holder.sizes[0] == HatSize.LARGE
        holder.sizes[1] == HatSize.SMALL
    }

}

enum HatSize {
    SMALL, MEDIUM, LARGE
}

class HatSizeHolder {
    List<HatSize> sizes
}
