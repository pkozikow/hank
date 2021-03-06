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

import com.rapleaf.hank.util.FsUtils;
import junit.framework.TestCase;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TestCurlyMerger extends TestCase {
  private static final String LOCAL_PARTITION_ROOT = "/tmp/TestCurlyMerger/local";

  public void setUp() throws Exception {
    FsUtils.rmrf(LOCAL_PARTITION_ROOT);
    new File(LOCAL_PARTITION_ROOT).mkdirs();
  }

  private static final CurlyFilePath BASE = new CurlyFilePath(LOCAL_PARTITION_ROOT + "00000.base.curly");
  private static final byte[] BASE_DATA = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
  private static final CurlyFilePath DELTA1 = new CurlyFilePath(LOCAL_PARTITION_ROOT + "00001.delta.curly");
  private static final byte[] DELTA1_DATA = {11, 12, 13};
  private static final CurlyFilePath DELTA2 = new CurlyFilePath(LOCAL_PARTITION_ROOT + "00002.delta.curly");
  private static final byte[] DELTA2_DATA = {14, 15, 16};

  public void testMerge() throws Exception {
    OutputStream s = new FileOutputStream(BASE.getPath());
    s.write(BASE_DATA);
    s.flush();
    s.close();

    s = new FileOutputStream(DELTA1.getPath());
    s.write(DELTA1_DATA);
    s.flush();
    s.close();

    s = new FileOutputStream(DELTA2.getPath());
    s.write(DELTA2_DATA);
    s.flush();
    s.close();

    long[] offsetAdjustments = new CurlyMerger().merge(BASE, Arrays.asList(DELTA1, DELTA2));

    assertEquals(3, offsetAdjustments.length);
    assertEquals(0, offsetAdjustments[0]);
    assertEquals(11, offsetAdjustments[1]);
    assertEquals(14, offsetAdjustments[2]);

    byte[] merged = new byte[17];
    DataInputStream in = new DataInputStream(new FileInputStream(BASE.getPath()));
    in.readFully(merged);

    assertEquals(ByteBuffer.allocate(17).put(BASE_DATA).put(DELTA1_DATA).put(DELTA2_DATA).rewind(), ByteBuffer.wrap(merged));
  }
}
