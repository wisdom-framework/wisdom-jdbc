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

import org.apache.geronimo.transaction.log.HOWLLog;
import org.apache.geronimo.transaction.manager.LogException;
import org.apache.geronimo.transaction.manager.*;
import org.objectweb.howl.log.*;
import org.objectweb.howl.log.xa.XACommittingTx;
import org.objectweb.howl.log.xa.XALogRecord;
import org.objectweb.howl.log.xa.XALogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.xa.Xid;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of the Geronimo Transaction Log based on OW2 Howl.
 * Configuration is documented in the <a href="http://howl.ow2.org/jdoc/public/index.html">Howl API</a>.
 */
public class HowlLog implements TransactionLog {
    private static final byte COMMIT = 2;
    private static final byte ROLLBACK = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(HOWLLog.class);

    private File serverBaseDir;
    private String logFileDir;

    private final XidFactory xidFactory;

    private final XALogger logger;
    private final Configuration configuration = new Configuration();
    private boolean started = false;
    private Map<Xid, Recovery.XidBranchesPair> recovered;

    /**
     * Creates the HowLog instance
     *
     * @param bufferClassName              the buffer class name
     * @param bufferSize                   the buffer size
     * @param checksumEnabled              whether or not checksum is enabled
     * @param adler32Checksum              whether or not the Adler 32 checksum algorithm should be used
     * @param flushSleepTimeMilliseconds   the time in ms the flush manager should sleep
     * @param logFileDir                   the directory of the log file
     * @param logFileExt                   the log file extension
     * @param logFileName                  the log file name
     * @param maxBlocksPerFile             the maximum number of block in a file
     * @param maxBuffers                   the max buffer
     * @param maxLogFiles                  the max number of file
     * @param minBuffers                   the min buffer
     * @param threadsWaitingForceThreshold the thread waiting threshold
     * @param flushPartialBuffers          whether or not partial buffer are flushed
     * @param xidFactory                   the XidFactory
     * @param serverBaseDir                the server directory
     * @throws IOException               when the logger cannot be created
     * @throws LogConfigurationException when the configuration is invalid
     */
    public HowlLog(String bufferClassName,
                   int bufferSize,
                   boolean checksumEnabled,
                   boolean adler32Checksum,
                   int flushSleepTimeMilliseconds,
                   String logFileDir,
                   String logFileExt,
                   String logFileName,
                   int maxBlocksPerFile,
                   int maxBuffers,
                   int maxLogFiles,
                   int minBuffers,
                   int threadsWaitingForceThreshold,
                   boolean flushPartialBuffers,
                   XidFactory xidFactory,
                   File serverBaseDir) throws IOException, LogConfigurationException {
        this.serverBaseDir = serverBaseDir;
        configuration.setBufferClassName(bufferClassName);
        configuration.setBufferSize(bufferSize);
        configuration.setChecksumEnabled(checksumEnabled);
        configuration.setAdler32Checksum(adler32Checksum);
        configuration.setFlushSleepTime(flushSleepTimeMilliseconds);
        this.logFileDir = logFileDir;
        configuration.setLogFileExt(logFileExt);
        configuration.setLogFileName(logFileName);
        configuration.setMaxBlocksPerFile(maxBlocksPerFile == -1 ? Integer.MAX_VALUE : maxBlocksPerFile);
        configuration.setMaxBuffers(maxBuffers);
        configuration.setMaxLogFiles(maxLogFiles);
        configuration.setMinBuffers(minBuffers);
        configuration.setThreadsWaitingForceThreshold(
                threadsWaitingForceThreshold == -1 ? Integer.MAX_VALUE : threadsWaitingForceThreshold);
        configuration.setFlushPartialBuffers(flushPartialBuffers);
        this.xidFactory = xidFactory;
        this.logger = new XALogger(configuration);
    }

    /**
     * Sets the log dir.
     * @param logDirName the directory name
     */
    public void setLogFileDir(String logDirName) {
        File logDir = new File(logDirName);
        if (!logDir.isAbsolute()) {
            logDir = new File(serverBaseDir, logDirName);
        }

        this.logFileDir = logDirName;
        if (started) {
            configuration.setLogFileDir(logDir.getAbsolutePath());
        }
    }

    /**
     * Starts the logger.
     * @throws Exception cannot be started
     */
    public void start() throws Exception {
        started = true;
        setLogFileDir(logFileDir);
        LOGGER.debug("Initiating transaction manager recovery");
        recovered = new HashMap<>();

        logger.open(null);

        ReplayListener replayListener = new GeronimoReplayListener(xidFactory, recovered);
        logger.replayActiveTx(replayListener);

        LOGGER.debug("In doubt transactions recovered from log");
    }

    /**
     * Stops the logger.
     * @throws Exception cannot be stopped
     */
    public void stop() throws Exception {
        started = false;
        logger.close();
        recovered = null;
    }

    /**
     * Begins a transaction.
     * @param xid the id
     */
    public void begin(Xid xid) {
        // Do nothing.
    }

    /**
     * Prepares a transaction
     * @param xid the id
     * @param branches  the branches
     * @return the log mark to use in commit/rollback calls.
     * @throws LogException on error
     */
    public Object prepare(Xid xid, List<? extends TransactionBranchInfo> branches) throws LogException {
        int branchCount = branches.size();
        byte[][] data = new byte[3 + 2 * branchCount][];
        data[0] = intToBytes(xid.getFormatId());
        data[1] = xid.getGlobalTransactionId();
        data[2] = xid.getBranchQualifier();
        int i = 3;
        for (TransactionBranchInfo transactionBranchInfo : branches) {
            data[i++] = transactionBranchInfo.getBranchXid().getBranchQualifier();
            data[i++] = transactionBranchInfo.getResourceName().getBytes();
        }
        try {
            return logger.putCommit(data);
        } catch (LogClosedException | LogRecordSizeException | InterruptedException | LogFileOverflowException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new LogException(e);
        }
    }

