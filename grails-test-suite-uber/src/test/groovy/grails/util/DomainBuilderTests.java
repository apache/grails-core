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
package grails.util;

import groovy.test.GroovyTestCase;
import groovy.util.ObjectGraphBuilder;

import java.util.ArrayList;
import java.util.List;

public class DomainBuilderTests extends GroovyTestCase {

    private DomainBuilder builder;
    private ObjectGraphBuilder.ChildPropertySetter childPropertySetter;
    private Employer employer;

    @Override
    protected void setUp() throws Exception {
        builder = new DomainBuilder();
        childPropertySetter = builder.getChildPropertySetter();

        employer = new Employer();
        employer.setName("Spacely Space Sprockets");
    }

    @SuppressWarnings("rawtypes")
    public void testChildIsCollection() throws Exception {
        Employee one = new Employee();
        one.setName("Cosmo");

        Employee two = new Employee();
        two.setName("George");

        childPropertySetter.setChild(employer, one, null, "employees");
        childPropertySetter.setChild(employer, two, null, "employees");

        List employees = employer.getEmployees();

        assertNull(employer.getAddress());
        assertEquals(2, employees.size());

        assertEquals(one.getName(), ((Employee)employees.get(0)).getName());
        assertEquals(two.getName(), ((Employee)employees.get(1)).getName());
    }

    public void testChildIsNotCollection() throws Exception {
        Address address = new Address();
        address.setStreet("Park Pl.");

        childPropertySetter.setChild(employer, address, null, "address");

        Address a = employer.getAddress();

        assertEquals(address.getStreet(), a.getStreet());

        assertEquals(0, employer.getEmployees().size());
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public static class Employer {
        private String name = null;
        private Address address = null;
        private List employees = new ArrayList();

        public void addToEmployees(Employee employee) {
            employees.add(employee);
        }

        public List getEmployees() {
            return employees;
        }

        public void setAddress(Address a) {
            address = a;
        }

        public Address getAddress() {
            return address;
        }

        public void setName(String n) {
            name = n;
        }

        public String getName() {
            return name;
        }
    }

    public static class Employee {
        private String name;
        public void setName(String n) { name = n; }
        public String getName() { return name; }
    }

    public static class Address {
        private String street;
        public void setStreet(String s) { street = s; }
        public String getStreet() { return street; }
    }
}
