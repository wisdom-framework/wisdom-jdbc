package org.wisdom.framework.transaction.impl;

import org.apache.geronimo.transaction.manager.XidFactory;
import org.junit.Test;

import javax.transaction.xa.Xid;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class XidFactoryImplTest {

    @Test
    public void testLong() {
        byte[] buffer = new byte[64];
        long l1 = 1343120074022l;
        XidFactoryImpl.insertLong(l1, buffer, 4);
        long l2 = XidFactoryImpl.extractLong(buffer, 4);
        assertThat(l1).isEqualTo(l2);

        l1 = 1343120074022l - TimeUnit.DAYS.toMillis(15);
        XidFactoryImpl.insertLong(l1, buffer, 4);
        l2 = XidFactoryImpl.extractLong(buffer, 4);
        assertThat(l1).isEqualTo(l2);
    }

    @Test
    public void testFactory() throws Exception {
        XidFactory factory = new XidFactoryImpl("hi".getBytes());
        Xid id1 = factory.createXid();
        Xid id2 = factory.createXid();

        assertThat(factory.matchesGlobalId(id1.getGlobalTransactionId())).isFalse();
        assertThat(factory.matchesGlobalId(id2.getGlobalTransactionId())).isFalse();

        Xid b_id1 = factory.createBranch(id1, 1);
        Xid b_id2 = factory.createBranch(id2, 1);

        assertThat(factory.matchesBranchId(b_id1.getBranchQualifier())).isFalse();
        assertThat(factory.matchesBranchId(b_id2.getBranchQualifier())).isFalse();

        Thread.sleep(5);

        XidFactory factory2 = new XidFactoryImpl("hi".getBytes());
        assertThat(factory2.matchesGlobalId(id1.getGlobalTransactionId())).isTrue();
        assertThat(factory2.matchesGlobalId(id2.getGlobalTransactionId())).isTrue();

        assertThat(factory2.matchesBranchId(b_id1.getBranchQualifier())).isTrue();
        assertThat(factory2.matchesBranchId(b_id2.getBranchQualifier())).isTrue();
    }

}