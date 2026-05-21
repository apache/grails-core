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

package org.grails.databinding.compiler

import org.codehaus.groovy.control.MultipleCompilationErrorsException

import spock.lang.Issue
import spock.lang.Specification

class BindingFormatCompilationErrorsSpec extends Specification {

	@Issue('GRAILS-11321')
    void 'Test compiling @BindingFormat with no code and no value'() {
        given:
        def gcl = new GroovyClassLoader()
        
        when:
        gcl.parseClass '''
package com.demo

class SomeClass {
        @grails.databinding.BindingFormat
        String someProperty
}
'''
        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains 'The @BindingFormat annotation on the field [someProperty] in class [com.demo.SomeClass] must provide a value for either the value() or code() attribute.'
    }

	void 'Test compiling @BindingFormat with code'() {
		given:
		def gcl = new GroovyClassLoader()
		
		when:
		def c = gcl.parseClass '''
package com.demo

class SomeClass {
		@grails.databinding.BindingFormat(code='foo')
		String someProperty
}
'''
		then:
		c
	}

	void 'Test compiling @BindingFormat with value'() {
		given:
		def gcl = new GroovyClassLoader()
		
		when:
		def c = gcl.parseClass '''
package com.demo

class SomeClass {
		@grails.databinding.BindingFormat('foo')
		String someProperty
}
'''
		then:
		c
	}
}
