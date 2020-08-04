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

package io.netty.codec.quic.packet;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import java.util.Optional;

public class PacketNumberSpace {
    private long nextPacketNumber = 0;
    private RangeSet<Long> ackRanges;

    public static class ECNCounts {
        /*
         ECT0 Count: A variable-length integer representing the total number
         of packets received with the ECT(0) codepoint in the packet number
         space of the ACK frame.
         */
        public long ect0Count = 0;

        /*
         ECT1 Count: A variable-length integer representing the total number
         of packets received with the ECT(1) codepoint in the packet number
         space of the ACK frame.
         */
        public long ect1Count = 0;

        /*
         CE Count: A variable-length integer representing the total number of
         packets received with the CE codepoint in the packet number space
         of the ACK frame.
         */
        public long ceCount = 0;
    }
    private ECNCounts ecnCounts;

    public PacketNumberSpace() {
        this.ackRanges = TreeRangeSet.create();
    }

    public Optional<Long> getLargestPacketNumberAcknowledged() {
        if (this.ackRanges.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(this.ackRanges.span().upperEndpoint());
        }
    }

    public void ack(Range<Long> ackRange) {
        this.ackRanges.add(ackRange);
    }

    public RangeSet<Long> getAckRanges() {
        return this.ackRanges;
    }
}
