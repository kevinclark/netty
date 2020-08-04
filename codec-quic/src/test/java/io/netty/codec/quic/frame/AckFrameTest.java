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
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import io.netty.buffer.ByteBuf;
import io.netty.codec.quic.util.QUICByteBufs;
import org.junit.Test;

import static org.junit.Assert.*;

public class AckFrameTest {

    @Test
    public void toByteBufWithSingleZeroPacket() {
        RangeSet<Long> ranges = new ImmutableRangeSet.Builder<Long>()
                                        .add(Range.closed(0L, 0L))
                                        .build();

        final AckFrame frame = AckFrame.create(ranges);
        final ByteBuf b = frame.toByteBuf();

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
        RangeSet<Long> ranges = new ImmutableRangeSet.Builder<Long>()
                .add(Range.closed(2L, 2L))
                .build();

        final AckFrame frame = AckFrame.create(ranges);
        final ByteBuf b = frame.toByteBuf();

        // Type
        assertEquals(0x2, QUICByteBufs.readVariableLengthNumber(b));
        // Largest acknowledged
        assertEquals(2, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Delay: TBD
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Range Count
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
        // First range
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));

        assertFalse(b.isReadable());
    }

    @Test
    public void toByteBufWithMultipleRanges() {
        RangeSet<Long> ranges = new ImmutableRangeSet.Builder<Long>()
                .add(Range.closed(2L, 2L))
                .add(Range.closed(0L, 0L))
                .build();

        final AckFrame frame = AckFrame.create(ranges);
        final ByteBuf b = frame.toByteBuf();

        // Type
        assertEquals(0x2, QUICByteBufs.readVariableLengthNumber(b));
        // Largest acknowledged
        assertEquals(2, QUICByteBufs.readVariableLengthNumber(b));
        // Ack Delay: TBD
        assertEquals(0, QUICByteBufs.readVariableLengthNumber(b));
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