package com.newsmead.gaze.mgazenet

import java.io.*

/** One bounded, hash-bound payload. Never encodes training rows or legacy calibration. */
object CalibrationBundle {
    data class Artifact(val identity: CalibrationIdentity, val x: ByteArray, val y: ByteArray)
    private const val MAX_MODEL_BYTES = 8 * 1024 * 1024
    fun encode(artifact: Artifact): ByteArray {
        require(artifact.identity.acquisition != "unbound")
        require(artifact.x.size in 1..MAX_MODEL_BYTES && artifact.y.size in 1..MAX_MODEL_BYTES)
        val body = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeUTF(artifact.identity.canonical())
            listOf(artifact.x,artifact.y).forEach { out.writeInt(it.size); out.write(it) }
        } }.toByteArray()
        return ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use {
            it.writeUTF(CalibrationIdentity.VERSION); it.writeUTF(CalibrationIdentity.hash(body)); it.write(body)
        } }.toByteArray()
    }
    fun decode(bytes: ByteArray, compatible: (CalibrationIdentity) -> Boolean): Artifact {
        require(bytes.size <= MAX_MODEL_BYTES * 2 + 8192)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUTF() == CalibrationIdentity.VERSION) { "MGazeNet calibration required" }
            val hash = input.readUTF()
            val body = input.readBytes()
            require(CalibrationIdentity.hash(body) == hash) { "Calibration hash mismatch" }
            DataInputStream(ByteArrayInputStream(body)).use { payload ->
                val identity = CalibrationIdentity.parse(payload.readUTF())
                require(identity.acquisition != "unbound")
                require(compatible(identity)) { "Device or screen geometry changed" }
                fun model(): ByteArray {
                    val size = payload.readInt(); require(size in 1..MAX_MODEL_BYTES && size <= payload.available())
                    return ByteArray(size).also { payload.readFully(it) }
                }
                val artifact = Artifact(identity, model(), model())
                require(payload.available() == 0)
                artifact
            }
        }
    }
}
