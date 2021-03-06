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

package com.rapleaf.hank.coordinator;

import com.rapleaf.hank.partition_server.FilesystemStatisticsAggregator;
import com.rapleaf.hank.partition_server.RuntimeStatisticsAggregator;
import com.rapleaf.hank.partition_server.UpdateManager;

import java.io.IOException;
import java.util.*;

public class Rings {

  /**
   * Enqueue <i>command</i> to all Hosts in this Ring.
   *
   * @param ring
   * @param command
   * @throws IOException
   */
  public static void commandAll(Ring ring, HostCommand command) throws IOException {
    for (Host host : ring.getHosts()) {
      host.enqueueCommand(command);
    }
  }

  /**
   * Get the set of Hosts that can serve a given domain's partition.
   *
   * @param ring
   * @param domain
   * @param partition
   * @return
   * @throws IOException
   */
  public static Set<Host> getHostsForDomainPartition(Ring ring, Domain domain, int partition) throws IOException {
    Set<Host> results = new HashSet<Host>();
    for (Host host : ring.getHosts()) {
      HostDomain domainById = host.getHostDomain(domain);
      for (HostDomainPartition hdpc : domainById.getPartitions()) {
        if (hdpc.getPartitionNumber() == partition) {
          results.add(host);
          break;
        }
      }
    }
    return results;
  }


  /**
   * Return all the hosts that are in the requested state.
   *
   * @param ring
   * @param state
   * @return
   * @throws IOException
   */
  public static Set<Host> getHostsInState(Ring ring, HostState state) throws IOException {
    Set<Host> results = new HashSet<Host>();
    for (Host host : ring.getHosts()) {
      if (host.getState() == state) {
        results.add(host);
      }
    }
    return results;
  }

  /**
   * Return true iff there is at least one assigned partition in the given ring,
   * and all partitions in the given ring have a current version that is not null (servable).
   *
   * @param ring
   * @return
   * @throws IOException
   */
  public static boolean isServable(Ring ring) throws IOException {
    int numPartitions = 0;
    for (Host host : ring.getHosts()) {
      for (HostDomain hostDomain : host.getAssignedDomains()) {
        for (HostDomainPartition hostDomainPartition : hostDomain.getPartitions()) {
          ++numPartitions;
          if (hostDomainPartition.getCurrentDomainGroupVersion() == null) {
            return false;
          }
        }
      }
    }
    return numPartitions != 0;
  }

  /**
   * Return true if each host in the given ring is considered up-to-date.
   *
   * @param ring
   * @param domainGroupVersion
   * @return
   * @throws IOException
   */
  public static boolean isUpToDate(Ring ring, DomainGroupVersion domainGroupVersion) throws IOException {
    for (Host host : ring.getHosts()) {
      if (!Hosts.isUpToDate(host, domainGroupVersion)) {
        return false;
      }
    }
    return true;
  }

  public static UpdateProgress computeUpdateProgress(Ring ring,
                                                     DomainGroupVersion domainGroupVersion) throws IOException {
    UpdateProgress result = new UpdateProgress();
    for (Host host : ring.getHosts()) {
      result.aggregate(Hosts.computeUpdateProgress(host, domainGroupVersion));
    }
    return result;
  }

  public static ServingStatusAggregator
  computeServingStatusAggregator(Ring ring, DomainGroupVersion domainGroupVersion) throws IOException {
    ServingStatusAggregator servingStatusAggregator = new ServingStatusAggregator();
    for (Host host : ring.getHosts()) {
      servingStatusAggregator.aggregate(Hosts.computeServingStatusAggregator(host, domainGroupVersion));
    }
    return servingStatusAggregator;
  }

  public static Map<Host, Map<Domain, RuntimeStatisticsAggregator>>
  computeRuntimeStatistics(Coordinator coordinator, Ring ring) throws IOException {
    Map<Host, Map<Domain, RuntimeStatisticsAggregator>> result =
        new HashMap<Host, Map<Domain, RuntimeStatisticsAggregator>>();
    for (Host host : ring.getHosts()) {
      result.put(host, Hosts.computeRuntimeStatistics(coordinator, host));
    }
    return result;
  }

