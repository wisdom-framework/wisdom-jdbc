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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wisdom.api.model.Crud;
import org.wisdom.api.model.InitTransactionException;
import org.wisdom.api.model.Repository;
import org.wisdom.api.model.RollBackHasCauseAnException;

import javax.persistence.EntityManager;
import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * An implementation of the {@link org.wisdom.api.model.Crud} service for entities from persistent units using local
 * transactions.
 */
public class LocalEntityCrud<T, I extends Serializable> extends AbstractJTACrud<T, I> implements Crud<T, I> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalEntityCrud.class);

    public LocalEntityCrud(String pu, EntityManager em,
                           Class<T> entity, Class<I> id, Repository repository) {
        super(pu, em, entity, id, repository);
    }

    @Override
    public org.wisdom.api.model.TransactionManager getTransactionManager() {
        return new org.wisdom.api.model.TransactionManager() {
            @Override
            public void begin() throws InitTransactionException {
                entityManager.getTransaction().begin();
            }

            @Override
            public void commit() throws Exception {
                entityManager.getTransaction().commit();
            }

            @Override
            public void rollback() throws RollBackHasCauseAnException {
                entityManager.getTransaction().rollback();
            }

            @Override
            public void close() {
                // Do nothing.
            }
        };
    }

    protected <X> X inTransaction(Callable<X> task) {
        try {
            boolean transactionBegunHere = false;
            if (!entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().begin();
                transactionBegunHere = true;
            }
            X result;
            try {
                result = task.call();
            } catch (Throwable e) {
                e.printStackTrace();
                LOGGER.error("[Unit : {}, Entity: {}, " +
                                "Id: {}] - Cannot execute query, rollback the transaction", pu, entity.getName(),
                        idClass.getName(), e);
                if (transactionBegunHere) {
                    entityManager.getTransaction().rollback();
                } else {
                    entityManager.getTransaction().setRollbackOnly();
                }
                return null;
            }

            if (transactionBegunHere) {
                entityManager.getTransaction().commit();
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("[Unit : {}, Entity: {}, " +
                    "Id: {}] - Cannot execute query", pu, entity.getName(), idClass.getName(), e);
        }
        return null;
    }
}
