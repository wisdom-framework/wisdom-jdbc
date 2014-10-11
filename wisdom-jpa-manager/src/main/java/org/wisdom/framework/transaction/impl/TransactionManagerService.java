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

import com.google.common.base.Strings;
import org.apache.felix.ipojo.annotations.*;
import org.apache.geronimo.transaction.log.UnrecoverableLog;
import org.apache.geronimo.transaction.manager.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.wisdom.api.configuration.ApplicationConfiguration;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import java.io.File;

@SuppressWarnings("UnusedDeclaration")
@Component(immediate = true)
@Instantiate
public class TransactionManagerService {

    public static final String TRANSACTION_TIMEOUT = "wisdom.transaction.timeout";
    public static final String RECOVERABLE = "wisdom.transaction.recoverable";
    public static final String TMID = "wisdom.transaction.tmid";
    public static final String HOWL_BUFFER_CLASS_NAME = "wisdom.transaction.howl.bufferClassName";
    public static final String HOWL_BUFFER_SIZE = "wisdom.transaction.howl.bufferSize";
    public static final String HOWL_CHECKSUM_ENABLED = "wisdom.transaction.howl.checksumEnabled";
    public static final String HOWL_ADLER32_CHECKSUM = "wisdom.transaction.howl.adler32Checksum";
    public static final String HOWL_FLUSH_SLEEP_TIME = "wisdom.transaction.howl.flushSleepTime";
    public static final String HOWL_LOG_FILE_EXT = "wisdom.transaction.howl.logFileExt";
    public static final String HOWL_LOG_FILE_NAME = "wisdom.transaction.howl.logFileName";
    public static final String HOWL_MAX_BLOCKS_PER_FILE = "wisdom.transaction.howl.maxBlocksPerFile";
    public static final String HOWL_MAX_LOG_FILES = "wisdom.transaction.howl.maxLogFiles";
    public static final String HOWL_MAX_BUFFERS = "wisdom.transaction.howl.maxBuffers";
    public static final String HOWL_MIN_BUFFERS = "wisdom.transaction.howl.minBuffers";
    public static final String HOWL_THREADS_WAITING_FORCE_THRESHOLD = "wisdom.transaction.howl.threadsWaitingForceThreshold";
    public static final String HOWL_LOG_FILE_DIR = "wisdom.transaction.howl.logFileDir";
    public static final String HOWL_FLUSH_PARTIAL_BUFFERS = "wisdom.transaction.flushPartialBuffers";

    public static final int DEFAULT_TRANSACTION_TIMEOUT = 600; // 600 seconds -> 10 minutes
    public static final boolean DEFAULT_RECOVERABLE = false;   // not recoverable by default

    public static TransactionManager transactionManager;

    private final TransactionLog transactionLog;

    @Requires
    ApplicationConfiguration configuration;

    private final BundleContext bundleContext;
    private ServiceRegistration<?> registration;

    public static TransactionManager get() {
        return transactionManager;
    }

