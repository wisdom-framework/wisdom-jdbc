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
package org.wisdom.framework.jpa.crud;

import com.google.common.collect.Iterables;
import org.wisdom.api.model.*;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Abstract implementation of the Crud service for JPA.
 * Most methods are very naive implementation and should probably be optimized.
 *
 * @param <T> the type of the entity
 * @param <I> the type of the entity primary key
 */
public abstract class AbstractJTACrud<T, I extends Serializable> implements Crud<T, I> {

    /**
     * The entity manager.
     */
    protected final EntityManager entityManager;

    /**
     * The class of the entity.
     */
    protected final Class<T> entity;

    /**
     * The class of the primary key.
     */
    protected final Class<I> idClass;

    /**
     * The name of the persistent unit.
     */
    protected final String pu;

    /**
     * The repository in which the Crud is registered.
     */
    protected final Repository repository;

    /**
     * Super constructor, that implementation must call.
     *
     * @param pu         the name of the persistence unit
     * @param em         the entity manager
     * @param entity     the class of the entity
     * @param id         the class of the primary key
     * @param repository the repository
     */
    public AbstractJTACrud(String pu, EntityManager em,
                           Class<T> entity, Class<I> id, Repository repository) {
        this.entityManager = em;
        this.entity = entity;
        this.idClass = id;
        this.pu = pu;
        this.repository = repository;
    }

    /**
     * Gets the class of the represented entity.
     *
     * @return the entity's class.
     */
    public Class<T> getEntityClass() {
        return entity;
    }

    /**
     * Gets the class of the Id used by the persistent layer.
     *
     * @return the type of the id.
     */
    public Class<I> getIdClass() {
        return idClass;
    }

    /**
     * Gets the repository storing the instances of this entity.
     *
     * @return the repository object, may be {@literal null} if there are no repository.
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * A method implemented when the Crud service is stopped.
     */
    public void dispose() {
        // Do nothing by default.
    }

    /**
     * Create a FluentTransaction with this Crud service,
     *
     * @param <R> the return type of the transaction block.
     * @return a new FluentTransaction.
     * @throws java.lang.UnsupportedOperationException       if transactions are not supported.
     * @throws org.wisdom.api.model.InitTransactionException if an exception occurred before running the transaction block.
     * @throws RollBackHasCauseAnException                   if an exception occurred when the transaction is rollback.
     */
    @Override
    public <R> FluentTransaction<R> transaction() {
        return FluentTransaction.transaction(getTransactionManager());
    }


    /**
     * Create a FluentTransaction, with the given transaction block.
     *
     * @param <R>       the return type of the transaction block
     * @param callable, The transaction block to be executed by the returned FluentTransaction
     * @return a new FluentTransaction with a transaction block already defined.
     * @throws java.lang.UnsupportedOperationException if transactions are not supported.
     */
    @Override
    public <R> FluentTransaction<R>.Intermediate transaction(Callable<R> callable) {
        return FluentTransaction.transaction(getTransactionManager()).with(callable);
    }


    /**
     * Executes the given runnable in a transaction. If the block throws an exception, the transaction is rolled back.
     * This method may not be supported by all persistent technologies, as they are not necessary supporting
     * transactions. In that case, this method throw a {@link java.lang.UnsupportedOperationException}.
     *
     * @param runnable the runnable to execute in a transaction
     * @throws HasBeenRollBackException                      if the transaction has been rollback.
     * @throws java.lang.UnsupportedOperationException       if transactions are not supported.
     * @throws org.wisdom.api.model.InitTransactionException if an exception occurred before running the transaction block.
     * @throws RollBackHasCauseAnException                   if an exception occurred when the transaction is rollback.
     */
    @Override
    public void executeTransactionalBlock(final Runnable runnable) throws HasBeenRollBackException {
        inTransaction(new Callable<Void>() {
            /**
             * The block to execute within a transaction.
             * @return {@code null}
             * @throws Exception the exception thrown by the given runnable
             */
            @Override
            public Void call() throws Exception {
                // We call the runnable directly.
                runnable.run(); //NOSONAR
                return null;
            }
        });
    }

    /**
     * Executes the given runnable in a transaction. If the block throws an exception, the transaction is rolled back.
     * This method may not be supported by all persistent technologies, as they are not necessary supporting
     * transactions. In that case, this method throw a {@link java.lang.UnsupportedOperationException}.
     *
     * @param callable the block to execute in a transaction
     * @return A the result
     * @throws HasBeenRollBackException                      if the transaction has been rollback.
     * @throws java.lang.UnsupportedOperationException       if transactions are not supported.
     * @throws org.wisdom.api.model.InitTransactionException if an exception occurred before running the transaction block.
     * @throws RollBackHasCauseAnException                   if an exception occurred when the transaction is rollback.
     */
    @Override
    public <A> A executeTransactionalBlock(Callable<A> callable) throws HasBeenRollBackException {
        return inTransaction(callable);
    }

