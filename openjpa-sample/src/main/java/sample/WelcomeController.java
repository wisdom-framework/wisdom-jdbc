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
package sample;

import models.School;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.wisdom.api.DefaultController;
import org.wisdom.api.annotations.Controller;
import org.wisdom.api.annotations.Route;
import org.wisdom.api.http.HttpMethod;
import org.wisdom.api.http.Result;
import org.wisdom.framework.transaction.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.transaction.*;
import java.util.List;

/**
 * Your first Wisdom Controller.
 */
@Controller
@Transactional
public class WelcomeController extends DefaultController {



    @Requires
    EntityManagerFactory emf;

    @Requires
    EntityManager entityManager;

    @Requires
    TransactionManager transactionManager;

    @Validate
    public void start() throws Exception {
        transactionManager.begin();
        EntityManager entityManager = emf.createEntityManager();
        School ujf = new School();
        ujf.setName("UJF");
        School upmf = new School();
        upmf.setName("UPMF");

        entityManager.persist(ujf);
        entityManager.persist(upmf);

        transactionManager.commit();
    }


    /**
     * The action method returning the welcome page. It handles
     * HTTP GET request on the "/" URL.
     *
     * @return the welcome page
     */
    @Route(method = HttpMethod.GET, uri = "/")
    public Result welcome() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        return ok(all()).json();
    }

    List<School> all() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
//        EntityManager entityManager = emf.createEntityManager();
//        transactionManager.begin();
//        entityManager.joinTransaction();
        Query query = entityManager.createQuery("SELECT e FROM School e");
        List<School> list = query.getResultList();

//        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
//        CriteriaQuery<School> cq = cb.createQuery(School.class);
//        Root<School> rootEntry = cq.from(School.class);
//        CriteriaQuery<School> all = cq.select(rootEntry);
//        TypedQuery<School> allQuery = entityManager.createQuery(all);
//        final List<School> list = allQuery.getResultList();

//        transactionManager.commit();

        return list;
    }

}
