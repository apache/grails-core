/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.init;

public class Start {

    public static void main(String[] args) {
        try {
            GrailsWrapper wrapper = new GrailsWrapper();
            wrapper.execute(args);
        } catch (GrailsWrapperException e) {
            // the message says what is wrong and what to do about it; like every message of the wrapper it goes to
            // standard error, since standard output is the command line's own
            System.err.println("Unable to execute Grails Wrapper: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unable to execute Grails Wrapper: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
