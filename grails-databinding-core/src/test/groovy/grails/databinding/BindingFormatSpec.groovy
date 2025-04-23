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

import grails.databinding.errors.BindingError
import grails.databinding.events.DataBindingListenerAdapter
import spock.lang.Specification

import java.text.ParseException

class BindingFormatSpec extends Specification {

	void 'bind to a date'() {
		given:
		def binder = new SimpleDataBinder()
		def obj = new SomeWidget()
		def listener = new DateBindingListener()

		when:
		binder.bind obj, [birthDate: '11/15/1969'] as SimpleMapDataBindingSource, listener
		def cal = Calendar.getInstance()
		cal.time = obj.birthDate

		then:
		!listener.bindingErrors
		cal.get(Calendar.MONTH) == Calendar.NOVEMBER
		cal.get(Calendar.YEAR) == 1969
		cal.get(Calendar.DATE) == 15

		when:
		obj = new SomeWidget()
		binder.bind obj, [birthDate: '1969/11/15'] as SimpleMapDataBindingSource, listener

		then:
		!obj.birthDate
		listener.bindingErrors
		listener.bindingErrors.size() == 1

		when:
		def error = listener.bindingErrors[0]

		then:
		error.rejectedValue == '1969/11/15'
		error.propertyName == 'birthDate'
		error.cause instanceof ParseException
		error.cause.message == 'Unparseable date: "1969/11/15"'
	}
}

class SomeWidget {
	@BindingFormat('MM/dd/yyyy')
	Date birthDate
}

class DateBindingListener extends DataBindingListenerAdapter {

	def bindingErrors = []

	void bindingError(BindingError error, errors) {
		bindingErrors << error
	}
}