    public TransactionManagerService(@Context BundleContext bundleContext, @Requires ApplicationConfiguration configuration) {
        this.bundleContext = bundleContext;
        // Transaction timeout
        int transactionTimeout = configuration.getIntegerWithDefault(TRANSACTION_TIMEOUT, DEFAULT_TRANSACTION_TIMEOUT);
        if (transactionTimeout <= 0) {
            throw new IllegalArgumentException("The transaction timeout cannot be negative or zero");
        }

        final String tmid = configuration.getWithDefault(TMID, "wisdom-transaction-manager");
        // the max length of the factory should be 64
        XidFactory xidFactory = new XidFactoryImpl(tmid.substring(0, Math.min(tmid.length(), 64)).getBytes());
        // Transaction log
        if (configuration.getBooleanWithDefault(RECOVERABLE, DEFAULT_RECOVERABLE)) {
            String bufferClassName = configuration.getWithDefault(HOWL_BUFFER_CLASS_NAME, "org.objectweb.howl.log.BlockLogBuffer");
            int bufferSizeKBytes = configuration.getIntegerWithDefault(HOWL_BUFFER_SIZE, 4);
            if (bufferSizeKBytes < 1 || bufferSizeKBytes > 32) {
                throw new IllegalArgumentException("The buffer size must be between 1 and 32");
            }
            boolean checksumEnabled = configuration.getBooleanWithDefault(HOWL_CHECKSUM_ENABLED, true);
            boolean adler32Checksum = configuration.getBooleanWithDefault(HOWL_ADLER32_CHECKSUM, true);
            int flushSleepTimeMilliseconds = configuration.getIntegerWithDefault(HOWL_FLUSH_SLEEP_TIME, 50);
            String logFileExt = configuration.getWithDefault(HOWL_LOG_FILE_EXT, "log");
            String logFileName = configuration.getWithDefault(HOWL_LOG_FILE_NAME, "transaction");
            int maxBlocksPerFile = configuration.getIntegerWithDefault(HOWL_MAX_BLOCKS_PER_FILE, -1);
            int maxLogFiles = configuration.getIntegerWithDefault(HOWL_MAX_LOG_FILES, 2);
            int minBuffers = configuration.getIntegerWithDefault(HOWL_MIN_BUFFERS, 4);
            if (minBuffers < 0) {
                throw new IllegalArgumentException("The min buffer must be strictly greater than 0");
            }
            int maxBuffers = configuration.getIntegerWithDefault(HOWL_MAX_BUFFERS, 0);
            if (maxBuffers > 0 && minBuffers < maxBuffers) {
                throw new IllegalArgumentException("The max buffer must be strictly greater than the min buffer (" +
                        minBuffers + ")");
            }
            int threadsWaitingForceThreshold = configuration.getIntegerWithDefault(HOWL_THREADS_WAITING_FORCE_THRESHOLD,
                    -1);
            boolean flushPartialBuffers = configuration.getBooleanWithDefault(HOWL_FLUSH_PARTIAL_BUFFERS, true);
            final File dir = new File(configuration.getBaseDir(), configuration.getWithDefault(HOWL_LOG_FILE_DIR, ".howl"));
            dir.mkdirs();
            String logFileDir = dir.getAbsolutePath();
            try {
                transactionLog = new HowlLog(bufferClassName,
                        bufferSizeKBytes,
                        checksumEnabled,
                        adler32Checksum,
                        flushSleepTimeMilliseconds,
                        logFileDir,
                        logFileExt,
                        logFileName,
                        maxBlocksPerFile,
                        maxBuffers,
                        maxLogFiles,
                        minBuffers,
                        threadsWaitingForceThreshold,
                        flushPartialBuffers,
                        xidFactory,
                        null);
                ((HowlLog) transactionLog).doStart();
            } catch (Exception e) {
                // This should not really happen as we've checked properties earlier
                throw new IllegalArgumentException("Cannot instantiate the transaction log", e);
            }
        } else {
            transactionLog = new UnrecoverableLog();
        }
        // Create transaction manager
        try {
            // Because of OpenJPA, we store it in a static field (we need a way to retrieve it from a static method).
            transactionManager = new TransactionManagerImpl(transactionTimeout, xidFactory, transactionLog); //NOSONAR
        } catch (XAException e) {
            throw new IllegalStateException("Cannot instantiate the transaction manager", e);
        }
    }

    @Validate
    public void register() {
        registration = bundleContext.registerService(new String[]{
                UserTransaction.class.getName(),
                TransactionManager.class.getName(),
                TransactionSynchronizationRegistry.class.getName(),
                XidImporter.class.getName(),
                MonitorableTransactionManager.class.getName(),
                RecoverableTransactionManager.class.getName()
        }, transactionManager, null);
    }

    @Invalidate
    public void unregister() throws Exception {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }

        if (transactionLog instanceof HowlLog) {
            ((HowlLog) transactionLog).doStop();
        }
    }

}
