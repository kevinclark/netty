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
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.packet.ECNCounts;
import io.netty.codec.quic.util.QUICByteBufs;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/*
 Section 19.3
   ACK Frame {
     Type (i) = 0x02..0x03,
     Largest Acknowledged (i),
     ACK Delay (i),
     ACK Range Count (i),
     First ACK Range (i),
     ACK Range (..) ...,
     [ECN Counts (..)],
   }
 */
public class AckFrame extends Frame {
    final public long delay;
    final public RangeSet<Long> ranges;
    final public Optional<ECNCounts> ecnCounts;

    public static AckFrame createWithECN(final long delay, final RangeSet<Long> ranges, final ECNCounts ecnCounts) {
        return new AckFrame(delay, ranges, ecnCounts);
    }

    public static AckFrame create(final long delay, final RangeSet<Long> ranges) {
        return new AckFrame(delay, ranges, null);
    }

    private AckFrame(final long delay, final RangeSet<Long> ranges, final ECNCounts ecnCounts) {
        assert(! ranges.isEmpty());

        this.delay = delay;
        this.ranges = ranges;
        this.ecnCounts = Optional.ofNullable(ecnCounts);
    }

    /**
     * Parse an ACK frame from the provided byte buffer. Type byte is expected to be passed already.
     * @param buf - The buffer to read.
     * @return An Optional.of(AckFrame) or Optional.empty if there's a frame encoding error
     */
    public static Optional<AckFrame> readFrom(final ByteBuf buf, final boolean hasECNCounts) {
        try {
            final long largestAckd = QUICByteBufs.readVariableLengthNumber(buf).get();
            final long delay = QUICByteBufs.readVariableLengthNumber(buf).get();
            final long ackRangeCount = QUICByteBufs.readVariableLengthNumber(buf).get();

            if (ackRangeCount < 0) {
                return Optional.empty();
            }
            long smallest = largestAckd - QUICByteBufs.readVariableLengthNumber(buf).get();
            if (smallest < 0) {
                return Optional.empty();
            }

            ImmutableRangeSet.Builder<Long> ranges = ImmutableRangeSet.builder();
            ranges.add(Range.closed(smallest, largestAckd));

            for (int i = 0; i != ackRangeCount; ++i) {
                final long gap = QUICByteBufs.readVariableLengthNumber(buf).get();
                final long len = QUICByteBufs.readVariableLengthNumber(buf).get();

                final long upper = -gap + smallest - 2;
                final long lower = upper - len;

                if (lower < 0) {
                    return Optional.empty();
                }

                ranges.add(Range.closed(lower, upper));

                smallest = lower;
            }

            if (hasECNCounts) {
                final long ecn0Count = QUICByteBufs.readVariableLengthNumber(buf).get();
                final long ecn1Count = QUICByteBufs.readVariableLengthNumber(buf).get();
                final long ceCount = QUICByteBufs.readVariableLengthNumber(buf).get();

                return Optional.of(createWithECN(delay, ranges.build(), new ECNCounts(ecn0Count, ecn1Count, ceCount)));
            } else {
                return Optional.of(create(delay, ranges.build()));
            }

        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

//    public static AckFrame readFromWithECN(final ByteBuf buf) {
//
//    }

    public void writeTo(final ByteBuf buf) {
        final Set<Range<Long>> descendingRanges = this.ranges.asDescendingSetOfRanges();
        final Iterator<Range<Long>> rangeIter = descendingRanges.iterator();
        Range<Long> range = rangeIter.next();

        QUICByteBufs.writeVariableLengthNumber(buf, this.ecnCounts.isEmpty() ? 0x2 : 0x3); // Type (i) = 0x02..0x03
        QUICByteBufs.writeVariableLengthNumber(buf, range.upperEndpoint());                // Largest Acknowledged (i)
        QUICByteBufs.writeVariableLengthNumber(buf, delay);                                // ACK Delay (i)
        QUICByteBufs.writeVariableLengthNumber(buf, descendingRanges.size() - 1);    // ACK Range Count (i)

        /*
        First ACK Range: A variable-length integer indicating the number of contiguous packets preceding the
                         Largest Acknowledged that are being acknowledged. The First ACK Range is encoded as an
                         ACK Range; see Section 19.3.1 starting from the Largest Acknowledged. That is, the
                         smallest packet acknowledged in the range is determined by subtracting the First
                         ACK Range value from the Largest Acknowledged.
         */

        // largest - smallest = ack range
        QUICByteBufs.writeVariableLengthNumber(buf, range.upperEndpoint() - range.lowerEndpoint());

        long previousSmallest = range.lowerEndpoint();

        while (rangeIter.hasNext()) {
            range = rangeIter.next();

            // Gap: A variable-length integer indicating the number of contiguous unacknowledged packets preceding the
            //      packet number one lower than the smallest in the preceding ACK Range.
            QUICByteBufs.writeVariableLengthNumber(buf, previousSmallest - range.upperEndpoint() - 2);
            // ACK Range Length: A variable-length integer indicating the number of contiguous acknowledged packets
            //                   preceding the largest packet number, as determined by the preceding Gap.
            QUICByteBufs.writeVariableLengthNumber(buf, range.upperEndpoint() - range.lowerEndpoint());

            previousSmallest = range.lowerEndpoint();
        }

        // My kingdom for a lambda
        this.ecnCounts.ifPresent(new Consumer<ECNCounts>() {
            @Override
            public void accept(ECNCounts ecnCounts) {
                QUICByteBufs.writeVariableLengthNumber(buf, ecnCounts.ect0Count);
                QUICByteBufs.writeVariableLengthNumber(buf, ecnCounts.ect1Count);
                QUICByteBufs.writeVariableLengthNumber(buf, ecnCounts.ceCount);
            }
        });
    }

    public ByteBuf toByteBuf() {
        final ByteBuf buf = Unpooled.buffer();

        writeTo(buf);

        return buf;
    }
}
