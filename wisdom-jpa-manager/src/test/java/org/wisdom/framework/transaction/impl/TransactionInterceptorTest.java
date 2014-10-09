package org.wisdom.framework.transaction.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.wisdom.api.DefaultController;
import org.wisdom.api.configuration.ApplicationConfiguration;
import org.wisdom.api.http.HttpMethod;
import org.wisdom.api.http.Result;
import org.wisdom.api.interception.RequestContext;
import org.wisdom.api.router.Route;
import org.wisdom.framework.jpa.accessor.TransactionManagerAccessor;
import org.wisdom.framework.transaction.Propagation;
import org.wisdom.framework.transaction.Transactional;

import javax.transaction.*;
import java.io.File;
import java.util.Dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionInterceptorTest {

    TransactionInterceptor interceptor;
    private TransactionManagerService tms;

    Transaction transaction;

    @Before
    public void setUp() {
        transaction = null;

        ApplicationConfiguration configuration = mock(ApplicationConfiguration.class);
        when(configuration.getBaseDir()).thenReturn(new File("target"));
        when(configuration.getWithDefault(anyString(), anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return (String) invocation.getArguments()[1];
            }
        });
        when(configuration.getBooleanWithDefault(anyString(), anyBoolean())).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                if (invocation.getArguments()[0].equals(TransactionManagerService.RECOVERABLE)) {
                    return true;
                }
                return (Boolean) invocation.getArguments()[1];
            }
        });
        when(configuration.getIntegerWithDefault(anyString(), anyInt())).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                return (Integer) invocation.getArguments()[1];
            }
        });

        BundleContext context = mock(BundleContext.class);
        ServiceRegistration registration = mock(ServiceRegistration.class);
        when(context.registerService(any(Class.class), any(), any(Dictionary.class))).thenReturn(registration);
        tms = new TransactionManagerService(context, configuration);
        tms.configuration = configuration;
        tms.register();

        interceptor = new TransactionInterceptor();
        interceptor.manager = TransactionManagerAccessor.get();
        interceptor.propagation = new PropagationManager(interceptor.manager);
    }

    @After
    public void tearDown() throws Exception {
        tms.unregister();
    }

    @Test
    public void testCallOK() throws Exception {
        Transactional transactional = mock(Transactional.class);
        when(transactional.noRollbackFor()).thenReturn(new Class[0]);
        when(transactional.rollbackOnlyFor()).thenReturn(new Class[0]);
        when(transactional.propagation()).thenReturn(Propagation.REQUIRES);

        RequestContext ctx = mock(RequestContext.class);
        final MyController controller = new MyController();
        when(ctx.proceed()).thenAnswer(new Answer<Result>() {
            @Override
            public Result answer(InvocationOnMock invocation) throws Throwable {
                return controller.index();
            }
        });
        when(ctx.route()).thenReturn(new Route(HttpMethod.GET, "/", controller,
                MyController.class.getMethod("index")));

        interceptor.call(transactional, ctx);
        assertThat(transaction).isNotNull();
        assertThat(controller.status).isEqualTo(Status.STATUS_COMMITTED);
    }

    @Test
    public void testCallKO() throws Exception {
        Transactional transactional = mock(Transactional.class);
        when(transactional.noRollbackFor()).thenReturn(new Class[0]);
        when(transactional.rollbackOnlyFor()).thenReturn(new Class[0]);
        when(transactional.propagation()).thenReturn(Propagation.REQUIRES);

        RequestContext ctx = mock(RequestContext.class);
        final MyController controller = new MyController();
        when(ctx.proceed()).thenAnswer(new Answer<Result>() {
            @Override
            public Result answer(InvocationOnMock invocation) throws Throwable {
                return controller.bad();
            }
        });
        when(ctx.route()).thenReturn(new Route(HttpMethod.GET, "/", controller,
                MyController.class.getMethod("bad")));

        try {
            interceptor.call(transactional, ctx);
            fail("Exception expected");
        } catch (Exception e) {
            // OK, exception expected
        }
        assertThat(transaction).isNotNull();
        assertThat(controller.status).isEqualTo(Status.STATUS_ROLLEDBACK);
    }

    @Test
    public void testAnnotation() throws Exception {
        assertThat(interceptor.annotation()).isEqualTo(Transactional.class);
    }

    private class MyController extends DefaultController implements Synchronization {

        public int status;

        public Result index() throws SystemException, RollbackException {
            assertThat(interceptor.manager.getTransaction()).isNotNull();
            transaction = interceptor.manager.getTransaction();
            transaction.registerSynchronization(this);
            return ok();
        }

        public Result bad() throws SystemException, RollbackException {
            assertThat(interceptor.manager.getTransaction()).isNotNull();
            transaction = interceptor.manager.getTransaction();
            transaction.registerSynchronization(this);
            throw new NullPointerException("Bad");
        }

        @Override
        public void beforeCompletion() {

        }

        @Override
        public void afterCompletion(int status) {
            this.status = status;
        }
    }
}