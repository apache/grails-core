/*
 * Copyright 2008 the original author or authors.
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
package grails.test;

import groovy.lang.Closure;
import groovy.mock.interceptor.LooseExpectation;
import groovy.mock.interceptor.StrictExpectation;

/**
 * This closure proxy stores an expectation and checks it before each
 * call to the target closure. It is used by the Grails mocking framework.
 *
 * @author Peter Ledbrook
 */
public class MockClosureProxy extends AbstractClosureProxy {

    private static final long serialVersionUID = -1373536726646006925L;

    String methodName;
    Object expectation;

    /**
     * Constructor.
     * @param target
     * @param methodName
     * @param expectation
     */
    public MockClosureProxy(Closure<?> target, String methodName, Object expectation) {
        super(target);
        this.methodName = methodName;
        this.expectation = expectation;

        if (!(expectation instanceof LooseExpectation) && !(expectation instanceof StrictExpectation)) {
            throw new IllegalArgumentException(
                    "Expectation must be either groovy.mock.interceptor.LooseExpectation or " +
                    " groovy.mock.interceptor.StrictExpectation (actual class: " +
                    expectation.getClass() + ")");
        }
    }

    /**
     * Checks whether the target "method" is expected or not, on the
     * basis that this closure is mocking a method with the name
     * <code>methodName</code>.
     * @param args The arguments to the "method" (actually
     * the argumetns to the target closure invocation).
     */
    @Override
    protected void doBeforeCall(Object[] args) {
        if (expectation instanceof LooseExpectation) {
            ((LooseExpectation) expectation).match(methodName);
        }
        else {
            ((StrictExpectation) expectation).match(methodName);
        }
    }

    /**
     * Empty implementation.
     * @param args The arguments to the target closure.
     */
    @Override
    protected void doAfterCall(Object[] args) {
        // do nothing
    }

    /**
     * Creates a new <code>MockClosureProxy</code> wrapping the given
     * closure.
     * @param c The closure to wrap.
     * @return the new proxy.
     */
    @Override
    protected Closure<?> createWrapper(Closure<?> c) {
        return new MockClosureProxy(c, methodName, expectation);
    }
}
