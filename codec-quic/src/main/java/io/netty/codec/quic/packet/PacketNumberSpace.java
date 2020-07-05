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

    public PacketNumberSpace() {
        this.ackRanges = TreeRangeSet.create();
    }

    public Optional<Long> getLargestPacketNumberReceived() {
        if (this.ackRanges.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(this.ackRanges.span().upperEndpoint());
        }
    }

    public void ack(Range<Long> ackRange) {
        this.ackRanges.add(ackRange);
    }
}
