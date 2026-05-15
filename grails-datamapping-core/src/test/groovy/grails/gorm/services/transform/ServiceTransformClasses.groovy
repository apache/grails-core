/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.services.transform

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.services.Query
import grails.gorm.services.Where
import grails.gorm.services.Join
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEntity
import jakarta.persistence.criteria.JoinType

@Entity
class XProj implements GormEntity<XProj> {

    String a
    String b
}

interface IXProj {
    String getB()
}

@Service(XProj)
interface XServiceProj {
    IXProj getX(String a)
}

interface HasTitleMarker {
    String getTitle()
}

@Entity
class ArticleMarker implements HasTitleMarker {

    String title
    String subtitle
}

interface ArticleProjectionMarker {
    String getTitle()
}

@Service(ArticleMarker)
interface ArticleServiceMarker {
    ArticleProjectionMarker getArticle(String title)
}

@Entity
class XTenant {

    String a
    String b
}

@Service(XTenant)
@CurrentTenant
interface XServiceTenant {
    XTenant getX(String a)
}

@Entity
class FooProt {

    String title
}

@Service(FooProt)
abstract class AbstractMyServiceProt {

    FooProt searchFoo(Serializable id) {
        find(id)
    }

    protected abstract FooProt find(Serializable id)
}

@Entity
class FooGen {

    String title
}

@Service(FooGen)
interface MyServiceGen {
    List<FooGen> findByTitleLike(String title)
}

@Entity
class FooQ {

    String title
    String name
}

interface IFooQ {
    String getTitle()
}

@Service(FooQ)
interface MyServiceQ {
    @Query('from FooQ as f where f.title like $title')
    IFooQ search(String title)
}

@Entity
class FooP {

    String title
    String name
}

interface IFooP {
    String getTitle()
}

@Service(FooP)
interface MyServiceP {
    IFooP find(String title)
}

@Entity
class FooL {

    String title
    String name
}

interface IFooL {
    String getTitle()
}

@Service(FooL)
interface MyServiceL {
    List<IFooL> find(String title)
}

@Entity
class FooD {

    String title
    String name
}

interface IFooD {
    String getTitle()
}

@Service(FooD)
interface MyServiceD {
    IFooD findByTitle(String title)
}

@Entity
class FooA {

    String title
}

@Service(FooA)
interface MyServiceA {
    List<FooA> listFoos()
}

@Entity
class BarJ {

}

@Entity
class FooJ {

    String title
    static hasMany = [bars: BarJ]
}

@Service(FooJ)
interface MyJoinServiceJ {
    @Join('bars')
    FooJ find(String title)
}

@Entity
class BarJ2 {

}

@Entity
class FooJ2 {

    String title
    static hasMany = [bars: BarJ2]
}

@Service(FooJ2)
interface MyJoinServiceJ2 {
    @Join(value='bars', type=JoinType.LEFT)
    FooJ2 findFoo(String title)
}

@Entity
class FooS {

    String title
}

@Service(FooS)
interface MyServiceS {

    @Query('from FooS as f where f.title like $pattern')
    FooS searchByTitle(String pattern)
}

@Entity
class FooProj {

    String title
    int age
}

@Service(FooProj)
abstract class MyServiceProj {

    @Query('select max(${f.age}) from ${FooProj f} where f.title like $pattern')
    abstract Object searchByTitle(String pattern)
}

@Entity
class FooU {

    String title
}

@Service(FooU)
interface MyServiceU {

    @Query('update ${FooU foo} set ${foo.title} = $newTitle where ${foo.title} = $oldTitle')
    Number updateTitle(String newTitle, String oldTitle)

    @Query('delete ${FooU foo} where ${foo.title} = $title')
    void kill(String title)
}

@Entity
class FooI {

    String title
}

@Service(FooI)
interface MyServiceI {

    @Query('update ${FooI foo} set ${foo.title} = $newTitle where $foo.id = $id')
    Number updateTitle(String newTitle, Long id)

    @Query('delete ${FooI foo} where ${foo.title} = $title')
    void kill(String title)
}

@Entity
class FooT {

    String title
}

@Service(FooT)
@Transactional('foo')
interface MyServiceT {

    @Query('update ${FooT foo} set ${foo.title} = $newTitle where ${foo.title} = $oldTitle')
    Number updateTitle(String newTitle, String oldTitle)

    @Query('delete ${FooT foo} where ${foo.title} = $title')
    void kill(String title)
}

@Entity
class FooV {

    String title
}

@Service(FooV)
interface MyServiceV {

    @Query('select $f.title from ${FooV f} where $f.title like $pattern')
    List<String> searchByTitle(String pattern)
}

@Entity
class FooW {

    String title
}

@Service(FooW)
interface MyServiceW {

    @Where({ title == pattern })
    FooW searchByTitle(String pattern)
}

@Entity
class FooAbs {

    String title
}

interface MyServiceInterface {
    Number deleteMoreFoos(String title)

    void deleteFoos(String title)

    FooAbs delete(Serializable id)

    List<FooAbs> listFoos()

    FooAbs[] listMoreFoos()

    Iterable<FooAbs> listEvenMoreFoos()

    List<FooAbs> findByTitle(String title)

    List<FooAbs> findByTitleLike(String title)

    FooAbs saveFoo(String title)
}

@Service(FooAbs)
abstract class AbstractMyServiceAbs implements MyServiceInterface {

    FooAbs readFoo(Serializable id) {
        FooAbs.read(id)
    }

    @Override
    FooAbs delete(Serializable id) {
        def foo = FooAbs.get(id)
        foo?.delete()
        foo?.title = 'DELETED'
        return foo
    }
}

@Entity
class FooInterface {

    String title
}

@Service(FooInterface)
interface MyServiceInterfaceOnly {
    Number deleteMoreFoos(String title)

    void deleteFoos(String title)

    FooInterface delete(Serializable id)

    List<FooInterface> listFoos()

    FooInterface[] listMoreFoos()

    Iterable<FooInterface> listEvenMoreFoos()

    List<FooInterface> findByTitle(String title)

    List<FooInterface> findByTitleLike(String title)

    FooInterface saveFoo(String title)
}

@Entity
class ServiceEntity {}

@Service(ServiceEntity)
class TestServiceBase {

    void doStuff() {
    }
}

@Service(ServiceEntity)
class TestServiceBase2 {

    void doStuff() {
    }
}
