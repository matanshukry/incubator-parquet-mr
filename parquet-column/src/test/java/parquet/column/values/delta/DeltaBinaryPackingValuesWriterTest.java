/**
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package parquet.column.values.delta;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import parquet.bytes.BytesInput;
import parquet.column.values.ValuesReader;
import parquet.column.values.ValuesWriter;
import parquet.column.values.rle.RunLengthBitPackingHybridValuesWriter;
import parquet.io.ParquetDecodingException;

public class DeltaBinaryPackingValuesWriterTest {
  DeltaBinaryPackingValuesReader reader;
  private int blockSize;
  private int miniBlockNum;
  private ValuesWriter writer;
  private Random random;

  @Before
  public void setUp() {
    blockSize = 128;
    miniBlockNum = 4;
    writer = new DeltaBinaryPackingValuesWriter(blockSize, miniBlockNum, 100);
    random = new Random();
  }

  @Test(expected = IllegalArgumentException.class)
  public void miniBlockSizeShouldBeMultipleOf8() {
    new DeltaBinaryPackingValuesWriter(1281, 4, 100);
  }

  /* When data size is multiple of Block*/
  @Test
  public void shouldWriteWhenDataIsAlignedWithBlock() throws IOException {
    int[] data = new int[5 * blockSize];
    for (int i = 0; i < blockSize * 5; i++) {
      data[i] = random.nextInt();
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldWriteAndReadWhenBlockIsNotFullyWritten() throws IOException {
    int[] data = new int[blockSize - 3];
    for (int i = 0; i < data.length; i++) {
      data[i] = random.nextInt();
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldWriteAndReadWhenAMiniBlockIsNotFullyWritten() throws IOException {
    int miniBlockSize = blockSize / miniBlockNum;
    int[] data = new int[miniBlockSize - 3];
    for (int i = 0; i < data.length; i++) {
      data[i] = random.nextInt();
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldWriteNegativeDeltas() throws IOException {
    int[] data = new int[blockSize];
    for (int i = 0; i < data.length; i++) {
      data[i] = 10 - (i * 32 - random.nextInt(6));
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldWriteAndReadWhenDeltasAreSame() throws IOException {
    int[] data = new int[2 * blockSize];
    for (int i = 0; i < blockSize; i++) {
      data[i] = i * 32;
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldWriteAndReadWhenValuesAreSame() throws IOException {
    int[] data = new int[2 * blockSize];
    for (int i = 0; i < blockSize; i++) {
      data[i] = 3;
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldWriteWhenDeltaIs0ForEachBlock() throws IOException {
    int[] data = new int[5 * blockSize + 1];
    for (int i = 0; i < data.length; i++) {
      data[i] = (i - 1) / blockSize;
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldReadWriteWhenDataIsNotAlignedWithBlock() throws IOException {
    int[] data = new int[5 * blockSize + 3];
    for (int i = 0; i < data.length; i++) {
      data[i] = random.nextInt(20) - 10;
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldReadMaxMinValue() throws IOException {
    int[] data = new int[10];
    for (int i = 0; i < data.length; i++) {
      if(i%2==0) {
        data[i]=Integer.MIN_VALUE;
      }else {
        data[i]=Integer.MAX_VALUE;
      }
    }
    shouldWriteAndRead(data);
  }

  @Test
  public void shouldReturnCorrectOffsetAfterInitialization() throws IOException {
    int[] data = new int[2 * blockSize + 3];
    for (int i = 0; i < data.length; i++) {
      data[i] = i * 32;
    }
    writeData(data);

    reader = new DeltaBinaryPackingValuesReader();
    BytesInput bytes = writer.getBytes();
    byte[] valueContent = bytes.toByteArray();
    byte[] pageContent = new byte[valueContent.length * 10];
    int contentOffsetInPage = 33;
    System.arraycopy(valueContent, 0, pageContent, contentOffsetInPage, valueContent.length);

    //offset should be correct
    reader.initFromPage(100, pageContent, contentOffsetInPage);
    int offset= reader.getNextOffset();
    assertEquals(valueContent.length + contentOffsetInPage, offset);

    //should be able to read data correclty
    for (int i : data) {
      assertEquals(i, reader.readInteger());
    }
  }

  @Test
  public void shouldThrowExceptionWhenReadMoreThanWritten() throws IOException {
    int[] data = new int[5 * blockSize + 1];
    for (int i = 0; i < data.length; i++) {
      data[i] = i * 32;
    }
    shouldWriteAndRead(data);
    try {
      reader.readInteger();
    } catch (ParquetDecodingException e) {
      assertEquals("no more value to read, total value count is " + data.length, e.getMessage());
    }

  }

  private void withRLE() {
    writer = new RunLengthBitPackingHybridValuesWriter(32, 100);
//    reader = new RunLengthBitPackingHybridValuesReader(32);
  }

  public void readingPerfTest() throws IOException {
    int round = 100;
    int[] data = new int[1000 * blockSize];
    for (int i = 0; i < data.length; i++) {
      data[i] = random.nextInt();
    }
//    writer=new RunLengthBitPackingHybridValuesWriter(32,100);
    for (int i = 0; i < data.length; i++) {
      writer.writeInteger(data[i]);
    }
    byte[] page = writer.getBytes().toByteArray();

    double avg = 0.0;
    ValuesReader reader;
    for (int i = 0; i < round; i++) {
//      System.out.print("<");
      long startTime = System.nanoTime();

//      reader = new RunLengthBitPackingHybridValuesReader(32);
      reader = new DeltaBinaryPackingValuesReader();

      reader.initFromPage(100, page, 0);

      for (int j = 0; j < data.length; j++) {
        reader.readInteger();
      }

      long endTime = System.nanoTime();
      long duration = endTime - startTime;
      avg += (double) duration / round;
    }
    System.out.println("average time is " + avg);

  }

  @Test
  public void writingPerfTest() throws IOException {
//    withRLE();
    int round = 1000;
    int warmup = 10000;
    int[] data = new int[1000 * blockSize];
    for (int i = 0; i < data.length; i++) {
      data[i] = random.nextInt(100) - 200;
    }


//    ValuesWriter writer=new RunLengthBitPackingHybridValuesWriter(32,100);
    double avg = 0.0;
    for (int i = 0; i < round + warmup; i++) {
//      System.out.print("<");
      writer.reset();
      long startTime = System.nanoTime();
      writeData(data);
      long endTime = System.nanoTime();
      long duration = endTime - startTime;

      if (i > warmup) {
        avg += (double) duration / round;
      }

//      System.out.println(">time consumed " + duration);
    }

    System.out.println("average time is " + avg);

  }

  @Test
  public void shouldSkip() throws IOException {
    int[] data = new int[5 * blockSize + 1];
    for (int i = 0; i < data.length; i++) {
      data[i] = i * 32;
    }
    writeData(data);
    reader = new DeltaBinaryPackingValuesReader();
    reader.initFromPage(100, writer.getBytes().toByteArray(), 0);
    for (int i = 0; i < data.length; i++) {
      if (i % 3 == 0) {
        reader.skip();
      } else {
        assertEquals(i * 32, reader.readInteger());
      }
    }
  }

  @Test
  public void shouldReset() throws IOException {
    shouldReadWriteWhenDataIsNotAlignedWithBlock();
    int[] data = new int[5 * blockSize];
    for (int i = 0; i < blockSize * 5; i++) {
      data[i] = i * 2;
    }
    writer.reset();
    shouldWriteAndRead(data);
  }

  @Test
  public void randomDataTest() throws IOException {
    int maxSize = 1000;
    int[] data = new int[maxSize];

    for (int round = 0; round < 100000; round++) {


      int size = random.nextInt(maxSize);

      for (int i = 0; i < size; i++) {
        data[i] = random.nextInt();
      }
      shouldReadAndWrite(data, size);
      writer.reset();
    }
  }

  private void shouldWriteAndRead(int[] data) throws IOException {
    shouldReadAndWrite(data, data.length);
  }

  private void shouldReadAndWrite(int[] data, int length) throws IOException {

    writeData(data, length);
    reader = new DeltaBinaryPackingValuesReader();
    byte[] page = writer.getBytes().toByteArray();
    int miniBlockSize = blockSize / miniBlockNum;

    double miniBlockFlushed = Math.ceil(((double) length - 1) / miniBlockSize);
    double blockFlushed = Math.ceil(((double) length - 1) / blockSize);
    double estimatedSize = 4 * 5 //blockHeader
            + 4 * miniBlockFlushed * miniBlockSize //data(aligned to miniBlock)
            + blockFlushed * miniBlockNum //bitWidth of mini blocks
            + (5.0 * blockFlushed);//min delta for each block
    assertTrue(estimatedSize >= page.length);
    double avg = (double) page.length / length;
//    System.out.println("avg length for integer is "+ avg);
    reader.initFromPage(100, page, 0);

    for (int i = 0; i < length; i++) {
      assertEquals(data[i], reader.readInteger());
    }
  }

  private void writeData(int[] data) {
    writeData(data, data.length);
  }

  private void writeData(int[] data, int length) {
    for (int i = 0; i < length; i++) {
      writer.writeInteger(data[i]);
    }
  }

}