  public static RuntimeStatisticsAggregator
  computeRuntimeStatisticsForRing(Map<Host, Map<Domain, RuntimeStatisticsAggregator>> runtimeStatistics) {
    RuntimeStatisticsAggregator result = new RuntimeStatisticsAggregator();
    for (Map.Entry<Host, Map<Domain, RuntimeStatisticsAggregator>> entry1 : runtimeStatistics.entrySet()) {
      for (Map.Entry<Domain, RuntimeStatisticsAggregator> entry2 : entry1.getValue().entrySet()) {
        result.add(entry2.getValue());
      }
    }
    return result;
  }

  public static RuntimeStatisticsAggregator
  computeRuntimeStatisticsForHost(Map<Host, Map<Domain, RuntimeStatisticsAggregator>> runtimeStatistics,
                                  Host host) {
    if (runtimeStatistics.containsKey(host)) {
      return Hosts.computeRuntimeStatisticsForHost(runtimeStatistics.get(host));
    } else {
      return new RuntimeStatisticsAggregator();
    }
  }

  public static SortedMap<Domain, RuntimeStatisticsAggregator>
  computeRuntimeStatisticsForDomains(
      Map<Host, Map<Domain, RuntimeStatisticsAggregator>> runtimeStatistics) {
    SortedMap<Domain, RuntimeStatisticsAggregator> result = new TreeMap<Domain, RuntimeStatisticsAggregator>();
    for (Map.Entry<Host, Map<Domain, RuntimeStatisticsAggregator>> entry1 : runtimeStatistics.entrySet()) {
      for (Map.Entry<Domain, RuntimeStatisticsAggregator> entry2 : entry1.getValue().entrySet()) {
        RuntimeStatisticsAggregator aggregator = result.get(entry2.getKey());
        if (aggregator == null) {
          aggregator = new RuntimeStatisticsAggregator();
          result.put(entry2.getKey(), aggregator);
        }
        aggregator.add(entry2.getValue());
      }
    }
    return result;
  }

  public static Map<Host, Map<String, FilesystemStatisticsAggregator>>
  computeFilesystemStatistics(Ring ring) throws IOException {
    Map<Host, Map<String, FilesystemStatisticsAggregator>> result =
        new HashMap<Host, Map<String, FilesystemStatisticsAggregator>>();
    for (Host host : ring.getHosts()) {
      result.put(host, Hosts.computeFilesystemStatistics(host));
    }
    return result;
  }

  public static FilesystemStatisticsAggregator
  computeFilesystemStatisticsForRing(Map<Host, Map<String, FilesystemStatisticsAggregator>> filesystemStatistics) {
    FilesystemStatisticsAggregator result = new FilesystemStatisticsAggregator();
    Set<String> hostAndFilesystemRootsAdded = new HashSet<String>();
    for (Map.Entry<Host, Map<String, FilesystemStatisticsAggregator>> entry1 : filesystemStatistics.entrySet()) {
      for (Map.Entry<String, FilesystemStatisticsAggregator> entry2 : entry1.getValue().entrySet()) {
        String hostAndFilesystemRoot = entry1.getKey().getAddress().getHostName() + entry2.getKey();
        if (!hostAndFilesystemRootsAdded.contains(hostAndFilesystemRoot)) {
          hostAndFilesystemRootsAdded.add(hostAndFilesystemRoot);
          result.add(entry2.getValue());
        }
      }
    }
    return result;
  }

  public static FilesystemStatisticsAggregator
  computeFilesystemStatisticsForHost(Map<Host, Map<String, FilesystemStatisticsAggregator>> filesystemStatistics,
                                     Host host) {
    if (filesystemStatistics.containsKey(host)) {
      return Hosts.computeFilesystemStatisticsForHost(filesystemStatistics.get(host));
    } else {
      return new FilesystemStatisticsAggregator();
    }
  }

  public static long computeUpdateETA(Ring ring) {
    long maxUpdateETA = -1;
    for (Host host : ring.getHosts()) {
      long hostUpdateETA = UpdateManager.getUpdateETA(host);
      if (hostUpdateETA > maxUpdateETA) {
        maxUpdateETA = hostUpdateETA;
      }
    }
    return maxUpdateETA;
  }
}
