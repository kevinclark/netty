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

package io.netty.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.codec.quic.QUICLongHeaderPacket.ToByteBuf;

public class QUICInitialPacket extends QUICLongHeaderPacket<QUICInitialPacket.Payload> {
    public static class Payload implements QUICLongHeaderPacket.ToByteBuf {
        public final ByteBuf token;
        public final byte packetNumberLength; // One less than the length in bytes
        public final int packetNumber;
        public final ByteBuf packetPayload;

        public Payload(final ByteBuf token, int packetNumber, final ByteBuf packetPayload) {
            this.token = token;
            this.packetNumber = packetNumber;
            this.packetNumberLength = numberEncodingLength(packetNumber);
            this.packetPayload = packetPayload;
        }

        private byte numberEncodingLength(int packingNumber) {
            if ((packingNumber & 0xffffff00) == 0) {
                return 0;
            } else if ((packingNumber & 0xffff0000) == 0) {
                return 1;
            } else if ((packingNumber & 0xff000000) == 0) {
                return 2;
            } else {
                return 3;
            }
        }

        @Override
        public ByteBuf toByteBuf() {
            final ByteBuf pn;
            switch (this.packetNumberLength) {
            case 0:
                pn = Unpooled.wrappedBuffer(new byte[] { (byte) this.packetNumber });
                break;

            case 1:
                pn = Unpooled.copyShort(this.packetNumber);
                break;

            case 2:
                pn = Unpooled.copyMedium(this.packetNumber);
                break;

            default:
                pn = Unpooled.copyInt(this.packetNumber);
            }

            return Unpooled.wrappedBuffer(this.token, pn, this.packetPayload);
        }
    }

    public QUICInitialPacket(int packetNumber, final QUICVersion version,
                             final ByteBuf destConnId, final ByteBuf sourceConnId,
                             final ByteBuf token,
                             final Payload payload) {
        super(PacketType.Initial,
              /* Reserved Bits (2), Packet Number Length (2)*/
              payload.packetNumberLength, version,
              destConnId, sourceConnId, payload);
    }
}
