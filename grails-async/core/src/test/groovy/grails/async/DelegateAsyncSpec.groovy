/*
 * Copyright 2013 SpringSource
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
package grails.async

import grails.async.decorator.PromiseDecorator
import grails.async.decorator.PromiseDecoratorProvider
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 2.3
 */
class DelegateAsyncSpec extends Specification {

    void 'Test delegate async applied to class with a method taking arguments'() {

        when: 'the DelegateAsync annotation is applied to a class'
            def mathService = new AsyncMathService()
            def p = mathService.sum(1, 2)
        
        then: 'methods from the delegate return a promise'
            p instanceof Promise

        when: 'the the value of the promise is obtained'
            def val = p.get()

        then: 'it is correct'
            val == 3
    }

    void 'Test delegate async applied to field with a method taking arguments'() {

        when: 'the DelegateAsync annotation is applied to a class'
            def mathService = new AsyncMathService2()
            def p = mathService.sum(1, 2)
        
        then: 'methods from the delegate return a promise'
            p instanceof Promise

        when: 'the the value of the promise is obtained'
            def val = p.get()

        then: 'it is correct'
            val == 3
    }

    void 'Test delegate async passes decorators to created promises if target is a DecoratorProvider'() {

        when: 'the DelegateAsync annotation is applied to a class'
            def mathService = new AsyncMathService3()
            def p = mathService.sum(1, 2)

        then: 'methods from the delegate return a promise'
            p instanceof Promise

        when: 'the the value of the promise is obtained'
            def val = p.get()

        then: 'the decorator is applied to the value'
            val == 6
    }
}

class MathService {

    Integer sum(int n1, int n2) { n1 + n2 }
    void calculate() { /* no-op */ }
    
    // having this method here makes sure that the
    // transformation can deal with copying parameters
    // that are generics placeholders
    <T> void someMethod(T arg) {}
}

@DelegateAsync(MathService)
class AsyncMathService {}

class AsyncMathService2 {
    @DelegateAsync
    MathService ms = new MathService()
}

@DelegateAsync(MathService)
class AsyncMathService3 implements PromiseDecoratorProvider {
    List<PromiseDecorator> decorators = [
        { Closure c -> return { c.call(*it) * 2 } } as PromiseDecorator
    ]
}

