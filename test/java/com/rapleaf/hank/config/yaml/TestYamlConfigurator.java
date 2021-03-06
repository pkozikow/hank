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
package com.rapleaf.hank.config.yaml;

import com.rapleaf.hank.BaseTestCase;
import com.rapleaf.hank.config.InvalidConfigurationException;
import com.rapleaf.hank.coordinator.Coordinator;
import com.rapleaf.hank.coordinator.mock.MockCoordinator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TestYamlConfigurator extends BaseTestCase {
  private final String configPath = localTmpDir + "/bad4.yml";

  private static class TestImplOfBaseYamlConfigurator extends YamlCoordinatorConfigurator {
    public TestImplOfBaseYamlConfigurator(String path) throws IOException,
        InvalidConfigurationException {
      super(path);
    }
  }

  public void testIt() throws Exception {
    PrintWriter pw = new PrintWriter(new FileWriter(configPath));
    pw.println("cordinator:");
    pw.close();
    try {
      new TestImplOfBaseYamlConfigurator(configPath);
      fail("should have thrown an exception");
    } catch (InvalidConfigurationException e) {
      //yay!
    }

    pw = new PrintWriter(new FileWriter(configPath));
    pw.println("coordinator:");
    pw.close();
    try {
      new TestImplOfBaseYamlConfigurator(configPath);
      fail("should have thrown an exception");
    } catch (InvalidConfigurationException e) {
      //yay!
    }

    pw = new PrintWriter(new FileWriter(configPath));
    pw.println("coordinator:");
    pw.println("  blah: blah");
    pw.close();
    try {
      new TestImplOfBaseYamlConfigurator(configPath);
      fail("should have thrown an exception");
    } catch (InvalidConfigurationException e) {
      //yay!
    }

    pw = new PrintWriter(new FileWriter(configPath));
    pw.println("coordinator:");
    pw.println("  factory: 1");
    pw.close();
    try {
      new TestImplOfBaseYamlConfigurator(configPath);
      fail("should have thrown an exception");
    } catch (InvalidConfigurationException e) {
      //yay!
    }

    pw = new PrintWriter(new FileWriter(configPath));
    pw.println("coordinator:");
    pw.println("  factory: " + MockCoordinator.Factory.class.getName());
    pw.close();
    try {
      new TestImplOfBaseYamlConfigurator(configPath);
      fail("should have thrown an exception");
    } catch (InvalidConfigurationException e) {
      //yay!
    }

    pw = new PrintWriter(new FileWriter(configPath));
    pw.println("coordinator:");
    pw.println("  factory: " + MockCoordinator.Factory.class.getName());
    pw.println("  options:");
    pw.println("    blah: blah");
    pw.close();
    TestImplOfBaseYamlConfigurator conf = new TestImplOfBaseYamlConfigurator(configPath);
    Coordinator coordinator = conf.createCoordinator();
    assertTrue(coordinator instanceof MockCoordinator);
  }
}
