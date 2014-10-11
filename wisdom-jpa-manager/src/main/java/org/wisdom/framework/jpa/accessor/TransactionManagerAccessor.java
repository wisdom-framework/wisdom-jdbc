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
package org.wisdom.framework.jpa.accessor;

import org.wisdom.framework.transaction.impl.TransactionManagerService;

import javax.transaction.TransactionManager;

/**
 * A class used to retrieve the Transaction Manager.
 * This class is used by OpenJPA to retrieve the transaction manager.
 */
public class TransactionManagerAccessor {

    /**
     * @return the transaction manager.
     */
    public static TransactionManager get() {
        return TransactionManagerService.get();
    }
}
