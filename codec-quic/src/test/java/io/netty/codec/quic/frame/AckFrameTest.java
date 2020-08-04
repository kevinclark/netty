/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.codec.quic.frame;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.ImmutableRangeSet.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.packet.ECNCounts;
import io.netty.codec.quic.util.QUICByteBufs;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

public class AckFrameTest {

    private ByteBuf encodeAckFrameWithDelayAndRanges(long delay, Iterable<Range<Long>> ranges) {
        ImmutableRangeSet.Builder<Long> builder = new Builder<Long>();
        for (Range<Long> range : ranges) {
            builder.add(range);
        }
        final AckFrame frame = AckFrame.create(delay, builder.build());
        final ByteBuf b = frame.toByteBuf();

        return b;
    }

    private ByteBuf encodeAckFrameWithDelayAndRangesAndECNCounts(long delay, Iterable<Range<Long>> ranges, ECNCounts ecnCounts) {
        ImmutableRangeSet.Builder<Long> builder = new Builder<Long>();
        for (Range<Long> range : ranges) {
            builder.add(range);
        }
        final AckFrame frame = AckFrame.createWithECN(delay, builder.build(), ecnCounts);
        final ByteBuf b = frame.toByteBuf();

        return b;
    }

    @Test
    public void writeWithSingleZeroPacket() {
        final ByteBuf b = encodeAckFrameWithDelayAndRanges(0, Lists.newArrayList(Range.closed(0L, 0L)));

        // Type
        assertEquals(0x2, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Largest acknowledged
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Delay: TBD
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Range Count
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // First range
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());

        assertFalse(b.isReadable());
    }

    @Test
    public void writeWithSingleZeroPacketAndECNCounts() {
        final ByteBuf b =
                encodeAckFrameWithDelayAndRangesAndECNCounts(0,
                                                             Lists.newArrayList(Range.closed(0L, 0L)),
                                                             new ECNCounts(1, 2 ,3));

        // Type
        assertEquals(0x3, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Largest acknowledged
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Delay: TBD
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Range Count
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // First range
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());

        // ECN
        assertEquals(1, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        assertEquals(2, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        assertEquals(3, (long)QUICByteBufs.readVariableLengthNumber(b).get());

        assertFalse(b.isReadable());
    }

    @Test
    public void writeWithSingleNonZeroPacket() {
        final ByteBuf b = encodeAckFrameWithDelayAndRanges(63, Lists.newArrayList(Range.closed(2L, 2L)));
        // Type
        assertEquals(0x2, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Largest acknowledged
        assertEquals(2, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Delay
        assertEquals(63, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Range Count
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // First range
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
    }

    @Test
    public void writeWithMultipleRanges() {
        final ByteBuf b =
                encodeAckFrameWithDelayAndRanges(63,
                                                 Lists.newArrayList(Range.closed(2L, 2L),
                                                                    Range.closed(0L, 0L)));
        // Type
        assertEquals(0x2, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Largest acknowledged
        assertEquals(2, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Delay
        assertEquals(63, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Ack Range Count
        assertEquals(1, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // First range
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Gap
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());
        // Len
        assertEquals(0, (long)QUICByteBufs.readVariableLengthNumber(b).get());

        assertFalse(b.isReadable());
    }

    @Test
    public void readWithSingleZeroPacket() {
        final AckFrame frame = AckFrame.readFrom(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 0}), false).get();
        Set<Range<Long>> ranges = frame.ranges.asDescendingSetOfRanges();
        assertEquals(Sets.newHashSet(Range.closed(0L, 0L)), ranges);
        assertTrue(frame.ecnCounts.isEmpty());
    }

    @Test
    public void readWithSingleZeroPacketAndECNCounts() {
        final AckFrame frame = AckFrame.readFrom(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 0, 1, 2, 3}), true).get();
        Set<Range<Long>> ranges = frame.ranges.asDescendingSetOfRanges();
        assertEquals(Sets.newHashSet(Range.closed(0L, 0L)), ranges);
        assertEquals(new ECNCounts(1, 2, 3), frame.ecnCounts.get());
    }

    @Test
    public void readWithBlockLengthUnderflow() {
        final Optional<AckFrame> result =
                AckFrame.readFrom(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 1}), false);
        assertTrue(result.isEmpty());
    }

    @Test
    public void readWithGapGoingDownToPn0() {
        final AckFrame frame = AckFrame.readFrom(Unpooled.wrappedBuffer(new byte[] { 2, 0, 1, 0, 0, 0}), false).get();

        assertEquals(Sets.newHashSet(Range.closed(2L, 2L), Range.closed(0L, 0L)),
                     frame.ranges.asDescendingSetOfRanges());
    }

    @Test
    public void readWithBlockLenGoingNegative() {
        final Optional<AckFrame> result =
                AckFrame.readFrom(Unpooled.wrappedBuffer(new byte[] { 2, 0, 1, 0, 0, 1}), false);
        assertTrue(result.isEmpty());
    }
}