/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.grails.datastore.gorm.mongo.java;

import org.grails.datastore.gorm.mongo.Book;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.mongo.MongoDatastore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class MongoResultListJavaForEachTest {

    MongoDatastore datastore;
    PlatformTransactionManager transactionManager;
    TransactionStatus transaction;
    @Before
    public void setup() {
        datastore = new MongoDatastore(Book.class);
        transactionManager = datastore.getTransactionManager();
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());

    }

    @After
    public void cleanup() {
        transactionManager.commit(transaction);
        if (datastore != null) {
            datastore.close();
        }
    }

    // Test for issue https://github.com/grails/gorm-mongodb/issues/45
    @Test
    public void testForEachWithMongoResultList() {
        PersistentEntity entity = datastore.getMappingContext().getPersistentEntity(Book.class.getName());
        datastore.getCollection(entity).drop();
        Book b1 = new Book();
        b1.setName("The Stand");

        Session session = datastore.getCurrentSession();
        session.persist(b1);

        Book b2 = new Book();
        b2.setName("The Stand");
        session.persist(b2);
        session.flush();
        session.clear();


        List<Book> results = session.createQuery(Book.class).max(10).list();
        int total = 0;
        ArrayList<Book> anotherList = new ArrayList<>();
        anotherList.add(new Book());

        for (Object result : results) {
            results.equals(anotherList); // reproduce bug
            total++;
        }

        assertEquals(2, total);
    }
}
