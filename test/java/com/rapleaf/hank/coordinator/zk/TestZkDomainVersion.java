package com.rapleaf.hank.coordinator.zk;

import com.rapleaf.hank.ZkTestCase;
import com.rapleaf.hank.coordinator.DomainVersion;
import com.rapleaf.hank.coordinator.DomainVersions;
import com.rapleaf.hank.zookeeper.ZkPath;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;

public class TestZkDomainVersion extends ZkTestCase {
  public void testCreate() throws Exception {
    DomainVersion dv = ZkDomainVersion.create(getZk(), getRoot(), 1, null, null);
    assertEquals(1, dv.getVersionNumber());
    assertNull(dv.getClosedAt());
    assertFalse(DomainVersions.isClosed(dv));
    assertFalse(dv.isDefunct());
  }

  public void testLoad() throws Exception {
    ZkDomainVersion.create(getZk(), getRoot(), 1, null, null);
    dumpZk();
    DomainVersion dv = new ZkDomainVersion(getZk(), ZkPath.append(getRoot(), "versions/version_" + 1), null);
    assertEquals(1, dv.getVersionNumber());
    assertNull(dv.getClosedAt());
    assertFalse(DomainVersions.isClosed(dv));
    assertFalse(dv.isDefunct());
  }

  public void testCancelVersion() throws Exception {
    DomainVersion dv = ZkDomainVersion.create(getZk(), getRoot(), 1, null, null);
    assertEquals(1, dv.getVersionNumber());
    assertNull(dv.getClosedAt());
    assertFalse(DomainVersions.isClosed(dv));

    dv.cancel();
    assertNull(getZk().exists(ZkPath.append(getRoot(), "versions/version_1"), false));
  }

  public void testCloseVersion() throws Exception {
    DomainVersion dv = ZkDomainVersion.create(getZk(), getRoot(), 1, null, null);
    assertEquals(1, dv.getVersionNumber());
    assertNull(dv.getClosedAt());
    assertFalse(DomainVersions.isClosed(dv));

    dv.close();

    assertNotNull(dv.getClosedAt());
    assertTrue(DomainVersions.isClosed(dv));
  }

  public void testPartitionProperties() throws Exception {
    DomainVersion dv = ZkDomainVersion.create(getZk(), getRoot(), 1, null, null);
    DomainVersion dv2 = new ZkDomainVersion(getZk(), ZkPath.append(getRoot(), "versions/version_1"), null);

    assertTrue(dv.getPartitionProperties().isEmpty());
    assertTrue(dv2.getPartitionProperties().isEmpty());

    dv.addPartitionProperties(1, 2, 3);
    // should be available immediately
    assertEquals(1, dv.getPartitionProperties().size());
    // should be available after a short wait
    Thread.sleep(1000);
    assertEquals(1, dv2.getPartitionProperties().size());
  }

  public void testDefunct() throws Exception {
    DomainVersion dv = ZkDomainVersion.create(getZk(), getRoot(), 1, null, null);
    DomainVersion otherDv = new ZkDomainVersion(getZk(), ZkPath.append(getRoot(), "versions/version_1"), null);

    assertFalse(dv.isDefunct());
    assertFalse(otherDv.isDefunct());

    dv.setDefunct(true);
    Thread.sleep(1000);
    assertTrue(dv.isDefunct());
    assertTrue(otherDv.isDefunct());

    dv.setDefunct(false);
    Thread.sleep(1000);
    assertFalse(dv.isDefunct());
    assertFalse(otherDv.isDefunct());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getZk().create(ZkPath.append(getRoot(), "versions"), null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
  }
}
