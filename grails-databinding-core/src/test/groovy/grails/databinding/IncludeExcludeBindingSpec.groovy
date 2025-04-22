/* Copyright 2013 the original author or authors.
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
package grails.databinding

import grails.databinding.SimpleDataBinder;
import grails.databinding.SimpleMapDataBindingSource;
import spock.lang.Specification

class IncludeExcludeBindingSpec extends Specification {

    void 'Test white list'() {
        given:
        def binder = new SimpleDataBinder()
        def greek = new Greek()
        def whiteList = ['alpha', 'beta']

        when:
        binder.bind greek, new SimpleMapDataBindingSource([alpha: 1, beta: 2, gamma: 3, delta: 4]), whiteList

        then:
        greek.alpha == 1
        greek.beta == 2
        greek.gamma == null
        greek.delta == null
    }

    void 'Test black list'() {
        given:
        def binder = new SimpleDataBinder()
        def greek = new Greek()
        def blackList = ['alpha', 'beta']

        when:
        binder.bind greek, new SimpleMapDataBindingSource([alpha: 1, beta: 2, gamma: 3, delta: 4]), null, blackList

        then:
        greek.alpha == null
        greek.beta == null
        greek.gamma == 3
        greek.delta == 4
    }

    void 'Test black list overrules white list'() {
        given:
        def binder = new SimpleDataBinder()
        def greek = new Greek()
        def blackList = ['alpha', 'beta']
        def whiteList = ['alpha', 'gamma', 'delta']

        when:
        binder.bind greek, new SimpleMapDataBindingSource([alpha: 1, beta: 2, gamma: 3, delta: 4]), whiteList, blackList

        then:
        greek.alpha == null
        greek.beta == null
        greek.gamma == 3
        greek.delta == 4
    }
}

class Greek {
    def alpha
    def beta
    def gamma
    def delta
}
