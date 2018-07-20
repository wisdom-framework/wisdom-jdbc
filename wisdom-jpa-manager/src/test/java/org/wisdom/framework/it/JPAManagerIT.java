/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
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
 * #L%
 */
package org.wisdom.framework.it;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ow2.chameleon.testing.helpers.OSGiHelper;
import org.wisdom.api.model.Crud;
import org.wisdom.api.model.EntityFilter;
import org.wisdom.api.model.HasBeenRollBackException;
import org.wisdom.framework.entities.ClassRoom;
import org.wisdom.framework.entities.Student;
import org.wisdom.framework.entities.vehicules.Car;
import org.wisdom.framework.entities.vehicules.Driver;
import org.wisdom.framework.transaction.impl.TransactionManagerService;
import org.wisdom.test.parents.Filter;
import org.wisdom.test.parents.WisdomTest;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.transaction.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class JPAManagerIT extends WisdomTest {

    @Inject
    @Filter("(persistent.unit.name=jta-unit)")
    EntityManagerFactory jtaEmf;

    @Inject
    @Filter("(persistent.unit.name=local-unit)")
    EntityManagerFactory localEmf;

    @Inject
    @Filter("(persistent.unit.name=local-unit)")
    EntityManager localEm;

    @Inject
    @Filter("(persistent.unit.name=jta-unit)")
    EntityManager jtaEm;

    @Inject
    @Filter("(entity.classname=org.wisdom.framework.entities.Student)")
    Crud<Student, Integer> students;

    @Inject
    @Filter("(entity.classname=org.wisdom.framework.entities.vehicules.Car)")
    Crud<Car, Long> cars;

    private OSGiHelper helper;

    @Before
    public void setUp() {
        helper = new OSGiHelper(context);
    }

    @After
    public void tearDown() {
        helper.dispose();
    }

    @Test
    public void checkThatServicesAreAvailable() {
        assertThat(jtaEmf).isNotNull();
        assertThat(localEmf).isNotNull();
        assertThat(jtaEm).isNotNull();
        assertThat(localEm).isNotNull();
        assertThat(students).isNotNull();
        assertThat(cars).isNotNull();

        // For classloading purpose retrieve the transaction manager as follows:
        assertThat(TransactionManagerService.get()).isNotNull();
    }

    @Test
    public void checkBehaviorOfTheLocalUnitUsingEntityManager() {
        localEm.getTransaction().begin();
        create(localEm);
        localEm.getTransaction().commit();

        // Find all rooms
        localEm.getTransaction().begin();
        List<ClassRoom> rooms = getAllRooms(localEm);
        List<Student> students = getAllStudents(localEm);
        localEm.getTransaction().commit();

        assertThat(rooms).hasSize(3);
        assertThat(students).hasSize(4);

        assertThat(getRoom("200", rooms).getStudents()).hasSize(1);
        assertThat(getRoom("200", rooms).getStudents().get(0).getName()).isEqualTo("Jen");
        assertThat(getRoom("018", rooms).getStudents()).hasSize(2);

        // Delete everything
        localEm.getTransaction().begin();
        for (Student s : getAllStudents(localEm)) {
            localEm.remove(s);
        }
        for (ClassRoom s : getAllRooms(localEm)) {
            localEm.remove(s);
        }
        localEm.getTransaction().commit();

        localEm.getTransaction().begin();
        rooms = getAllRooms(localEm);
        students = getAllStudents(localEm);
        localEm.getTransaction().commit();

        assertThat(rooms).hasSize(0);
        assertThat(students).hasSize(0);
    }

    @Test
    public void checkBehaviorOfTheJTAUnitUsingEntityManager() throws SystemException, NotSupportedException,
            HeuristicRollbackException, HeuristicMixedException, RollbackException {
        TransactionManager transactionManager = TransactionManagerService.get();
        transactionManager.begin();
        createFleet(jtaEm);
        transactionManager.commit();

        // Find all rooms
        transactionManager.begin();
        List<Car> cars = getAllCars(jtaEm);
        List<Driver> drivers = getAllDrivers(jtaEm);
        transactionManager.commit();

        assertThat(cars).hasSize(1);
        assertThat(drivers).hasSize(1);

        // Delete everything
        transactionManager.begin();
        for (Driver s : getAllDrivers(jtaEm)) {
            jtaEm.remove(s);
        }
        for (Car s : getAllCars(jtaEm)) {
            jtaEm.remove(s);
        }
        transactionManager.commit();

        transactionManager.begin();
        cars = getAllCars(jtaEm);
        drivers = getAllDrivers(jtaEm);
        transactionManager.commit();

        assertThat(cars).hasSize(0);
        assertThat(drivers).hasSize(0);
    }

    @Test
    public void checkBehaviorOfTheJTAUnitUsingEntityManagerFactory() throws SystemException, NotSupportedException,
            HeuristicRollbackException, HeuristicMixedException, RollbackException {
        TransactionManager transactionManager = TransactionManagerService.get();
        transactionManager.begin();
        EntityManager em = jtaEmf.createEntityManager();
        createFleet(em);
        transactionManager.commit();

        transactionManager.begin();
        em = jtaEmf.createEntityManager();
        List<Car> cars = getAllCars(em);
        List<Driver> drivers = getAllDrivers(em);
        transactionManager.commit();

        assertThat(cars).hasSize(1);
        assertThat(drivers).hasSize(1);

        // Delete everything
        transactionManager.begin();
        em = jtaEmf.createEntityManager();
        for (Driver s : getAllDrivers(em)) {
            em.remove(s);
        }
        for (Car s : getAllCars(em)) {
            em.remove(s);
        }
        transactionManager.commit();

        transactionManager.begin();
        em = jtaEmf.createEntityManager();
        cars = getAllCars(em);
        drivers = getAllDrivers(em);
        transactionManager.commit();

        assertThat(cars).hasSize(0);
        assertThat(drivers).hasSize(0);
    }

    @Test
    public void checkBehaviorOfTheLocalUnitUsingEntityManagerFactory() {
        EntityManager em = localEmf.createEntityManager();
        em.getTransaction().begin();
        create(em);
        em.getTransaction().commit();
        //em.close();

        // Find all rooms
        em.getTransaction().begin();
        List<ClassRoom> rooms = getAllRooms(em);
        List<Student> students = getAllStudents(em);
        em.getTransaction().commit();

        assertThat(rooms).hasSize(3);
        assertThat(students).hasSize(4);

        assertThat(getRoom("200", rooms).getStudents()).hasSize(1);
        assertThat(getRoom("200", rooms).getStudents().get(0).getName()).isEqualTo("Jen");
        assertThat(getRoom("018", rooms).getStudents()).hasSize(2);

        // Delete everything
        em.getTransaction().begin();
        for (Student s : getAllStudents(em)) {
            em.remove(s);
        }
        for (ClassRoom s : getAllRooms(em)) {
            em.remove(s);
        }
        em.getTransaction().commit();

        em.getTransaction().begin();
        rooms = getAllRooms(em);
        students = getAllStudents(em);
        em.getTransaction().commit();

        assertThat(rooms).hasSize(0);
        assertThat(students).hasSize(0);
        em.close();
    }

    @Test
    public void testStudentCrudService() throws HasBeenRollBackException {
        Student student1 = new Student();
        student1.setName("A");
        students.save(student1);

        Student student2 = new Student();
        student2.setName("B");
        Student student3 = new Student();
        student3.setName("C");
        students.save(ImmutableList.of(student2, student3));

        assertThat(students.count()).isEqualTo(3);
        assertThat(students.findAll()).hasSize(3);
        assertThat(students.findAll(new EntityFilter<Student>() {
            @Override
            public boolean accept(Student student) {
                return !student.getName().equalsIgnoreCase("A");
            }
        })).hasSize(2);
        assertThat(students.findOne(new EntityFilter<Student>() {
            @Override
            public boolean accept(Student student) {
                return student.getName().equalsIgnoreCase("A");
            }
        })).isNotNull();
        
        assertThat(students.findOne(student1.getId())).isNotNull();
        assertThat(students.findOne(-1)).isNull();

        assertThat(students.exists(student1.getId())).isTrue();
        assertThat(students.exists(-1)).isFalse();

        assertThat(students.getEntityClass()).isEqualTo(Student.class);
        assertThat(students.getIdClass()).isEqualTo(Integer.TYPE);
        assertThat(students.getRepository()).isNotNull();
        assertThat(students.getRepository().get()).isEqualTo(localEm);


        // Check update
        assertThat(students.findOne(new EntityFilter<Student>() {
            @Override
            public boolean accept(Student student) {
                return student.getName().equalsIgnoreCase("D");
            }
        })).isNull();
        student1.setName("D");
        students.save(student1);
        assertThat(students.findOne(new EntityFilter<Student>() {
            @Override
            public boolean accept(Student student) {
                return student.getName().equalsIgnoreCase("D");
            }
        })).isNotNull();
        assertThat(students.findOne(new EntityFilter<Student>() {
            @Override
            public boolean accept(Student student) {
                return student.getName().equalsIgnoreCase("A");
            }
        })).isNull();

        students.delete(student2);
        assertThat(students.count()).isEqualTo(2);
        students.delete(student3.getId());
        assertThat(students.count()).isEqualTo(1);
        students.delete(ImmutableList.of(student3, student1));
        assertThat(students.count()).isEqualTo(0);
    }

    @Test
    public void testCarCrudService() throws HasBeenRollBackException {
        Car car1 = new Car();
        car1.setName("A");
        cars.save(car1);

        Car car2 = new Car();
        car2.setName("B");
        Car car3 = new Car();
        car3.setName("C");
        cars.save(ImmutableList.of(car2, car3));

        assertThat(cars.count()).isEqualTo(3);
        assertThat(cars.findAll()).hasSize(3);
        assertThat(cars.findAll(new EntityFilter<Car>() {
            @Override
            public boolean accept(Car car) {
                return !car.getName().equalsIgnoreCase("A");
            }
        })).hasSize(2);
        assertThat(cars.findOne(new EntityFilter<Car>() {
            @Override
            public boolean accept(Car student) {
                return student.getName().equalsIgnoreCase("A");
            }
        })).isNotNull();

        assertThat(cars.findOne(car1.getId())).isNotNull();
        assertThat(cars.findOne(-1l)).isNull();

        assertThat(cars.exists(car1.id)).isTrue();
        assertThat(cars.exists(-1l)).isFalse();

        assertThat(cars.getEntityClass()).isEqualTo(Car.class);
        assertThat(cars.getIdClass()).isEqualTo(Long.class);
        assertThat(cars.getRepository()).isNotNull();
        assertThat(cars.getRepository().get()).isEqualTo(jtaEm);

        cars.delete(car2.id);

            cars.executeTransactionalBlock(new Runnable() {
                @Override
                public void run() {
                    try {
                        Iterable<Car> list = cars.findAll();
                        assertThat(Iterables.size(list)).isEqualTo(2);

                        for (Car c : list) {
                            cars.delete(c);
                        }

                        assertThat(cars.count()).isEqualTo(0);

                    } catch (HasBeenRollBackException e) {
                        System.out.println("HasBeenRollBackException");
                    }
                }
            });


        assertThat(cars.count()).isEqualTo(0);

    }

    private void create(EntityManager em) {
        ClassRoom room1 = new ClassRoom();
        room1.setBuilding("Bat C");
        room1.setName("200");

        ClassRoom room2 = new ClassRoom();
        room2.setBuilding("Bat F");
        room2.setName("018");

        ClassRoom room3 = new ClassRoom();
        room3.setBuilding("Bat F");
        room3.setName("021");

        Student student1 = new Student();
        student1.setName("Jen");

        Student student2 = new Student();
        student2.setName("John");

        Student student3 = new Student();
        student3.setName("Gin");

        Student student4 = new Student();
        student4.setName("Joe");

        room1.setStudents(ImmutableList.of(student1));
        room2.setStudents(ImmutableList.of(student2, student3));
        room3.setStudents(ImmutableList.of(student4));

        // Save everything
        em.persist(room1);
        em.persist(room2);
        em.persist(room3);
    }

    private void createFleet(EntityManager em) {
        Driver d1 = new Driver();
        d1.setName("clement");

        Car car = new Car();
        car.setDrivers(ImmutableList.of(d1));
        car.setName("Clio");

        em.persist(car);
    }


    private ClassRoom getRoom(String name, List<ClassRoom> rooms) {
        for (ClassRoom cr : rooms) {
            if (cr.getName().equals(name)) {
                return cr;
            }
        }
        return null;
    }

    public List<ClassRoom> getAllRooms(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ClassRoom> cq = cb.createQuery(ClassRoom.class);
        Root<ClassRoom> rootEntry = cq.from(ClassRoom.class);
        CriteriaQuery<ClassRoom> all = cq.select(rootEntry);
        TypedQuery<ClassRoom> allQuery = em.createQuery(all);
        return allQuery.getResultList();
    }

    public List<Student> getAllStudents(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Student> cq = cb.createQuery(Student.class);
        Root<Student> rootEntry = cq.from(Student.class);
        CriteriaQuery<Student> all = cq.select(rootEntry);
        TypedQuery<Student> allQuery = em.createQuery(all);
        return allQuery.getResultList();
    }

    public List<Car> getAllCars(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Car> cq = cb.createQuery(Car.class);
        Root<Car> rootEntry = cq.from(Car.class);
        CriteriaQuery<Car> all = cq.select(rootEntry);
        TypedQuery<Car> allQuery = em.createQuery(all);
        return allQuery.getResultList();
    }

    public List<Driver> getAllDrivers(EntityManager em) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Driver> cq = cb.createQuery(Driver.class);
        Root<Driver> rootEntry = cq.from(Driver.class);
        CriteriaQuery<Driver> all = cq.select(rootEntry);
        TypedQuery<Driver> allQuery = em.createQuery(all);
        return allQuery.getResultList();
    }

}
