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
package grails.gorm.rx.services;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.grails.datastore.gorm.transform.GormASTTransformationClass;
import rx.schedulers.Schedulers;
import org.apache.grails.common.compiler.GroovyTransformOrder;

import java.lang.annotation.*;

/**
 * A transformation that transforms the body of a method to return an Observable that runs on the IO Scheduler
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.datastore.gorm.transform.OrderedGormTransformation")
@GormASTTransformationClass("org.grails.gorm.rx.transform.RxScheduleIOTransformation")
public @interface RxSchedule {

    /**
     * @return A closure that returns the scheduler to run on. Default is {@link Schedulers#io()}
     */
    Class scheduler() default Object.class;
    /**
     * @return Whether the underlying query method returns a single result of an iterable
     */
    boolean singleResult() default false;

    default int priority() {
        return GroovyTransformOrder.RX_SCHEDULE;
    }
}
