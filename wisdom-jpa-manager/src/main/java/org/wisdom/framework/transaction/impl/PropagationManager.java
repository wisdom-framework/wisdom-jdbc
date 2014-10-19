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
package org.wisdom.framework.transaction.impl;

import com.google.common.collect.ArrayListMultimap;
import org.wisdom.framework.transaction.Propagation;

import javax.transaction.*;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Class managing transaction boundaries and action to perform when we enter / leave a transactional bloc.
 */
public class PropagationManager implements Synchronization {

    private final TransactionManager manager;

    Set<Transaction> transactions = new LinkedHashSet<>();
    Set<Transaction> owned = new LinkedHashSet<>();

    ArrayListMultimap<Thread, Transaction> suspended = ArrayListMultimap.create();

    /**
     * Creates a new {@link org.wisdom.framework.transaction.impl.PropagationManager}.
     *
     * @param manager the transaction manager.
     */
    public PropagationManager(TransactionManager manager) {
        this.manager = manager;
    }

    /**
     * Checks whether or not we have an active transaction. If so, returns it.
     *
     * @return the activate transaction, {@code null} if none.
     * @throws SystemException thrown by the transaction manager to indicate that it has encountered an
     *                         unexpected error condition that prevents future transaction services from
     *                         proceeding.
     */
    private Transaction getActiveTransaction() throws SystemException {
        Transaction tx = manager.getTransaction();
        if (tx != null && tx.getStatus() != Status.STATUS_NO_TRANSACTION) {
            return tx;
        } else {
            return null;
        }
    }

    /**
     * Enters a transactional bloc.
     *
     * @param propagation    the propagation strategy
     * @param timeout        the transaction timeout
     * @param interceptionId an identifier for the interception, used for logging.
     * @throws SystemException       thrown by the transaction manager to indicate that it has encountered an
     *                               unexpected error condition that prevents future transaction services from
     *                               proceeding.
     * @throws NotSupportedException indicates that the request cannot be executed because the operation is not a
     *                               supported feature.
     * @throws RollbackException     thrown when the transaction has been marked for rollback only or the transaction
     *                               has been rolled back instead of committed.
     */
    public void onEntry(Propagation propagation, int timeout, String interceptionId) throws SystemException,
            NotSupportedException, RollbackException {

        Transaction transaction = getActiveTransaction();
        switch (propagation) {
            case REQUIRES:
                // Are we already in a transaction?
                if (transaction == null) {
                    // No, create one
                    if (timeout > 0) {
                        manager.setTransactionTimeout(timeout);
                    }
                    manager.begin();
                    Transaction tx = getActiveTransaction();
                    tx.registerSynchronization(this);
                    owned.add(tx);
                } else {
                    // Add the transaction to the transaction list
                    transactions.add(transaction);
                }
                break;
            case MANDATORY:
                if (transaction == null) {
                    // Error
                    throw new IllegalStateException("The " + interceptionId + " must be called inside a " +
                            "JTA transaction");
                } else {
                    if (transactions.add(transaction)) {
                        transaction.registerSynchronization(this);
                    }
                }
                break;
            case SUPPORTED:
                // if transaction != null, register the callback, else do nothing
                if (transaction != null) {
                    if (transactions.add(transaction)) {
                        transaction.registerSynchronization(this);
                    }
                }
                // Else do nothing.
                break;
            case NOT_SUPPORTED:
                if (transaction != null) {
                    // Suspend the current transaction.
                    suspended.put(Thread.currentThread(), transaction);
                    manager.suspend();
                }
                break;
            case NEVER:
                if (transaction != null) {
                    throw new IllegalStateException("The " + interceptionId + " must never be called inside a transaction");
                }
                break;
            case REQUIRES_NEW:
                if (transaction == null) {
                    // No current transaction, Just creates a new one
                    if (timeout > 0) {
                        manager.setTransactionTimeout(timeout);
                    }
                    manager.begin();
                    Transaction tx = getActiveTransaction();
                    owned.add(tx);
                } else {
                    // suspend the current transaction
                    suspended.put(Thread.currentThread(), manager.suspend());
                    if (timeout > 0) {
                        manager.setTransactionTimeout(timeout);
                    }
                    manager.begin();
                    owned.add(manager.getTransaction());
                }
                break;
            default:
                throw new UnsupportedOperationException("Unknown or unsupported propagation policy for " + interceptionId + " :" +
                        propagation);

        }

    }

