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

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.wisdom.api.http.Result;
import org.wisdom.api.interception.Interceptor;
import org.wisdom.api.interception.RequestContext;
import org.wisdom.framework.transaction.Transactional;

import javax.transaction.TransactionManager;

/**
 * The transaction interceptor that manages the transactional execution of an action.
 */
@Component
@Provides(specifications = {Interceptor.class})
@Instantiate
public class TransactionInterceptor extends Interceptor<Transactional> {

    @Requires
    TransactionManager manager;

    PropagationManager propagation;

    public TransactionInterceptor() {
        propagation = new PropagationManager(manager);
    }

    /**
     * The interception method. The method should call {@link org.wisdom.api.interception.RequestContext#proceed()}
     * to call the next interception. Without this call it cuts the chain.
     *
     * @param configuration the interception configuration
     * @param context       the interception context
     * @return the result
     * @throws Exception if anything bad happen
     */
    @Override
    public Result call(Transactional configuration, RequestContext context) throws Exception {
        propagation.onEntry(configuration.propagation(),
                configuration.timeout(),
                context.route().getControllerMethod().getName());
        try {
            Result result = context.proceed();
            propagation.onExit(configuration.propagation(), context.route().getControllerMethod().getName(), null);
            return result;
        } catch (Exception e) {
            propagation.onError(e, configuration.propagation(), configuration.noRollbackFor(),
                    configuration.rollbackOnlyFor(), context.route().getControllerMethod().getName(), null);
            throw e;
        }
    }

    /**
     * Gets the annotation class configuring the current interceptor.
     *
     * @return the annotation
     */
    @Override
    public Class<Transactional> annotation() {
        return Transactional.class;
    }
}
