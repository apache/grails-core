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

import spock.lang.Specification


class BindInitializerSpec extends Specification {
    
    void 'Test BindInitializer for specific property'() {
        given:
            def binder = new SimpleDataBinder()
            def obj = new ClassWithBindInitializerOnProperty()
        when:
            binder.bind(obj, new SimpleMapDataBindingSource(['association': [valueBound:'valueBound']]))

        then:
            obj.association.valueBound == 'valueBound'
            obj.association.valueInitialized == 'valueInitialized'
    }


    static class ReferencedClass{
        String valueInitialized
        String valueBound
    }
    class ClassWithBindInitializerOnProperty {
        @BindInitializer({
            obj -> 
                new ReferencedClass(valueInitialized:'valueInitialized')
        })
        ReferencedClass association
    }
}
