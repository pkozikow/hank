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

package com.rapleaf.hank.storage.cueball;

import com.rapleaf.hank.compress.CompressionCodec;
import com.rapleaf.hank.coordinator.Domain;
import com.rapleaf.hank.coordinator.DomainVersion;
import com.rapleaf.hank.storage.IncrementalPartitionUpdater;
import com.rapleaf.hank.storage.IncrementalUpdatePlan;
import com.rapleaf.hank.storage.PartitionRemoteFileOps;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CueballPartitionUpdater extends IncrementalPartitionUpdater {

  private static final Logger LOG = Logger.getLogger(CueballPartitionUpdater.class);

  private final PartitionRemoteFileOps partitionRemoteFileOps;
  private final int keyHashSize;
  private final int valueSize;
  private final ICueballMerger merger;
  private final CompressionCodec compressionCodec;
  private final int hashIndexBits;

  public CueballPartitionUpdater(Domain domain,
                                 PartitionRemoteFileOps partitionRemoteFileOps,
                                 int keyHashSize,
                                 int valueSize,
                                 ICueballMerger merger,
                                 CompressionCodec compressionCodec,
                                 int hashIndexBits,
                                 String localPartitionRoot) throws IOException {
    super(domain, localPartitionRoot);
    this.partitionRemoteFileOps = partitionRemoteFileOps;
    this.keyHashSize = keyHashSize;
    this.valueSize = valueSize;
    this.merger = merger;
    this.compressionCodec = compressionCodec;
    this.hashIndexBits = hashIndexBits;
  }

  @Override
  protected Integer detectCurrentVersionNumber() throws IOException {
    SortedSet<CueballFilePath> localBases = Cueball.getBases(localPartitionRoot);
    if (localBases.size() > 0) {
      return localBases.last().getVersion();
    } else {
      return null;
    }
  }

  // TODO: determining the parent domain version should be based on DomainVersion metadata instead
  @Override
  protected DomainVersion getParentDomainVersion(DomainVersion domainVersion) throws IOException {
    if (partitionRemoteFileOps.exists(Cueball.getName(domainVersion.getVersionNumber(), true))) {
      // Base file exists, there is no parent
      return null;
    } else if (partitionRemoteFileOps.exists(Cueball.getName(domainVersion.getVersionNumber(), false))) {
      // Delta file exists, the parent is just the previous version based on version number
      int versionNumber = domainVersion.getVersionNumber();
      if (versionNumber <= 0) {
        return null;
      } else {
        DomainVersion result = domain.getVersionByNumber(versionNumber - 1);
        if (result == null) {
          throw new IOException("Failed to find version numbered " + (versionNumber - 1)
              + " of domain " + domain
              + " which was determined be the parent of domain version " + domainVersion);
        }
        return result;
      }
    } else {
      throw new IOException("Failed to determine parent version of domain version: " + domainVersion);
    }
  }

  @Override
  protected Set<DomainVersion> detectCachedBasesCore() throws IOException {
    return detectCachedVersions(Cueball.getBases(localPartitionRootCache));
  }

  @Override
  protected Set<DomainVersion> detectCachedDeltasCore() throws IOException {
    return detectCachedVersions(Cueball.getDeltas(localPartitionRootCache));
  }

  private Set<DomainVersion> detectCachedVersions(SortedSet<CueballFilePath> cachedFiles) throws IOException {
    Set<DomainVersion> cachedVersions = new HashSet<DomainVersion>();
    for (CueballFilePath file : cachedFiles) {
      DomainVersion version = domain.getVersionByNumber(file.getVersion());
      if (version != null) {
        cachedVersions.add(version);
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
    if (partitionRemoteFileOps.exists(Cueball.getName(version.getVersionNumber(), true))) {
      isBase = true;
    } else if (partitionRemoteFileOps.exists(Cueball.getName(version.getVersionNumber(), false))) {
      isBase = false;
    }
    if (isBase == null) {
      throw new IOException("Failed to determine if version was a base or a delta: " + version);
    }
    // Fetch version files
    String fileToFetch = Cueball.getName(version.getVersionNumber(), isBase);
    LOG.info("Fetching " + fileToFetch + " from " + partitionRemoteFileOps + " to " + fetchRoot);
    partitionRemoteFileOps.copyToLocalRoot(fileToFetch, fetchRoot);
  }

  @Override
  protected void runUpdateCore(DomainVersion currentVersion,
                               DomainVersion updatingToVersion,
                               IncrementalUpdatePlan updatePlan,
                               String updateWorkRoot) throws IOException {

    String newBasePath = updateWorkRoot + "/"
        + Cueball.getName(updatingToVersion.getVersionNumber(), true);

    // Determine files from versions
    CueballFilePath base = getFilePathForVersion(updatePlan.getBase(), currentVersion, true);
    List<CueballFilePath> deltas = new ArrayList<CueballFilePath>();
    for (DomainVersion delta : updatePlan.getDeltasOrdered()) {
      deltas.add(getFilePathForVersion(delta, currentVersion, false));
    }

    // Check that all required files are available
    checkRequiredFileExists(base.getPath());
    for (CueballFilePath delta : deltas) {
      checkRequiredFileExists(delta.getPath());
    }

    // If there are no deltas, simply move the required base to the target version.
    // Otherwise, perform merging.
    if (deltas.size() == 0) {
      if (!new File(base.getPath()).renameTo(new File(newBasePath))) {
        throw new IOException("Failed to rename Cueball base: " + base.getPath() + " to: " + newBasePath);
      }
    } else {
      merger.merge(base,
          deltas,
          newBasePath,
          keyHashSize,
          valueSize,
          null,
          hashIndexBits,
          compressionCodec);
    }
  }

  private CueballFilePath getFilePathForVersion(DomainVersion version,
                                                DomainVersion currentVersion,
                                                boolean isBase) {
    if (currentVersion != null && currentVersion.equals(version)) {
      // If version is current version, data is in root
      return new CueballFilePath(localPartitionRoot + "/" + Cueball.getName(version.getVersionNumber(), isBase));
    } else {
      // Otherwise, version must be in cache
      return new CueballFilePath(localPartitionRootCache + "/" + Cueball.getName(version.getVersionNumber(), isBase));
    }
  }

  private void checkRequiredFileExists(String path) throws IOException {
    if (!new File(path).exists()) {
      throw new IOException("Could not find required file for merging: " + path);
    }
  }
}