    /**
     * Leaves a transactional bloc. This method decides what do to with the current transaction. This includes
     * committing or resuming a transaction.
     *
     * @param propagation    the propagation strategy
     * @param interceptionId an identifier for the interception, used for logging.
     * @param callback       the transaction callback
     * @throws HeuristicRollbackException  thrown by the commit operation to report that a heuristic decision was
     *                                     made and that all relevant updates have been rolled back.
     * @throws HeuristicMixedException     report that a heuristic decision was made and that some relevant updates have
     *                                     been committed and others have been rolled back
     * @throws SystemException             thrown by the transaction manager to indicate that it has encountered an
     *                                     unexpected error condition that prevents future transaction services from
     *                                     proceeding.
     * @throws InvalidTransactionException the current transaction is invalid
     */
    public void onExit(Propagation propagation, String interceptionId,
                       TransactionCallback callback) throws HeuristicRollbackException, HeuristicMixedException, SystemException,
            InvalidTransactionException {
        Transaction current = getActiveTransaction();
        if (callback == null) {
            callback = new TransactionCallback() {
                @Override
                public void transactionCommitted(Transaction transaction) {
                    // Do nothing
                }

                @Override
                public void transactionRolledBack(Transaction transaction) {
                    // Do nothing
                }
            };
        }

        switch (propagation) {
            case REQUIRES:
                // Are we the owner of the transaction?
                if (owned.contains(current)) { // Owner.
                    try {
                        current.commit(); // Commit the transaction
                        owned.remove(current);
                        callback.transactionCommitted(current);
                    } catch (RollbackException e) {
                        owned.remove(current);
                        e.printStackTrace();
                        callback.transactionRolledBack(current);
                    }
                } // Else wait for commit.
                break;
            case MANDATORY:
                // We are never the owner, so just exits the transaction.
                break;
            case SUPPORTED:
                // Do nothing.
                break;
            case NOT_SUPPORTED:
                // We may have suspended a transaction if one, resume it
                // If we have another transaction and we have suspended a transaction,
                // throw an IllegalStateException because it's impossible to resume
                // the suspended transaction. If we didn't suspend a transaction, accept the new transaction (user
                // responsibility)
                List<Transaction> susp = suspended.get(Thread.currentThread());
                if (current != null && !susp.isEmpty()) {
                    throw new IllegalStateException("Error while handling " + interceptionId + " : you cannot start a" +
                            " transaction after having suspended one. We would not be able to resume the suspended " +
                            "transaction");
                } else if (current == null && !susp.isEmpty()) {
                    manager.resume(susp.remove(susp.size() - 1));
                }
                break;
            case NEVER:
                // Do nothing.
                break;
            case REQUIRES_NEW:
                // We're necessary the owner.
                try {
                    current.commit(); // Commit the transaction
                    owned.remove(current);
                    callback.transactionCommitted(current);
                    List<Transaction> suspendedTransactions = suspended.get(Thread.currentThread());
                    if (suspendedTransactions != null && !suspendedTransactions.isEmpty()) {
                        // suspend the completed transaction.
                        Transaction trans = suspendedTransactions.get(suspendedTransactions.size() - 1);
                        manager.suspend();
                        suspendedTransactions.remove(trans);
                        manager.resume(trans);
                    }
                } catch (RollbackException e) { // The transaction was rolledback rather than committed
                    owned.remove(current);
                    callback.transactionRolledBack(current);

                    List<Transaction> suspendedTransactions = suspended.get(Thread.currentThread());
                    if (suspendedTransactions != null && !suspendedTransactions.isEmpty()) {
                        // suspend the transaction.
                        Transaction trans = suspendedTransactions.get(suspendedTransactions.size() - 1);
                        manager.suspend();
                        suspendedTransactions.remove(trans);
                        manager.resume(trans);
                    }
                }
                break;
            default:
                throw new UnsupportedOperationException("Unknown or unsupported propagation policy for " + interceptionId + " :" +
                        propagation);

        }
    }

    /**
     * Default callback.
     */
    @Override
    public void beforeCompletion() {

    }

    /**
     * Default callback
     *
     * @param status transaction status
     */
    @Override
    public void afterCompletion(int status) {

    }

    /**
     * A transactional bloc has thrown an exception. This method decides what needs to be done in that case.
     *
     * @param e              the exception
     * @param propagation    the propagation strategy
     * @param noRollbackFor  the set of exceptions that does not make the current transaction to rollback
     * @param rollbackFor    the set of exceptions that makes the current transaction to rollback
     * @param interceptionId an identifier for the interception, used for logging.
     * @param callback       the transaction callback
     * @throws SystemException             thrown by the transaction manager to indicate that it has encountered an
     *                                     unexpected error condition that prevents future transaction services from
     *                                     proceeding.
     * @throws HeuristicRollbackException  thrown by the commit operation to report that a heuristic decision was made
     *                                     and that all relevant updates have been rolled back.
     * @throws HeuristicMixedException     thrown to report that a heuristic decision was made and that some relevant
     *                                     updates have been committed and others have been rolled back.
     * @throws InvalidTransactionException the request carried an invalid transaction context.
     */
    public void onError(Exception e, Propagation propagation, Class<? extends Exception>[] noRollbackFor,
                        Class<? extends Exception>[] rollbackFor, String interceptionId, TransactionCallback callback) throws SystemException, HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        Transaction current = getActiveTransaction();
        if (current != null) {
            // We have a transaction.
            // Check whether or not the transaction needs to be marked as rollback only.
            if (!Arrays.asList(noRollbackFor).contains(e.getClass())) {
                if (Arrays.asList(rollbackFor).contains(e.getClass()) || rollbackFor.length == 0) {
                    current.setRollbackOnly();
                }
            }
            onExit(propagation, interceptionId, callback);
        }
    }
}
