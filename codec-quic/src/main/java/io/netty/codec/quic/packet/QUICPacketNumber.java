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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.util.ToByteBuf;

/**
 * TODO: Actually encode based on this, in concert with QUICKPacketNumberSpace.
 *
 * 17.1. Packet Number Encoding and Decoding
 *  Packet numbers are integers in the range 0 to 2^62-1 (Section 12.3).
 *  When present in long or short packet headers, they are encoded in 1
 *  to 4 bytes. The number of bits required to represent the packet
 *  number is reduced by including the least significant bits of the
 *  packet number.
 *  The encoded packet number is protected as described in Section 5.4 of
 *  [QUIC-TLS].
 *
 *  The sender MUST use a packet number size able to represent more than
 *  twice as large a range than the difference between the largest
 *  acknowledged packet and packet number being sent. A peer receiving
 *  the packet will then correctly decode the packet number, unless the
 *  packet is delayed in transit such that it arrives after many higher-
 *  numbered packets have been received. An endpoint SHOULD use a large
 *  enough packet number encoding to allow the packet number to be
 *  recovered even if the packet arrives after packets that are sent
 *  afterwards.
 *
 *  As a result, the size of the packet number encoding is at least one
 *  bit more than the base-2 logarithm of the number of contiguous
 *  unacknowledged packet numbers, including the new packet.
 *  For example, if an endpoint has received an acknowledgment for packet
 *  0xabe8bc, sending a packet with a number of 0xac5c02 requires a
 *  packet number encoding with 16 bits or more; whereas the 24-bit
 *  packet number encoding is needed to send a packet with a number of
 *  0xace8fe.
 *
 *  At a receiver, protection of the packet number is removed prior to
 *  recovering the full packet number. The full packet number is then
 *  reconstructed based on the number of significant bits present, the
 *  value of those bits, and the largest packet number received on a
 *  successfully authenticated packet. Recovering the full packet number
 *  is necessary to successfully remove packet protection.
 *
 *  Once header protection is removed, the packet number is decoded by
 *  finding the packet number value that is closest to the next expected
 *  packet. The next expected packet is the highest received packet
 *  number plus one. For example, if the highest successfully
 *  authenticated packet had a packet number of 0xa82f30ea, then a packet
 *  containing a 16-bit value of 0x9b32 will be decoded as 0xa82f9b32.
 *  Example pseudo-code for packet number decoding can be found in
 *  Appendix A.
 */
public final class QUICPacketNumber implements ToByteBuf {
    public final int number;
    public final byte encodedLength;

    public QUICPacketNumber(int number) {
        this.number = number;
        this.encodedLength = encodedLength(number);
    }

    public int bytesNeeded() {
        return this.encodedLength + 1;
    }

    @Override
    public ByteBuf toByteBuf() {
        switch (this.encodedLength) {
        case 0:
            return Unpooled.wrappedBuffer(new byte[] { (byte) this.number });

        case 1:
            return Unpooled.copyShort(this.number);

        case 2:
            return Unpooled.copyMedium(this.number);

        default:
            return Unpooled.copyInt(this.number);
        }
    }

    private static byte encodedLength(int number) {
        if ((number & 0xffffff00) == 0) {
            return 0;
        } else if ((number & 0xffff0000) == 0) {
            return 1;
        } else if ((number & 0xff000000) == 0) {
            return 2;
        } else {
            return 3;
        }
    }
}
