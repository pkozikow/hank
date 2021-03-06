package com.rapleaf.hank.storage.curly;

import com.rapleaf.hank.ZkTestCase;
import com.rapleaf.hank.compress.NoCompressionCodec;
import com.rapleaf.hank.coordinator.mock.MockDomain;
import com.rapleaf.hank.coordinator.mock.MockDomainVersion;
import com.rapleaf.hank.hasher.Murmur64Hasher;
import com.rapleaf.hank.storage.LocalPartitionRemoteFileOps;
import com.rapleaf.hank.storage.RemoteDomainVersionDeleter;
import com.rapleaf.hank.storage.Writer;
import com.rapleaf.hank.storage.incremental.IncrementalDomainVersionProperties;

import java.io.File;
import java.nio.ByteBuffer;

public class TestCurlyRemoteDomainVersionDeleter extends ZkTestCase {
  private String localDiskRoot = localTmpDir + "/local_disk_root";
  private ByteBuffer key = ByteBuffer.wrap(new byte[]{1});
  private ByteBuffer value = ByteBuffer.wrap(new byte[]{2});

  public void testIt() throws Exception {
    final Curly storageEngine = new Curly(1, new Murmur64Hasher(), 100000, 1, 1000, localDiskRoot,
        new LocalPartitionRemoteFileOps.Factory(), NoCompressionCodec.class,
        new MockDomain("domain", 0, 1, null, null, null, null),
        0, -1, -1, -1);
    Writer writer = storageEngine.getWriter(new MockDomainVersion(1, 0L, new IncrementalDomainVersionProperties.Base()),
        new LocalPartitionRemoteFileOps(localDiskRoot, 0), 0);
    writer.write(key, value);
    writer.close();
    writer = storageEngine.getWriter(new MockDomainVersion(2, 0L, new IncrementalDomainVersionProperties.Delta(1)),
        new LocalPartitionRemoteFileOps(localDiskRoot, 0), 0);
    writer.write(key, value);
    writer.close();

    assertTrue(new File(localDiskRoot + "/0/00001.base.cueball").exists());
    assertTrue(new File(localDiskRoot + "/0/00002.delta.cueball").exists());
    assertTrue(new File(localDiskRoot + "/0/00001.base.curly").exists());
    assertTrue(new File(localDiskRoot + "/0/00002.delta.curly").exists());

    final RemoteDomainVersionDeleter cleaner = storageEngine.getRemoteDomainVersionDeleter();
    cleaner.deleteVersion(1);

    assertFalse(new File(localDiskRoot + "/0/00001.base.cueball").exists());
    assertTrue(new File(localDiskRoot + "/0/00002.delta.cueball").exists());
    assertFalse(new File(localDiskRoot + "/0/00001.base.curly").exists());
    assertTrue(new File(localDiskRoot + "/0/00002.delta.curly").exists());
  }
}
