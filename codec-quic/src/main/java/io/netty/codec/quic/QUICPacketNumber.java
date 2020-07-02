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
