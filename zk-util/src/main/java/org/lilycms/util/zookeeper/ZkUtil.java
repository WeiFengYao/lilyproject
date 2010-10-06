package org.lilycms.util.zookeeper;

import java.io.IOException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper.States;
import org.apache.zookeeper.data.Stat;

/**
 * Various ZooKeeper utility methods.
 */
public class ZkUtil {
    
    public static ZooKeeperItf connect(String connectString, int sessionTimeout) throws ZkConnectException {
        ZooKeeperImpl zooKeeper;
        try {
            zooKeeper = new ZooKeeperImpl(connectString, sessionTimeout);
        } catch (IOException e) {
            throw new ZkConnectException("Failed to connect with Zookeeper @ <" + connectString + ">", e);
        }
        long waitUntil = System.currentTimeMillis() + 10000;
        boolean connected = (States.CONNECTED).equals(zooKeeper.getState());
        while (!connected && waitUntil > System.currentTimeMillis()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                connected = (States.CONNECTED).equals(zooKeeper.getState());
                break;
            }
            connected = (States.CONNECTED).equals(zooKeeper.getState());
        }
        if (!connected) {
            System.out.println("Failed to connect to Zookeeper within timeout: Dumping stack: ");
            Thread.dumpStack();
            zooKeeper.close();
            throw new ZkConnectException("Failed to connect with Zookeeper @ <" + connectString +
                    "> within timeout <" + sessionTimeout + ">");
        }
        return zooKeeper;
    }

    public static void createPath(final ZooKeeperItf zk, final String path)
            throws InterruptedException, KeeperException {
        createPath(zk, path, null, CreateMode.PERSISTENT);
    }

    /**
     * Creates a persistent path on zookeeper if it does not exist yet, including any parents.
     * Keeps retrying in case of connection loss.
     *
     */
    public static void createPath(final ZooKeeperItf zk, final String path, final byte[] data,
            final CreateMode createMode) throws InterruptedException, KeeperException {

        Stat stat = zk.retryOperation(new ZooKeeperOperation<Stat>() {
            public Stat execute() throws KeeperException, InterruptedException {
                return zk.exists(path, null);
            }
        });

        if (stat != null)
            return;

        if (!path.startsWith("/"))
            throw new IllegalArgumentException("Path should start with a slash.");

        String[] parts = path.substring(1).split("/");

        final StringBuilder subPath = new StringBuilder();
        for (String part : parts) {
            subPath.append("/").append(part);
            try {
                zk.retryOperation(new ZooKeeperOperation<String>() {
                    public String execute() throws KeeperException, InterruptedException {
                        return zk.create(subPath.toString(), data, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
                    }
                });
            } catch (KeeperException.NodeExistsException e) {
                // ignore
            }
        }
    }
}
