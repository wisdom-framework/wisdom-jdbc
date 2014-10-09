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

import org.apache.geronimo.transaction.manager.XidFactory;
import org.apache.geronimo.transaction.manager.XidImpl;

import javax.transaction.xa.Xid;

/**
 * Factory for transaction ids that are ever increasing allowing determination of new transactions.
 * The Xid is constructed of two parts:
 * <ol>
 * <li>8 byte id (LSB first)</li>
 * <li>base id</li>
 * <ol>
 * We can't easily extend geronimo XidFactoryImpl because count is private. This class is very close to the Aries
 * implementation from org.apache.aries.transaction.manager-1.0.1.
 */
public class XidFactoryImpl implements XidFactory {
    private final byte[] baseId = new byte[Xid.MAXGTRIDSIZE];
    private final long start = System.currentTimeMillis();
    private long count = start;

    /**
     * Creates an instance of the factory
     * @param tmId the id
     */
    public XidFactoryImpl(byte[] tmId) {
        System.arraycopy(tmId, 0, baseId, 8, tmId.length);
    }

    /**
     * Creates the Xid.
     * @return the new Xid
     */
    public Xid createXid() {
        byte[] globalId = baseId.clone();
        long id;
        synchronized (this) {
            id = count++;
        }
        insertLong(id, globalId, 0);
        return new XidImpl(globalId);
    }

    /**
     * Create a branch Xid.
     * @param globalId the global Xid
     * @param branch the branch number
     * @return the new Xid.
     */
    public Xid createBranch(Xid globalId, int branch) {
        byte[] branchId = baseId.clone();
        branchId[0] = (byte) branch;
        branchId[1] = (byte) (branch >>> 8);
        branchId[2] = (byte) (branch >>> 16);
        branchId[3] = (byte) (branch >>> 24);
        insertLong(start, branchId, 4);
        return new XidImpl(globalId, branchId);
    }

    /**
     * Checks whether or not the given byte array matches the global id.
     * @param globalTransactionId the global transaction id
     * @return true if it matches
     */
    public boolean matchesGlobalId(byte[] globalTransactionId) {
        if (globalTransactionId.length != Xid.MAXGTRIDSIZE) {
            return false;
        }
        for (int i = 8; i < globalTransactionId.length; i++) {
            if (globalTransactionId[i] != baseId[i]) {
                return false;
            }
        }
        // for recovery, only match old transactions
        long id = extractLong(globalTransactionId, 0);
        return (id < start);
    }

    /**
     * Checks whether or not the given byte array matches the branch id.
     * @param branchQualifier the branch qualifier
     * @return true if it matches
     */
    public boolean matchesBranchId(byte[] branchQualifier) {
        if (branchQualifier.length != Xid.MAXBQUALSIZE) {
            return false;
        }
        long id = extractLong(branchQualifier, 4);
        if (id >= start) {
            // newly created branch, not recoverable
            return false;
        }

        for (int i = 12; i < branchQualifier.length; i++) {
            if (branchQualifier[i] != baseId[i]) {
                return false;
            }
        }
        return true;
    }

    public Xid recover(int formatId, byte[] globalTransactionId, byte[] branchQualifier) {
        return new XidImpl(formatId, globalTransactionId, branchQualifier);
    }

    static void insertLong(long value, byte[] bytes, int offset) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
        bytes[offset + 4] = (byte) (value >>> 32);
        bytes[offset + 5] = (byte) (value >>> 40);
        bytes[offset + 6] = (byte) (value >>> 48);
        bytes[offset + 7] = (byte) (value >>> 56);
    }

    static long extractLong(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                + (((bytes[offset + 1] & 0xff)) << 8)
                + (((bytes[offset + 2] & 0xff)) << 16)
                + (((bytes[offset + 3] & 0xffL)) << 24)
                + (((bytes[offset + 4] & 0xffL)) << 32)
                + (((bytes[offset + 5] & 0xffL)) << 40)
                + (((bytes[offset + 6] & 0xffL)) << 48)
                + (((long) bytes[offset + 7]) << 56);
    }

}