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

import com.rapleaf.hank.hasher.Murmur64Hasher;
import com.rapleaf.hank.storage.Writer;
import com.rapleaf.hank.util.Bytes;
import com.rapleaf.hank.util.EncodingHelper;
import com.rapleaf.hank.util.LruHashMap;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class CurlyWriter implements Writer {

  private static final int VALUE_FOLDING_HASH_NUM_BYTES = 16;

  private static final Murmur64Hasher murmur64Hasher = new Murmur64Hasher();

  private long currentRecordOffset;
  private long numFoldedValues = 0;
  private long numFoldedBytesApproximate = 0;

  private final Writer keyfileWriter;
  private final OutputStream recordFileStream;
  private final long maxOffset;
  private final ByteBuffer valueOffsetBuffer;
  private final byte[] valueLengthBuffer = new byte[5];
  private final LruHashMap<ByteBuffer, ByteBuffer> hashedValueToEncodedRecordOffsetCache;

  public CurlyWriter(OutputStream recordfileStream,
                     Writer keyfileWriter,
                     int offsetSize,
                     int valueFoldingCacheCapacity) {
    this.recordFileStream = recordfileStream;
    this.keyfileWriter = keyfileWriter;
    this.maxOffset = 1L << (offsetSize * 8);
    this.currentRecordOffset = 0;

    valueOffsetBuffer = ByteBuffer.wrap(new byte[offsetSize]);

    // Initialize LRU cache only when needed
    if (valueFoldingCacheCapacity > 0) {
      hashedValueToEncodedRecordOffsetCache = new LruHashMap<ByteBuffer, ByteBuffer>(valueFoldingCacheCapacity, valueFoldingCacheCapacity);
    } else {
      hashedValueToEncodedRecordOffsetCache = null;
    }
  }

  @Override
  public void close() throws IOException {
    recordFileStream.flush();
    recordFileStream.close();
    keyfileWriter.close();
    if (hashedValueToEncodedRecordOffsetCache != null) {
      hashedValueToEncodedRecordOffsetCache.clear();
    }
  }

  @Override
  public void write(ByteBuffer key, ByteBuffer value) throws IOException {
    if (currentRecordOffset > maxOffset) {
      throw new IOException("Exceeded configured max recordfile size of "
          + maxOffset
          + ". Increase number of partitions to go back below this level.");
    }

    ByteBuffer cachedValueRecordEncodedOffset = null;
    ByteBuffer hashedValue = null;

    // Retrieve cached offset if possible
    if (hashedValueToEncodedRecordOffsetCache != null) {
      hashedValue = computeHash(value);
      cachedValueRecordEncodedOffset = hashedValueToEncodedRecordOffsetCache.get(hashedValue);
    }

    if (cachedValueRecordEncodedOffset != null) {
      // Write cached offset in key file and nothing else needs to be done
      keyfileWriter.write(key, cachedValueRecordEncodedOffset);
      numFoldedValues += 1;
      numFoldedBytesApproximate += value.remaining();
    } else {
      EncodingHelper.encodeLittleEndianFixedWidthLong(currentRecordOffset, valueOffsetBuffer.array());
      // Write current offset in key file
      keyfileWriter.write(key, valueOffsetBuffer);
      // Value was not found in cache. Cache current value encoded offset buffer if needed
      if (hashedValueToEncodedRecordOffsetCache != null) {
        hashedValueToEncodedRecordOffsetCache.put(hashedValue, Bytes.byteBufferDeepCopy(valueOffsetBuffer));
      }
      int valueLength = value.remaining();
      int valueLengthNumBytes = EncodingHelper.encodeLittleEndianVarInt(valueLength, valueLengthBuffer);
      // Write var int representing value length
      recordFileStream.write(valueLengthBuffer, 0, valueLengthNumBytes);
      // Write value
      recordFileStream.write(value.array(), value.arrayOffset() + value.position(), valueLength);
      // Advance record offset
      currentRecordOffset += valueLengthNumBytes + valueLength;
    }
  }

  private ByteBuffer computeHash(ByteBuffer value) {
    // 128-bit murmur64 hash
    byte[] hashBytes = new byte[VALUE_FOLDING_HASH_NUM_BYTES];
    murmur64Hasher.hash(value, VALUE_FOLDING_HASH_NUM_BYTES, hashBytes);
    return ByteBuffer.wrap(hashBytes);
  }

  @Override
  public long getNumBytesWritten() {
    return keyfileWriter.getNumBytesWritten() + currentRecordOffset;
  }

  @Override
  public long getNumRecordsWritten() {
    return keyfileWriter.getNumRecordsWritten();
  }

  @Override
  public String toString() {
    return "CurlyWriter [keyFileWriter=" + keyfileWriter.toString()
        + ", numRecordsWritten=" + getNumRecordsWritten()
        + ", numBytesWritten=" + getNumBytesWritten()
        + ", numFoldedValues=" + numFoldedValues
        + ", numFoldedBytesApproximate=" + numFoldedBytesApproximate
        + "]";
  }
}
