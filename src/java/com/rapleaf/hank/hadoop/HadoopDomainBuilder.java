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

package com.rapleaf.hank.hadoop;

import com.rapleaf.hank.config.CoordinatorConfigurator;
import com.rapleaf.hank.config.InvalidConfigurationException;
import com.rapleaf.hank.config.yaml.YamlClientConfigurator;
import com.rapleaf.hank.util.CommandLineChecker;
import org.apache.commons.lang.NotImplementedException;
import org.apache.hadoop.mapred.*;
import org.apache.log4j.Logger;

import java.io.IOException;

public class HadoopDomainBuilder extends AbstractHadoopDomainBuilder {

  private static final Logger LOG = Logger.getLogger(HadoopDomainBuilder.class);

  private final String inputPath;
  private final Class<? extends InputFormat> inputFormatClass;
  private final Class<? extends Mapper> mapperClass;

  public HadoopDomainBuilder(final String inputPath,
                             final Class<? extends InputFormat> inputFormatClass,
                             final Class<? extends Mapper> mapperClass) {
    this.inputPath = inputPath;
    this.inputFormatClass = inputFormatClass;
    this.mapperClass = mapperClass;
  }

  public HadoopDomainBuilder(JobConf conf,
                             final String inputPath,
                             final Class<? extends InputFormat> inputFormatClass,
                             final Class<? extends Mapper> mapperClass) {
    super(conf);
    this.inputPath = inputPath;
    this.inputFormatClass = inputFormatClass;
    this.mapperClass = mapperClass;
  }

  // Use a non-default output format
  @Override
  protected void configureJob(JobConf conf) {
    // Input specification
    conf.setInputFormat(inputFormatClass);
    FileInputFormat.setInputPaths(conf, inputPath);
    // Mapper class and key/value classes
    conf.setMapperClass(mapperClass);
    conf.setMapOutputKeyClass(KeyAndPartitionWritableComparable.class);
    conf.setMapOutputValueClass(ValueWritable.class);
    // Reducer class and key/value classes
    conf.setReducerClass(DomainBuilderReducer.class);
    conf.setOutputKeyClass(KeyAndPartitionWritable.class);
    conf.setOutputValueClass(ValueWritable.class);
    // Partitioner
    conf.setPartitionerClass(DomainBuilderPartitioner.class);
  }

  public static void main(String[] args) throws IOException, InvalidConfigurationException {
    CommandLineChecker.check(args, new String[]
        {"domain name", "config path", "jobjar", "input path", "output_path"},
        HadoopDomainBuilder.class);
    String domainName = args[0];
    CoordinatorConfigurator configurator = new YamlClientConfigurator(args[1]);
    String jobJar = args[2];
    String inputPath = args[3];
    String outputPath = args[4];

    DomainBuilderProperties properties = new DomainBuilderProperties(domainName, configurator, outputPath);
    JobConf conf = new JobConf();
    conf.setJar(jobJar);
    conf.setJobName(HadoopDomainBuilder.class.getSimpleName()
        + " Domain " + domainName + ", Output path: " + outputPath);
    HadoopDomainBuilder builder = new HadoopDomainBuilder(conf, inputPath,
        SequenceFileInputFormat.class,
        DomainBuilderMapperDefault.class);
    LOG.info("Building Hank domain " + domainName + " from input " + inputPath
        + " and coordinator configuration " + configurator);
    // TODO: Create DomainVersionProperties
    throw new NotImplementedException("TODO: Create DomainVersionProperties");
    // builder.buildHankDomain(properties, null);
  }
}
