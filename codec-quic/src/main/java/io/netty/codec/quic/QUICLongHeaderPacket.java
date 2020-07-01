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

package io.netty.codec.quic;/*
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

import io.netty.buffer.ByteBuf;

public final class QUICLongHeaderPacket {
    // 17.2 Long Header Packets - Draft 29
    final byte header;  // Header Form (1) = 1,
                        // Fixed Bit (1) = 1,
                        // Long Packet Type (2),
                        // Type-Specific Bits (4),
    final int version;  // Version (32),
    final byte destConnIdLength; // Destination Connection ID Length (8),
    final ByteBuf destConnId;    // Destination Connection ID (0..160),
    final byte sourceConnIdLength; // Source Connection ID Length (8),
    final ByteBuf sourceConnId; // Source Connection ID (0..160),

    public enum PacketType {
        Initial((byte) 0x0),
        ZeroRTT((byte) 0x1),
        Handshake((byte) 0x2),
        Retry((byte) 0x3);

        final byte value;

        PacketType(byte value) {
            this.value = value;
        }
    }

    QUICLongHeaderPacket(final PacketType packetType, byte typeSpecificBits,
                         int version,
                         byte destConnIdLength, final ByteBuf destConnId,
                         byte sourceConnIdLength, final ByteBuf sourceConnId) {

        this.header = (byte) ((0x80 /* Header Form */ |
                               0x40 /* Fixed Bit */ |
                               packetType.value /* Long Packet Type */) << 4 |
                             0x0F & typeSpecificBits) /* Type Specific Bits */;

        this.version = version;

        // Value MUST NOT exceed 20 in QUIC 1 but servers SHOULD be able to read longer connection IDs
        // from other QUIC versions  in order to properly form a version negotiation packet
        // Endpoints that receive a version 1 long header with a value larger than 20 MUST drop the packet.
        // TODO: Where to handle version validation? Probably external to the POJO.
        // TODO: Validate that the connIdLength is == readable bytes? Or handle that on write?
        this.destConnIdLength = destConnIdLength;
        this.destConnId = destConnId.retainedDuplicate();
        this.sourceConnIdLength = sourceConnIdLength;
        this.sourceConnId = sourceConnId.retainedDuplicate();
    }
}