    /**
     * Commits a transaction
     * @param xid the id
     * @param logMark the mark
     * @throws LogException on error
     */
    public void commit(Xid xid, Object logMark) throws LogException {
        //the data is theoretically unnecessary but is included to help with debugging
        // and because HOWL currently requires it.
        byte[][] data = new byte[4][];
        data[0] = new byte[]{COMMIT};
        data[1] = intToBytes(xid.getFormatId());
        data[2] = xid.getGlobalTransactionId();
        data[3] = xid.getBranchQualifier();
        try {
            logger.putDone(data, (XACommittingTx) logMark);
        } catch (LogClosedException | LogRecordSizeException | InterruptedException | IOException | LogFileOverflowException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Rollback a transaction.
     * @param xid the id
     * @param logMark the mark
     * @throws LogException on error
     */
    public void rollback(Xid xid, Object logMark) throws LogException {
        //the data is theoretically unnecessary but is included to help
        // with debugging and because HOWL currently requires it.
        byte[][] data = new byte[4][];
        data[0] = new byte[]{ROLLBACK};
        data[1] = intToBytes(xid.getFormatId());
        data[2] = xid.getGlobalTransactionId();
        data[3] = xid.getBranchQualifier();
        try {
            logger.putDone(data, (XACommittingTx) logMark);
        } catch (LogClosedException | LogRecordSizeException | LogFileOverflowException | IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Recovers the log, returning a map of (top level) xid to List of TransactionBranchInfo for the branches.
     * Uses the XidFactory to reconstruct the xids.
     *
     * @param xidFactory Xid factory
     * @return Map of recovered xid to List of TransactionBranchInfo representing the branches.
     * @throws LogException on error
     */
    public Collection<Recovery.XidBranchesPair> recover(XidFactory xidFactory) throws LogException {
        LOGGER.debug("Initiating transaction manager recovery");
        Map<Xid, Recovery.XidBranchesPair> recovered = new HashMap<>();
        ReplayListener replayListener = new GeronimoReplayListener(xidFactory, recovered);
        logger.replayActiveTx(replayListener);
        LOGGER.debug("In doubt transactions recovered from log");
        return recovered.values();
    }

    /**
     * Retrieves statistics
     * @return the statistics as XML.
     */
    public String getXMLStats() {
        return logger.getStats();
    }

    public int getAverageForceTime() {
        return 0;
    }

    public int getAverageBytesPerForce() {
        return 0;
    }

    private byte[] intToBytes(int formatId) {
        byte[] buffer = new byte[4];
        buffer[0] = (byte) (formatId >> 24);
        buffer[1] = (byte) (formatId >> 16);
        buffer[2] = (byte) (formatId >> 8);
        buffer[3] = (byte) (formatId);
        return buffer;
    }

    private int bytesToInt(byte[] buffer) {
        return ((int) buffer[0]) << 24 + ((int) buffer[1]) << 16 + ((int) buffer[2]) << 8 + ((int) buffer[3]);
    }

    private class GeronimoReplayListener implements ReplayListener {

        private final XidFactory xidFactory;
        private final Map<Xid, Recovery.XidBranchesPair> recoveredTx;

        public GeronimoReplayListener(XidFactory xidFactory, Map<Xid, Recovery.XidBranchesPair> recoveredTx) {
            this.xidFactory = xidFactory;
            this.recoveredTx = recoveredTx;
        }

        public void onRecord(LogRecord plainlr) {
            XALogRecord lr = null;
            if (plainlr instanceof XALogRecord) {
                lr = (XALogRecord) plainlr;
            } else {
                throw new IllegalStateException("The record is not a " + XALogRecord.class.getName());
            }
            short recordType = lr.type;
            XACommittingTx tx = lr.getTx();
            if (recordType == LogRecordType.XACOMMIT) {

                byte[][] data = tx.getRecord();

                assert data[0].length == 4;
                int formatId = bytesToInt(data[1]);
                byte[] globalId = data[1];
                byte[] branchId = data[2];
                Xid masterXid = xidFactory.recover(formatId, globalId, branchId);

                Recovery.XidBranchesPair xidBranchesPair = new Recovery.XidBranchesPair(masterXid, tx);
                recoveredTx.put(masterXid, xidBranchesPair);
                LOGGER.debug("recovered prepare record for master xid: " + masterXid);
                for (int i = 3; i < data.length; i += 2) {
                    byte[] branchBranchId = data[i];
                    String name = new String(data[i + 1]);

                    Xid branchXid = xidFactory.recover(formatId, globalId, branchBranchId);
                    TransactionBranchInfoImpl branchInfo = new TransactionBranchInfoImpl(branchXid, name);
                    xidBranchesPair.addBranch(branchInfo);
                    LOGGER.debug("recovered branch for resource manager, branchId " + name + ", " + branchXid);
                }
            } else {
                if (recordType != LogRecordType.END_OF_LOG) { // This value crops up every time the server is started
                    LOGGER.warn("Received unexpected log record: " + lr + " (" + recordType + ")");
                }
            }
        }

        public void onError(org.objectweb.howl.log.LogException exception) {
            LOGGER.error("Error during recovery: ", exception);
        }

        public LogRecord getLogRecord() {
            return new LogRecord(10 * 2 * Xid.MAXBQUALSIZE);
        }

    }
}
