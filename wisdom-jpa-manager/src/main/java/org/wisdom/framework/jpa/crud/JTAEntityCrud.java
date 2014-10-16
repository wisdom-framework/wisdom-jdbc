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
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * An implementation of the {@link org.wisdom.api.model.Crud} service for entities from persistent units using JTA
 * transactions.
 */
public class JTAEntityCrud<T, I extends Serializable> extends AbstractJTACrud<T, I> implements Crud<T, I> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JTAEntityCrud.class);

    /**
     * The transaction manager.
     */
    private final TransactionManager transaction;

    /**
     * Creates a new instance of {@link JTAEntityCrud}.
     *
     * @param pu          the persistent unit name
     * @param em          the entity manager
     * @param transaction the transaction manager
     * @param entity      the class of the entity
     * @param id          the primary key class
     * @param repository  the repository
     */
    public JTAEntityCrud(String pu, EntityManager em, TransactionManager transaction,
                         Class<T> entity, Class<I> id, Repository repository) {
        super(pu, em, entity, id, repository);
        this.transaction = transaction;
    }

    /**
     * @return The {@link org.wisdom.api.model.TransactionManager} used by this crud in order to run the transaction.
     */
    @Override
    public org.wisdom.api.model.TransactionManager getTransactionManager() {
        return new org.wisdom.api.model.TransactionManager() {
            @Override
            public void begin() throws InitTransactionException {
                try {
                    transaction.begin();
                } catch (NotSupportedException | SystemException e) {
                    throw new InitTransactionException("Cannot begin a transaction", e);
                }
            }

            @Override
            public void commit() throws Exception {
                transaction.commit();
            }

            @Override
            public void rollback() throws RollBackHasCauseAnException {
                try {
                    transaction.rollback();
                } catch (SystemException e) {
                    throw new RollBackHasCauseAnException("Cannot rollback the transaction", e);
                }
            }

            @Override
            public void close() {
                try {
                    // Suspend the transaction.
                    transaction.suspend();
                } catch (SystemException e) {
                    // Ignore it.
                }
            }
        };
    }

    @Override
    protected <X> X inTransaction(Callable<X> task) {
        boolean transactionBegunLocally = false;
        try {
            if (transaction.getTransaction() == null) {
                transaction.begin();
                transactionBegunLocally = true;
            }
            X result;
            try {
                result = task.call();
            } catch (Exception e) {
                // Exception thrown by the block
                LOGGER.error("[Unit : {}, Entity: {}, " +
                                "Id: {}] - the transactional block has thrown an exception, rollback the transaction",
                        pu, entity.getName(), idClass.getName(), e);
                if (transactionBegunLocally) {
                    transaction.rollback();
                } else {
                    transaction.getTransaction().setRollbackOnly();
                }
                return null;
            }
            if (transactionBegunLocally) {
                transaction.commit();
            }
            return result;
        } catch (Exception e) {
            // The transaction management has thrown an exception
            LOGGER.error("[Unit : {}, Entity: {}, " +
                    "Id: {}] - Cannot execute JPA query", pu, entity.getName(), idClass.getName(), e);
        }
        return null;

    }


}
