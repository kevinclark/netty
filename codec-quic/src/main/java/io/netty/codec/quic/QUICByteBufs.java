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

public class QUICByteBufs {
    static ByteBuf encodeVariableLengthNumber(long value) {
        final ByteBuf result = Unpooled.buffer(8);
        writeVariableLengthNumber(result, value);
        return result;
    }

    static void writeVariableLengthNumber(final ByteBuf buf, long value) {
        // Only 62 bits allowed max - two are for encoding length
        assert value < 1L << 62;

        if (value >= 1 << 30) { // If it's in the top 34 bits, we need 8 bytes
            buf.writeLong(0xc000000000000000L | 0x3fffffffffffffffL & value);
        } else if (value >= 1 << 14) { // If it's in the top 32, we need
            byte[] bytes = { (byte)0x80, 0, 0, 0 };
            buf.writeInt(0x80000000 | 0x3fffffff & (int)value);
        } else if (value >= 1 << 6) {
            byte[] bytes = { (byte)0x40, 0 };
            buf.writeShort(0x4000 | 0x3fff & (short)value);
        } else {
            buf.writeByte((byte)value);
        }
    }

    static long readVariableLengthNumber(final ByteBuf buf) {
        ByteBuf conversionBuf = Unpooled.buffer(8);
        byte firstByte = buf.readByte();

        // Push everything but the first two bits - that's data
        conversionBuf.writeByte(firstByte & 0x3f);

        switch (firstByte & 0xc0) {
        case 0xc0: // Eight bytes
            conversionBuf.writeBytes(buf.readBytes(7));
            return conversionBuf.readLong();

        case 0x80: // Four bytes
            conversionBuf.writeBytes(buf.readBytes(3));
            return conversionBuf.readInt();

        case 0x40: // Two bytes
            conversionBuf.writeByte(buf.readByte());
            return conversionBuf.readShort();

        case 0x00:
            return conversionBuf.readByte();

        default:
            assert false;
        }

        return -1;
    }
}