    /**
     * Retrieves an entity by its id.
     *
     * @param id the id, must not be null
     * @return the entity instance, {@literal null} if there are no entities matching the given id.
     */
    @Override
    public T findOne(final I id) throws HasBeenRollBackException {
        return inTransaction(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return entityManager.find(entity, id);
            }
        });
    }


    /**
     * Returns all instances of the entity.
     *
     * @return the instances, empty if none.
     */
    @Override
    public Iterable<T> findAll() throws HasBeenRollBackException {
        return inTransaction(new Callable<Iterable<T>>() {
            @Override
            public Iterable<T> call() throws Exception {
                CriteriaQuery<T> cq = entityManager.getCriteriaBuilder().createQuery(entity);
                Root<T> pet = cq.from(entity);
                cq.select(pet);
                return entityManager.createQuery(cq).getResultList();
            }
        });
    }

    /**
     * Retrieves the entity matching the given filter. If several entities matches, the first is returned.
     *
     * @param filter the filter
     * @return the first matching instance, {@literal null} if none
     */
    @Override
    public T findOne(EntityFilter<T> filter) throws HasBeenRollBackException {
        for (T object : findAll()) {
            if (filter.accept(object)) {
                return object;
            }
        }
        return null;
    }

    /**
     * Returns all instances of the type with the given IDs.
     *
     * @param ids the ids.
     * @return the instances, empty if none.
     */
    @Override
    public Iterable<T> findAll(Iterable<I> ids) throws HasBeenRollBackException {
        List<T> results = new ArrayList<>();
        for (I id : ids) {
            T t = findOne(id);
            if (t != null) {
                results.add(t);
            }
        }
        return results;
    }

    /**
     * Retrieves the entities matching the given filter.
     * Be aware that the implementation may load all stored entities in memory to retrieve the right set of entities.
     *
     * @param filter the filter
     * @return the matching instances, empty if none.
     */
    @Override
    public Iterable<T> findAll(EntityFilter<T> filter) throws HasBeenRollBackException {
        List<T> results = new ArrayList<>();
        for (T object : findAll()) {
            if (filter.accept(object)) {
                results.add(object);
            }
        }
        return results;
    }

    /**
     * Deletes the given entity instance. The instance is removed from the persistent layer.
     *
     * @param t the instance
     * @return the entity instance, may be the same as the parameter t but can also be different
     */
    @Override
    public T delete(final T t) throws HasBeenRollBackException {
        return inTransaction(
                new Callable<T>() {
                    @Override
                    public T call() throws Exception {
                        T attached = getAttached(t);
                        if (attached != null) {
                            entityManager.remove(attached);
                        }
                        return t;
                    }
                }
        );
    }

    /**
     * Deletes the given entity instance (specified by its id). The instance is removed from the persistent layer.
     *
     * @param id the id
     */
    @Override
    public void delete(final I id) throws HasBeenRollBackException {
        if (entity != null) {
            inTransaction(
                    new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            final T entity = findOne(id);
                            T attached = getAttached(entity);
                            if (attached != null) {
                                entityManager.remove(attached);
                            }
                            return null;
                        }
                    }
            );
        }
    }

    /**
     * Gets the 'managed' version of the given entity instance.
     *
     * @param object the entity
     * @return the managed version, it can be the given object is the instance is not detached. If we can't find the
     * 'managed' version of the object, return {@code null}.
     */
    @SuppressWarnings("unchecked")
    private T getAttached(T object) throws HasBeenRollBackException {
        if (entityManager.contains(object)) {
            return object;
        } else {
            return findOne((I) entityManager.getEntityManagerFactory()
                    .getPersistenceUnitUtil().getIdentifier(object));
        }
    }

    /**
     * Deletes the given entity instances. The instances are removed from the persistent layer.
     *
     * @param entities the entities to remove from the persistent layer
     * @return the set of entity instances
     */
    @Override
    public Iterable<T> delete(final Iterable<T> entities) throws HasBeenRollBackException {
        return inTransaction(new Callable<Iterable<T>>() {
            @Override
            public Iterable<T> call() throws Exception {
                for (T object : entities) {
                    T attached = getAttached(object);
                    if (attached != null) {
                        entityManager.remove(attached);
                    }
                }
                return entities;
            }
        });
    }

    /**
     * Saves a given entity. Use the returned instance for further operations as the operation might have
     * changed the entity instance completely.
     * <p>
     * This method is used to save a new entity or to update it.
     *
     * @param t the instance to save
     * @return the saved entity
     */
    @Override
    public T save(final T t) throws HasBeenRollBackException {
        return inTransaction(new Callable<T>() {
            @Override
            public T call() throws Exception {
                T attached = getAttached(t);
                if (attached != null) {
                    // Update
                    entityManager.merge(t);
                } else {
                    // New object
                    entityManager.persist(t);
                }
                return t;
            }
        });
    }

    /**
     * Checks whether an entity instance with the given id exists, i.e. has been saved and is persisted.
     *
     * @param id the id, must not be null
     * @return {@literal true} if an entity with the given id exists, {@literal false} otherwise.
     */
    @Override
    public boolean exists(I id) throws HasBeenRollBackException {
        return findOne(id) != null;
    }

    /**
     * Gets the number of stored instances.
     *
     * @return the number of stored instances, 0 if none.
     */
    @Override
    public long count() throws HasBeenRollBackException {
        return Iterables.size(findAll());
    }

    /**
     * Saves all given entities. Use the returned instances for further operations as the operation might have
     * changed the entity instances completely.
     *
     * @param entities the entities to save, must not contains {@literal null} values
     * @return the saved entities
     */
    @Override
    public Iterable<T> save(final Iterable<T> entities) throws HasBeenRollBackException {
        return inTransaction(new Callable<Iterable<T>>() {
            @Override
            public Iterable<T> call() throws Exception {
                for (T object : entities) {
                    entityManager.persist(object);
                }
                return entities;
            }
        });
    }

    /**
     * Runs the given block in a transaction.
     *
     * @param task the block
     * @param <X>  the return type, can be {@code Void}
     * @return the result of the operation.
     */
    protected abstract <X> X inTransaction(Callable<X> task) throws HasBeenRollBackException;

}
