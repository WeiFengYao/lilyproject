/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilycms.rowlog.impl.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.data.Stat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lilycms.rowlog.api.RowLog;
import org.lilycms.rowlog.api.RowLogMessage;
import org.lilycms.rowlog.api.RowLogMessageConsumer;
import org.lilycms.rowlog.api.RowLogProcessor;
import org.lilycms.rowlog.api.RowLogShard;
import org.lilycms.rowlog.impl.RowLogImpl;
import org.lilycms.rowlog.impl.RowLogProcessorImpl;
import org.lilycms.rowlog.impl.RowLogShardImpl;
import org.lilycms.testfw.HBaseProxy;
import org.lilycms.testfw.TestHelper;

public class RowLogEndToEndTest {
    private final static HBaseProxy HBASE_PROXY = new HBaseProxy();
    private static RowLog rowLog;
    private static TestMessageConsumer consumer;
    private static RowLogShard shard;
    private static RowLogProcessor processor;
    private static HTableInterface rowTable;
    private static Configuration configuration;
    private static String zkConnectString;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TestHelper.setupLogging();
        HBASE_PROXY.start();
        configuration = HBASE_PROXY.getConf();
        rowTable = RowLogTableUtil.getRowTable(configuration);
        zkConnectString = HBASE_PROXY.getZkConnectString();
        rowLog = new RowLogImpl("EndToEndRowLog", rowTable, RowLogTableUtil.PAYLOAD_COLUMN_FAMILY,
                RowLogTableUtil.EXECUTIONSTATE_COLUMN_FAMILY, 60000L, zkConnectString);
        shard = new RowLogShardImpl("EndToEndShard", configuration, rowLog, 100);
        processor = new RowLogProcessorImpl(rowLog, shard, zkConnectString);
        rowLog.registerShard(shard);

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        HBASE_PROXY.stop();
    }

    @Before
    public void setUp() throws Exception {
        consumer = new TestMessageConsumer(0);
        rowLog.registerConsumer(consumer);
    }

    @After
    public void tearDown() throws Exception {
        assertHostAndPortRemovedFromZK();
        consumer.validate();
        rowLog.unRegisterConsumer(consumer);
    }

    @Test
    public void testSingleMessage() throws Exception {
        RowLogMessage message = rowLog.putMessage(Bytes.toBytes("row1"), null, null, null);
        consumer.expectMessage(message);
        consumer.expectMessages(1);
        processor.start();
        consumer.waitUntilMessagesConsumed(120000);
        processor.stop();
    }

    @Test
    public void testProcessorPublishesHost() throws Exception {
        processor.start();
        final Semaphore semaphore = new Semaphore(0);
        ZooKeeper zooKeeper = null;
        try {
            zooKeeper = new ZooKeeper(HBASE_PROXY.getZkConnectString(), 50000, new Watcher() {

                public void process(WatchedEvent event) {
                    if (KeeperState.SyncConnected.equals(event.getState())) {
                        semaphore.release();
                    }
                }
            });
            semaphore.acquire();
            byte[] data = zooKeeper.getData("/lily/rowLog/EndToEndRowLog/EndToEndShard", false, new Stat());
            assertNotNull(data);
        } finally {
            if (zooKeeper != null) {
                zooKeeper.close();
            }
        }
        processor.stop();
    }

    @Test
    public void testSingleMessageProcessorStartsFirst() throws Exception {
        processor.start();
        RowLogMessage message = rowLog.putMessage(Bytes.toBytes("row2"), null, null, null);
        consumer.expectMessage(message);
        consumer.expectMessages(1);
        consumer.waitUntilMessagesConsumed(120000);
        processor.stop();
    }

    @Test
    public void testMultipleMessagesSameRow() throws Exception {
        RowLogMessage message;
        consumer.expectMessages(10);
        for (int i = 0; i < 10; i++) {
            byte[] rowKey = Bytes.toBytes("row3");
            message = rowLog.putMessage(rowKey, null, "aPayload".getBytes(), null);
            consumer.expectMessage(message);
        }
        processor.start();
        consumer.waitUntilMessagesConsumed(120000);
        processor.stop();
    }

    @Test
    public void testMultipleMessagesMultipleRows() throws Exception {
        RowLogMessage message;
        consumer.expectMessages(25);
        for (long seqnr = 0L; seqnr < 5; seqnr++) {
            for (int rownr = 10; rownr < 15; rownr++) {
                byte[] data = Bytes.toBytes(rownr);
                data = Bytes.add(data, Bytes.toBytes(seqnr));
                message = rowLog.putMessage(Bytes.toBytes("row" + rownr), data, null, null);
                consumer.expectMessage(message);
            }
        }
        processor.start();
        consumer.waitUntilMessagesConsumed(120000);
        processor.stop();
    }

    @Test
    public void testMultipleConsumers() throws Exception {
        TestMessageConsumer consumer2 = new TestMessageConsumer(1);
        rowLog.registerConsumer(consumer2);
        consumer.expectMessages(10);
        consumer2.expectMessages(10);
        RowLogMessage message;
        for (long seqnr = 0L; seqnr < 2; seqnr++) {
            for (int rownr = 20; rownr < 25; rownr++) {
                byte[] data = Bytes.toBytes(rownr);
                data = Bytes.add(data, Bytes.toBytes(seqnr));
                message = rowLog.putMessage(Bytes.toBytes("row" + rownr), data, null, null);
                consumer.expectMessage(message);
                consumer2.expectMessage(message);
            }
        }
        processor.start();
        consumer.waitUntilMessagesConsumed(120000);
        consumer2.waitUntilMessagesConsumed(120000);
        processor.stop();
        consumer2.validate();
        rowLog.unRegisterConsumer(consumer2);
    }

    @Test
    public void testProblematicMessage() throws Exception {
        RowLogMessage message = rowLog.putMessage(Bytes.toBytes("row1"), null, null, null);
        consumer.problematicMessages.add(message);
        consumer.expectMessage(message, 3);
        consumer.expectMessages(3);
        processor.start();
        consumer.waitUntilMessagesConsumed(120000);
        List<RowLogMessage> problematic = rowLog.getProblematic(0);
        Assert.assertTrue(problematic.contains(message));
        processor.stop();
    }

    private static class TestMessageConsumer implements RowLogMessageConsumer {

        private Map<RowLogMessage, Integer> expectedMessages = new HashMap<RowLogMessage, Integer>();
        private Map<RowLogMessage, Integer> earlyMessages = new HashMap<RowLogMessage, Integer>();
        public List<RowLogMessage> problematicMessages = new ArrayList<RowLogMessage>();
        private int count = 0;
        private int numberOfMessagesToBeExpected = 0;
        private final int id;
        public int maxTries = 3;

        public TestMessageConsumer(int id) {
            this.id = id;
        }

        public int getMaxTries() {
            return maxTries;
        }

        public void expectMessage(RowLogMessage message) throws Exception {
            expectMessage(message, 1);
        }

        public void expectMessage(RowLogMessage message, int times) throws Exception {
            if (earlyMessages.containsKey(message)) {
                int timesEarlyReceived = earlyMessages.get(message);
                count = count + timesEarlyReceived;
                int remainingTimes = times - timesEarlyReceived;
                if (remainingTimes < 0)
                    throw new Exception("Recieved message <" + message + "> more than expected");
                earlyMessages.remove(message);
                if (remainingTimes > 0) {
                    expectedMessages.put(message, remainingTimes);
                }
            } else {
                expectedMessages.put(message, times);
            }
        }

        public void expectMessages(int i) {
            this.numberOfMessagesToBeExpected = i;
        }

        public int getId() {
            return id;
        }

        public boolean processMessage(RowLogMessage message) {
            if (!expectedMessages.containsKey(message)) {
                if (earlyMessages.containsKey(message)) {
                    earlyMessages.put(message, earlyMessages.get(message) + 1);
                } else {
                    earlyMessages.put(message, 1);
                }
                return (!problematicMessages.contains(message));
            } else {
                count++;
                int timesRemaining = expectedMessages.get(message);
                if (timesRemaining == 1) {
                    expectedMessages.remove(message);
                    return (!problematicMessages.contains(message));
                } else {
                    expectedMessages.put(message, timesRemaining - 1);
                    return false;
                }
            }
        }

        public void waitUntilMessagesConsumed(long timeout) throws Exception {
            long waitUntil = System.currentTimeMillis() + timeout;
            while ((!expectedMessages.isEmpty() || (count < numberOfMessagesToBeExpected))
                    && System.currentTimeMillis() < waitUntil) {
                Thread.sleep(100);
            }
        }

        public void validate() throws Exception {
            if (count > numberOfMessagesToBeExpected)
                throw new Exception("Received more messages than expected");
            if (!earlyMessages.isEmpty())
                throw new Exception("Received more messages than expected");
            if (!expectedMessages.isEmpty())
                throw new Exception("Messages not processed within timeout");
        }

    }

    private void assertHostAndPortRemovedFromZK() throws Exception {
        final Semaphore semaphore = new Semaphore(0);
        ZooKeeper zooKeeper = null;
        try {
            zooKeeper = new ZooKeeper(HBASE_PROXY.getZkConnectString(), 50000, new Watcher() {

                public void process(WatchedEvent event) {
                    if (KeeperState.SyncConnected.equals(event.getState())) {
                        semaphore.release();
                    }
                }
            });
            semaphore.acquire();
            try {
                zooKeeper.getData("/lily/rowLog/EndToEndRowLog/EndToEndShard", false, new Stat());
                fail("The host in Zookeeper should have been deleted");
            } catch (KeeperException expected) {
            }
        } finally {
            if (zooKeeper != null) {
                zooKeeper.close();
            }
        }
    }
}
