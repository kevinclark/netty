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
import io.netty.buffer.ByteBuf;
import io.netty.codec.quic.util.QUICByteBufs;
import org.junit.Test;

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

    @Test
    public void toByteBufWithSingleZeroPacket() {
        final ByteBuf b = encodeAckFrameWithDelayAndRanges(0, Lists.newArrayList(Range.closed(0L, 0L)));

        // Type
        assertEquals(0x2, QUICByteBufs.readVariableLengthNumber(b));
        // Largest acknowledged
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Delay: TBD
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Range Count
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // First range
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));

        assertFalse(b.isReadable());
    }

    @Test
    public void toByteBufWithSingleNonZeroPacket() {
        final ByteBuf b = encodeAckFrameWithDelayAndRanges(63, Lists.newArrayList(Range.closed(2L, 2L)));
        // Type
        assertEquals(0x2, QUICByteBufs.readVariableLengthNumber(b));
        // Largest acknowledged
        assertEquals(2, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Delay
        assertEquals(63, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Range Count
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // First range
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
    }

    @Test
    public void toByteBufWithMultipleRanges() {
        final ByteBuf b =
                encodeAckFrameWithDelayAndRanges(63,
                                                 Lists.newArrayList(Range.closed(2L, 2L),
                                                                    Range.closed(0L, 0L)));
        // Type
        assertEquals(0x2, QUICByteBufs.readVariableLengthNumber(b));
        // Largest acknowledged
        assertEquals(2, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Delay
        assertEquals(63, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Range Count
        assertEquals(1, QUICByteBufs.readVariableLengthNumber(b));
        // First range
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // Gap
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // Len
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));

        assertFalse(b.isReadable());
    }
}