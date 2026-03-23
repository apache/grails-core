<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# GORM for Hibernate 7
This project implements [GORM](https://gorm.grails.org) for the Hibernate 7.

With the removal of Criterion API in Hibernate 7, we wanted to continue to support the DetachedCriteia in GORM as much as possible. We also wanted to encapsulate the JPA Criteria Building in one class so the following was done:
* DetachedCriteria holds almost all the state of the Query being built. It hold the target class for the query. It does not hold a session.
* HibernateQuery has a session and holds the DetachedCriteria and is a thin wrapper for it. Calling list or singleResult will internally create the Query and execute it. 
* HibernateCriteriaBuilder is a thin wrapper around HibernateQuery. Its main function is to use closures to populate the Hibernate Query and execute it at the end of the closure.
* Only the grails-datastore-gorm-hibernate7 module is being developed at the time.

For testing the following was done:
* Used testcontainers for specific  tests instead of h2 to verify features not supported by h2.
* A more opinionated and fluent HibernateGormDatastoreSpec is used for the specifications.









