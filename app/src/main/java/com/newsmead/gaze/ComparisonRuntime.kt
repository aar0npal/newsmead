package com.newsmead.gaze

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.newsmead.BuildConfig
import java.io.File
import java.io.FileInputStream

data class ComparisonLaunchParse(
    val requested: Boolean,
    val spec: ComparisonLaunchSpec? = null,
    val error: String? = null,
)

data class ComparisonRuntimeIdentity(
    val spec: ComparisonLaunchSpec,
    val build: ComparisonBuildIdentity,
    val protocolManifestSha256: String,
    val collectionApkSha256: String,
    val deviceInstanceSha256: String,
    val deviceModel: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val rotation: Int,
) {
    init {
        require(protocolManifestSha256 == ComparisonProtocol.MANIFEST_SHA256)
        require(ComparisonProtocol.isSha256(collectionApkSha256))
        require(ComparisonProtocol.isSha256(deviceInstanceSha256))
        require(deviceModel == spec.slot.deviceModel)
        require(screenWidthPx > 0 && screenHeightPx > 0 && densityDpi > 0)
        require(rotation in 0..3)
    }
}

data class ComparisonRecordBinding(
    val runtime: ComparisonRuntimeIdentity,
    val calibrationSha256: String?,
) {
    val digest: String = ComparisonProtocol.sha256(
        buildString {
            append(runtime.spec.protocolId).append('\n')
            append(runtime.protocolManifestSha256).append('\n')
            append(runtime.spec.slot.id).append('\n')
            append(runtime.spec.orderVariant).append('\n')
            append(runtime.build.estimatorId).append('\n')
            append(runtime.build.baseCommit).append('\n')
            append(runtime.build.baseApkSha256).append('\n')
            append(runtime.collectionApkSha256).append('\n')
            append(calibrationSha256 ?: "unavailable").append('\n')
            append(runtime.deviceInstanceSha256).append('\n')
            append(runtime.deviceModel).append('\n')
            append(runtime.screenWidthPx).append('x').append(runtime.screenHeightPx).append('\n')
            append(runtime.densityDpi).append('\n')
            append(runtime.rotation).append('\n')
            append(ComparisonProtocol.COORDINATE_SPACE).append('\n')
            append(ComparisonProtocol.MONOTONIC_CLOCK)
        }.toByteArray(Charsets.UTF_8),
    )
}

data class ComparisonReadingBinding(
    val record: ComparisonRecordBinding,
    val readingLayoutSha256: String,
    val passageSha256: String,
) {
    val digest: String = ComparisonProtocol.sha256(
        "${record.digest}\n$readingLayoutSha256\n$passageSha256\n${ReadingValidationProtocol.VERSION}"
            .toByteArray(Charsets.UTF_8),
    )
}

object ComparisonRuntime {
    private val extraKeys = listOf(
        ComparisonProtocol.EXTRA_PROTOCOL_ID,
        ComparisonProtocol.EXTRA_SLOT_ID,
        ComparisonProtocol.EXTRA_ORDER_VARIANT,
    )

    fun buildIdentity(): ComparisonBuildIdentity = ComparisonBuildIdentity(
        estimatorId = BuildConfig.COMPARISON_ESTIMATOR_ID,
        baseCommit = BuildConfig.COMPARISON_BASE_COMMIT,
        baseApkSha256 = BuildConfig.COMPARISON_BASE_APK_SHA256,
    )

    fun parseLaunch(intent: Intent): ComparisonLaunchParse {
        if (extraKeys.none(intent::hasExtra)) return ComparisonLaunchParse(requested = false)
        return try {
            ComparisonLaunchParse(
                requested = true,
                spec = ComparisonProtocol.validateLaunch(
                    protocolId = intent.getStringExtra(ComparisonProtocol.EXTRA_PROTOCOL_ID),
                    slotId = intent.getStringExtra(ComparisonProtocol.EXTRA_SLOT_ID),
                    orderVariant = intent.getStringExtra(ComparisonProtocol.EXTRA_ORDER_VARIANT),
                    build = buildIdentity(),
                    deviceModel = Build.MODEL,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ComparisonLaunchParse(requested = true, error = e.message ?: "Invalid comparison launch.")
        }
    }

    fun putLaunch(intent: Intent, spec: ComparisonLaunchSpec): Intent = intent.apply {
        putExtra(ComparisonProtocol.EXTRA_PROTOCOL_ID, spec.protocolId)
        putExtra(ComparisonProtocol.EXTRA_SLOT_ID, spec.slot.id)
        putExtra(ComparisonProtocol.EXTRA_ORDER_VARIANT, spec.orderVariant)
    }

    /** Called after explicit Start while CAMERA is still unopened. */
    fun resolveIdentity(
        context: Context,
        spec: ComparisonLaunchSpec,
        screenWidthPx: Int,
        screenHeightPx: Int,
        densityDpi: Int,
        rotation: Int,
    ): ComparisonRuntimeIdentity {
        require(
            BuildConfig.COMPARISON_PROTOCOL_MANIFEST_SHA256 ==
                ComparisonProtocol.MANIFEST_SHA256,
        ) { "The comparison build manifest identity changed." }
        val manifestBytes = context.assets.open(ComparisonProtocol.MANIFEST_ASSET).use { it.readBytes() }
        require(ComparisonProtocol.sha256(manifestBytes) == ComparisonProtocol.MANIFEST_SHA256) {
            "The embedded comparison protocol manifest changed."
        }
        val build = buildIdentity()
        ComparisonProtocol.validateLaunch(
            spec.protocolId,
            spec.slot.id,
            spec.orderVariant,
            build,
            Build.MODEL,
        )
        val apk = File(context.applicationInfo.sourceDir)
        require(apk.isFile && apk.length() > 0L) { "The installed APK cannot be verified." }
        return ComparisonRuntimeIdentity(
            spec = spec,
            build = build,
            protocolManifestSha256 = ComparisonProtocol.MANIFEST_SHA256,
            collectionApkSha256 = FileInputStream(apk).use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            },
            deviceInstanceSha256 = ComparisonProtocol.sha256(
                (Build.FINGERPRINT + ":" + Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID,
                )).toByteArray(Charsets.UTF_8),
            ),
            deviceModel = Build.MODEL,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            densityDpi = densityDpi,
            rotation = rotation,
        )
    }

    fun nowNs(): Long = SystemClock.elapsedRealtimeNanos()
}
