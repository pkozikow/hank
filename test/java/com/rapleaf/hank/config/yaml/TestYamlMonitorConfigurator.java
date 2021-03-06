package com.rapleaf.hank.config.yaml;

import com.rapleaf.hank.BaseTestCase;
import com.rapleaf.hank.coordinator.MockRingGroup;
import com.rapleaf.hank.monitor.notifier.mock.MockNotifier;
import com.rapleaf.hank.monitor.notifier.Notifier;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

public class TestYamlMonitorConfigurator extends BaseTestCase {

  private final String configPath = localTmpDir + "/config.yml";

  public void testIt() throws Exception {
    PrintWriter pw = new PrintWriter(new FileWriter(configPath));
    pw.println("monitor:");
    pw.println("  web_ui_url: http://hank_web_ui_url");

    pw.println("  notifier_configurations:");

    pw.println("    notifier1:");
    pw.println("      factory: com.rapleaf.hank.monitor.notifier.mock.MockNotifierFactory");
    pw.println("      configuration:");
    pw.println("        b: b");

    pw.println("    notifier2:");
    pw.println("      factory: com.rapleaf.hank.monitor.notifier.mock.MockNotifierFactory");
    pw.println("      configuration:");
    pw.println("        c: c");

    pw.println("  global_notifier_configurations:");
    pw.println("      - notifier1");

    pw.println("  ring_group_notifier_configurations:");

    pw.println("    rg1:");
    pw.println("      - notifier1");
    pw.println("      - notifier2");
    pw.println("    rg2:");
    pw.println("      - notifier2");

    pw.close();

    YamlMonitorConfigurator configurator = new YamlMonitorConfigurator(configPath);

    Notifier globalNotifier = configurator.getGlobalNotifiers().get(0);
    assertTrue(globalNotifier instanceof MockNotifier);
    assertEquals("b", ((MockNotifier) globalNotifier).getConfiguration().get("b"));

    List<Notifier> ringNotifiers1 = configurator.getRingGroupNotifiers(new MockRingGroup(null, "rg1", null, null));
    Notifier ringNotifier11 = ringNotifiers1.get(0);
    Notifier ringNotifier12 = ringNotifiers1.get(1);
    assertTrue(ringNotifier11 instanceof MockNotifier);
    assertTrue(ringNotifier12 instanceof MockNotifier);
    assertEquals("b", ((MockNotifier) ringNotifier11).getConfiguration().get("b"));
    assertEquals("c", ((MockNotifier) ringNotifier12).getConfiguration().get("c"));

    List<Notifier> ringNotifiers2 = configurator.getRingGroupNotifiers(new MockRingGroup(null, "rg2", null, null));
    Notifier ringNotifier22 = ringNotifiers2.get(0);
    assertTrue(ringNotifier22 instanceof MockNotifier);
    assertEquals("c", ((MockNotifier) ringNotifier22).getConfiguration().get("c"));
  }

}
