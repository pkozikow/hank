/**
 *  Copyright 2011 Rapleaf
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.rapleaf.hank.storage.curly;

import com.rapleaf.hank.coordinator.Domain;
import com.rapleaf.hank.coordinator.DomainVersion;
import com.rapleaf.hank.storage.incremental.IncrementalDomainVersionProperties;
import com.rapleaf.hank.storage.incremental.IncrementalPartitionUpdater;
import com.rapleaf.hank.storage.incremental.IncrementalUpdatePlan;
import com.rapleaf.hank.storage.PartitionRemoteFileOps;
import com.rapleaf.hank.storage.cueball.Cueball;
import com.rapleaf.hank.storage.cueball.CueballFilePath;
import com.rapleaf.hank.storage.cueball.ValueTransformer;
import com.rapleaf.hank.util.EncodingHelper;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

public abstract class AbstractCurlyPartitionUpdater extends IncrementalPartitionUpdater {

  private static final Logger LOG = Logger.getLogger(CurlyFastPartitionUpdater.class);

  protected final PartitionRemoteFileOps partitionRemoteFileOps;

  public AbstractCurlyPartitionUpdater(Domain domain,
                                       PartitionRemoteFileOps partitionRemoteFileOps,
                                       String localPartitionRoot) throws IOException {
    super(domain, localPartitionRoot);
    this.partitionRemoteFileOps = partitionRemoteFileOps;
  }

  public static final class OffsetTransformer implements ValueTransformer {
    private final long[] offsetAdjustments;
    private final int offsetSize;

    public OffsetTransformer(int offsetSize, long[] offsetAdjustments) {
      this.offsetSize = offsetSize;
      this.offsetAdjustments = offsetAdjustments;
    }

    @Override
    public void transform(byte[] buf, int valueOff, int relIndex) {
      long adjustment = offsetAdjustments[relIndex];
      if (adjustment != 0) {
        long offset = EncodingHelper.decodeLittleEndianFixedWidthLong(buf, valueOff, offsetSize);
        offset += adjustment;
        EncodingHelper.encodeLittleEndianFixedWidthLong(offset, buf, valueOff, offsetSize);
      }
    }
  }

  @Override
  protected Integer detectCurrentVersionNumber() throws IOException {
    SortedSet<CueballFilePath> localCueballBases = Cueball.getBases(localPartitionRoot);
    SortedSet<CurlyFilePath> localCurlyBases = Curly.getBases(localPartitionRoot);
    if (localCueballBases.size() > 0 && localCurlyBases.size() > 0) {
      if (localCueballBases.last().getVersion() == localCurlyBases.last().getVersion()) {
        return localCurlyBases.last().getVersion();
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  protected DomainVersion getParentDomainVersion(DomainVersion domainVersion) throws IOException {
    return IncrementalDomainVersionProperties.getParentDomainVersion(domain, domainVersion);
  }

  public static boolean isEmptyVersion(PartitionRemoteFileOps partitionRemoteFileOps,
                                       DomainVersion domainVersion) throws IOException {
    return !partitionRemoteFileOps.exists(Cueball.getName(domainVersion.getVersionNumber(), true))
        && !partitionRemoteFileOps.exists(Cueball.getName(domainVersion.getVersionNumber(), false))
        && !partitionRemoteFileOps.exists(Curly.getName(domainVersion.getVersionNumber(), true))
        && !partitionRemoteFileOps.exists(Curly.getName(domainVersion.getVersionNumber(), false));
  }

  @Override
  protected Set<DomainVersion> detectCachedBasesCore() throws IOException {
    return detectCachedVersions(Cueball.getBases(localPartitionRootCache),
        Curly.getBases(localPartitionRootCache));
  }

  @Override
  protected Set<DomainVersion> detectCachedDeltasCore() throws IOException {
    return detectCachedVersions(Cueball.getDeltas(localPartitionRootCache),
        Curly.getDeltas(localPartitionRootCache));
  }

  private Set<DomainVersion> detectCachedVersions(SortedSet<CueballFilePath> cueballCachedFiles,
                                                  SortedSet<CurlyFilePath> curlyCachedFiles) throws IOException {
    // Record in a set all cached Cueball versions
    HashSet<Integer> cachedCueballVersions = new HashSet<Integer>();
    for (CueballFilePath cueballCachedFile : cueballCachedFiles) {
      cachedCueballVersions.add(cueballCachedFile.getVersion());
    }
    // Compute cached Curly versions
    Set<DomainVersion> cachedVersions = new HashSet<DomainVersion>();
    for (CurlyFilePath curlyCachedFile : curlyCachedFiles) {
      // Check that the corresponding Cueball version is also cached
      if (cachedCueballVersions.contains(curlyCachedFile.getVersion())) {
        DomainVersion version = domain.getVersion(curlyCachedFile.getVersion());
        if (version != null) {
          cachedVersions.add(version);
        }
      }
    }
    return cachedVersions;
  }

  @Override
  protected void cleanCachedVersions() throws IOException {
    // Delete all cached versions
    FileUtils.deleteDirectory(new File(localPartitionRootCache));
  }

  @Override
  protected void fetchVersion(DomainVersion version, String fetchRoot) throws IOException {
    // Determine if version is a base or delta
    // TODO: use version's metadata to determine if it's a base or a delta
    Boolean isBase = null;
    if (partitionRemoteFileOps.exists(Cueball.getName(version.getVersionNumber(), true))
        && partitionRemoteFileOps.exists(Curly.getName(version.getVersionNumber(), true))) {
      isBase = true;
    } else if (partitionRemoteFileOps.exists(Cueball.getName(version.getVersionNumber(), false))
        && partitionRemoteFileOps.exists(Curly.getName(version.getVersionNumber(), false))) {
      isBase = false;
    }
    if (isBase == null) {
      // If unable to determine if it's a base or delta, do not fetch anything
      LOG.error("Unable to determine if version " + version.getVersionNumber()
          + " is a base or a delta in " + partitionRemoteFileOps);
      return;
    }
    // Fetch version files
    String cueballFileToFetch = Cueball.getName(version.getVersionNumber(), isBase);
    String curlyFileToFetch = Curly.getName(version.getVersionNumber(), isBase);
    if (partitionRemoteFileOps.exists(cueballFileToFetch)) {
      LOG.info("Fetching from " + partitionRemoteFileOps + " for file " + cueballFileToFetch + " to " + fetchRoot);
      partitionRemoteFileOps.copyToLocalRoot(cueballFileToFetch, fetchRoot);
    }
    if (partitionRemoteFileOps.exists(curlyFileToFetch)) {
      LOG.info("Fetching from " + partitionRemoteFileOps + " for file " + curlyFileToFetch + " to " + fetchRoot);
      partitionRemoteFileOps.copyToLocalRoot(curlyFileToFetch, fetchRoot);
    }
  }

  @Override
  protected abstract void runUpdateCore(DomainVersion currentVersion,
                                        DomainVersion updatingToVersion,
                                        IncrementalUpdatePlan updatePlan,
                                        String updateWorkRoot) throws IOException;

  public CurlyFilePath getCurlyFilePathForVersion(DomainVersion version,
                                                  DomainVersion currentVersion,
                                                  boolean isBase) {
    if (currentVersion != null && currentVersion.equals(version)) {
      // If version is current version, data is in root
      return new CurlyFilePath(localPartitionRoot + "/" + Curly.getName(version.getVersionNumber(), isBase));
    } else {
      // Otherwise, version must be in cache
      return new CurlyFilePath(localPartitionRootCache + "/" + Curly.getName(version.getVersionNumber(), isBase));
    }
  }
}
