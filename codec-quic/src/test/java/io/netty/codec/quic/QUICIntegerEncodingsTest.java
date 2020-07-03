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
import org.junit.Test;

import static org.junit.Assert.*;

public class QUICIntegerEncodingsTest {

    @Test
    public void encodeVariableLength8Bytes() {
        final ByteBuf encoded = QUICIntegerEncodings.encodeVariableLength(151288809941952652L);

        assertEquals(0xc2197c5eff14e88cL, encoded.readLong());
    }

    @Test
    public void variableLengthDecodeEightBytes() {
        final ByteBuf buf = Unpooled.copyLong(0xc2197c5eff14e88cL);

        assertEquals(151288809941952652L, QUICIntegerEncodings.decodeVariableLength(buf));
    }

    @Test
    public void encodeVariableLengthFourBytes() {
        assertEquals(0xbfffffff, QUICIntegerEncodings.encodeVariableLength(1073741823).readInt());
    }

    @Test
    public void variableLengthDecodeFourBytes() {
        assertEquals(1073741823, QUICIntegerEncodings.decodeVariableLength(Unpooled.copyInt(0xbfffffff)));
    }

    @Test
    public void encodeVariableLengthTwoBytes() {
        assertEquals(0x7fff, QUICIntegerEncodings.encodeVariableLength(16383).readShort());
    }

    @Test
    public void variableLengthDecodeWithTwoBytes() {
        assertEquals(37, QUICIntegerEncodings.decodeVariableLength(Unpooled.copyShort(0x4025)));

        assertEquals(16383,
                     QUICIntegerEncodings.decodeVariableLength(Unpooled.wrappedBuffer(new byte[]{ 0x7f, (byte)0xff })));
    }

    @Test
    public void encodeVariableLengthOneBytes() {
        assertEquals(0x25, QUICIntegerEncodings.encodeVariableLength(37).readByte());
    }

    @Test
    public void variableLengthDecodeWithOneByte() {
        final ByteBuf buf = Unpooled.wrappedBuffer(new byte[]{ 0x25 });
        final long val = QUICIntegerEncodings.decodeVariableLength(buf);

        assertEquals(37, val);
    }
}