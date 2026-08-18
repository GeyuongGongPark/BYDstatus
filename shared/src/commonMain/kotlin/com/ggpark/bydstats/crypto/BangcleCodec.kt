package com.ggpark.bydstats.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * BYD 통신에 사용되는 독자 CBC-AES 변형 코덱
 * BangcleCodec.swift를 Kotlin으로 직접 포팅
 */
class BangcleCodec(tableData: ByteArray) {

    private val invRound: ByteArray
    private val invXor: ByteArray
    private val invFirst: ByteArray
    private val round: ByteArray
    private val xor: ByteArray
    private val finalTable: ByteArray
    private val permDecrypt: ByteArray
    private val permEncrypt: ByteArray

    init {
        val bytes = tableData
        var offset = 0

        fun readU16(): Int {
            val v = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            return v
        }
        fun readU32(): Int {
            val v = (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return v
        }

        val magic = bytes.slice(0..3).map { it.toInt() and 0xFF }
        require(magic == listOf(0x42, 0x47, 0x54, 0x42)) { "Bad magic" }
        offset = 4

        val version = readU16()
        require(version == 1) { "Unsupported version $version" }
        val count = readU16()
        require(count == 8) { "Wrong table count $count" }

        val offsets = IntArray(8)
        val lengths = IntArray(8)
        for (i in 0 until 8) {
            offsets[i] = readU32()
            lengths[i] = readU32()
        }

        fun extract(i: Int) = bytes.copyOfRange(offsets[i], offsets[i] + lengths[i])

        invRound    = extract(0)
        invXor      = extract(1)
        invFirst    = extract(2)
        round       = extract(3)
        xor         = extract(4)
        finalTable  = extract(5)
        permDecrypt = extract(6)
        permEncrypt = extract(7)
    }

    // MARK: - Block Operations

    private fun prepareMatrix(block: ByteArray): ByteArray {
        val state = ByteArray(32)
        for (col in 0 until 4) for (row in 0 until 4) {
            state[col * 8 + row] = block[col + row * 4]
        }
        return state
    }

    private fun extractBlock(state: ByteArray): ByteArray {
        val output = ByteArray(16)
        for (col in 0 until 4) for (row in 0 until 4) {
            output[col + row * 4] = state[col * 8 + row]
        }
        return output
    }

    private fun encryptBlock(block: ByteArray, roundEnd: Int): ByteArray {
        val state = prepareMatrix(block)
        val temp64 = ByteArray(64)

        val rounds = minOf(9, maxOf(0, roundEnd))
        for (rnd in 0 until rounds) {
            val lVar21 = rnd * 4
            var permPtr = 0
            for (i in 0 until 4) {
                val bVar4 = permEncrypt[permPtr].toInt() and 0xFF
                val lVar16 = i * 8
                val base = i * 16
                for (j in 0 until 4) {
                    val uVar8 = (bVar4 + j) and 3
                    val byteVal = state[lVar16 + uVar8].toInt() and 0xFF
                    val idx = (byteVal + (i + (lVar21 + uVar8) * 4) * 256) * 4
                    temp64[base + j * 4]     = round[idx]
                    temp64[base + j * 4 + 1] = round[idx + 1]
                    temp64[base + j * 4 + 2] = round[idx + 2]
                    temp64[base + j * 4 + 3] = round[idx + 3]
                }
                permPtr += 2
            }

            var iVar16 = 1
            for (lVar22 in 0 until 4) {
                var pbOffset = lVar22
                for (lVar10 in 0 until 4) {
                    val local10 = temp64[pbOffset].toInt() and 0xFF
                    var uVar7  = local10 and 0xF
                    var uVar26 = local10 and 0xF0
                    val f0 = temp64[pbOffset + 0x10].toInt() and 0xFF
                    val f1 = temp64[pbOffset + 0x20].toInt() and 0xFF
                    val f2 = temp64[pbOffset + 0x30].toInt() and 0xFF
                    val lVar2 = lVar10 * 0x18 + rnd * 0x60
                    var iVar25 = iVar16
                    val fs = intArrayOf(f0, f1, f2)
                    for (lVar17 in 0 until 3) {
                        val inner = fs[lVar17]
                        val uVar1 = (inner shl 4) and 0xFF
                        val uVar27 = uVar7 or uVar1
                        uVar26 = ((uVar26 shr 4) or ((inner shr 4) shl 4)) and 0xFF
                        uVar7  = (xor[(lVar2 + (iVar25 - 1)) * 0x100 + uVar27].toInt()) and 0xF
                        val newByte = (xor[(lVar2 + iVar25) * 0x100 + uVar26].toInt()) and 0xFF
                        uVar26 = (newByte and 0xF) shl 4
                        iVar25 += 2
                    }
                    state[lVar10 + lVar22 * 8] = ((uVar26 or uVar7) and 0xFF).toByte()
                    pbOffset += 4
                }
                iVar16 += 6
            }
        }

        if (roundEnd == 10) {
            val tmp32 = state.copyOf(32)
            for (row in 0 until 4) {
                state[row]        = finalTable[(tmp32[(0 + row) and 3].toInt() and 0xFF) + ((0 + row) and 3) * 0x400]
                state[8  + row]   = finalTable[(tmp32[8  + ((1 + row) and 3)].toInt() and 0xFF) + ((1 + row) and 3) * 0x400 + 0x100]
                state[0x10 + row] = finalTable[(tmp32[0x10 + ((2 + row) and 3)].toInt() and 0xFF) + ((2 + row) and 3) * 0x400 + 0x200]
                state[0x18 + row] = finalTable[(tmp32[0x18 + ((3 + row) and 3)].toInt() and 0xFF) + ((3 + row) and 3) * 0x400 + 0x300]
            }
        }
        return extractBlock(state)
    }

    private fun decryptBlock(block: ByteArray, roundStart: Int): ByteArray {
        val state = prepareMatrix(block)
        val temp64 = ByteArray(64)

        val stopBound = maxOf(0, roundStart)
        for (rnd in 9 downTo stopBound) {
            val lVar21 = rnd * 4
            var permPtr = 0
            for (i in 0 until 4) {
                val bVar3 = permDecrypt[permPtr].toInt() and 0xFF
                val lVar16 = i * 8
                val base = i * 16
                for (j in 0 until 4) {
                    val uVar7 = (bVar3 + j) and 3
                    val byteVal = state[lVar16 + uVar7].toInt() and 0xFF
                    val idx = (byteVal + (i + (lVar21 + uVar7) * 4) * 256) * 4
                    temp64[base + j * 4]     = invRound[idx]
                    temp64[base + j * 4 + 1] = invRound[idx + 1]
                    temp64[base + j * 4 + 2] = invRound[idx + 2]
                    temp64[base + j * 4 + 3] = invRound[idx + 3]
                }
                permPtr += 2
            }

            var iVar15 = 1
            for (lVar21x in 0 until 4) {
                var pbOffset = lVar21x
                for (lVar9 in 0 until 4) {
                    val local10 = temp64[pbOffset].toInt() and 0xFF
                    var uVar6  = local10 and 0xF
                    var uVar26 = local10 and 0xF0
                    val f0 = temp64[pbOffset + 0x10].toInt() and 0xFF
                    val f1 = temp64[pbOffset + 0x20].toInt() and 0xFF
                    val f2 = temp64[pbOffset + 0x30].toInt() and 0xFF
                    val lVar2 = lVar9 * 0x18 + rnd * 0x60
                    var iVar25 = iVar15
                    val fs = intArrayOf(f0, f1, f2)
                    for (lVar16 in 0 until 3) {
                        val inner = fs[lVar16]
                        val uVar1 = (inner shl 4) and 0xFF
                        val uVar27 = uVar6 or uVar1
                        uVar26 = ((uVar26 shr 4) or ((inner shr 4) shl 4)) and 0xFF
                        uVar6  = (invXor[(lVar2 + (iVar25 - 1)) * 0x100 + uVar27].toInt()) and 0xF
                        val newByte = (invXor[(lVar2 + iVar25) * 0x100 + uVar26].toInt()) and 0xFF
                        uVar26 = (newByte and 0xF) shl 4
                        iVar25 += 2
                    }
                    state[lVar9 + lVar21x * 8] = ((uVar26 or uVar6) and 0xFF).toByte()
                    pbOffset += 4
                }
                iVar15 += 6
            }
        }

        if (roundStart == 1) {
            val tmp32 = state.copyOf(32)
            var u8 = 1; var u10 = 3; var u12 = 2
            for (row in 0 until 4) {
                state[row]        = invFirst[(tmp32[row].toInt() and 0xFF) + row * 0x400]
                state[8  + row]   = invFirst[(tmp32[8  + (u10 and 3)].toInt() and 0xFF) + (u10 and 3) * 0x400 + 0x100]
                state[0x10 + row] = invFirst[(tmp32[0x10 + (u12 and 3)].toInt() and 0xFF) + (u12 and 3) * 0x400 + 0x200]
                state[0x18 + row] = invFirst[(tmp32[0x18 + (u8 and 3)].toInt() and 0xFF) + (u8 and 3) * 0x400 + 0x300]
                u8++; u10++; u12++
            }
        }
        return extractBlock(state)
    }

    // MARK: - CBC Mode

    private fun encryptCBC(data: ByteArray, iv: ByteArray = ByteArray(16)): ByteArray {
        require(data.size % 16 == 0)
        val result = ByteArray(data.size)
        var prev = iv.copyOf()
        var offset = 0
        while (offset < data.size) {
            val block = data.copyOfRange(offset, offset + 16)
            for (i in 0 until 16) block[i] = (block[i].toInt() xor prev[i].toInt()).toByte()
            val enc = encryptBlock(block, 10)
            enc.copyInto(result, offset)
            prev = enc
            offset += 16
        }
        return result
    }

    private fun decryptCBC(data: ByteArray, iv: ByteArray = ByteArray(16)): ByteArray {
        require(data.size % 16 == 0)
        val result = ByteArray(data.size)
        var prev = iv.copyOf()
        var offset = 0
        while (offset < data.size) {
            val block = data.copyOfRange(offset, offset + 16)
            val dec = decryptBlock(block, 1)
            for (i in 0 until 16) dec[i] = (dec[i].toInt() xor prev[i].toInt()).toByte()
            dec.copyInto(result, offset)
            prev = block
            offset += 16
        }
        return result
    }

    // MARK: - PKCS7

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        return data + ByteArray(pad) { pad.toByte() }
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        val last = data.last().toInt() and 0xFF
        require(last in 1..16 && last <= data.size)
        val padStart = data.size - last
        require(data.slice(padStart until data.size).all { (it.toInt() and 0xFF) == last })
        return data.copyOfRange(0, padStart)
    }

    // MARK: - Envelope API

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeEnvelope(plaintext: String): String {
        val padded = pkcs7Pad(plaintext.encodeToByteArray())
        val cipher = encryptCBC(padded)
        return "F" + Base64.encode(cipher)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeEnvelope(envelope: String): String {
        var s = envelope
            .replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "")
            .replace("-", "+").replace("_", "/")
        require(s.startsWith("F")) { "Invalid envelope" }
        s = s.drop(1)
        val rem = s.length % 4
        if (rem != 0) s += "=".repeat(4 - rem)
        val cipherData = Base64.decode(s)
        val decrypted = decryptCBC(cipherData)
        val unpadded = pkcs7Unpad(decrypted)
        return unpadded.decodeToString()
    }
}
