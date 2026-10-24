package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Camera-free attribution experiment. No thread choice is promoted by this runner. */
object InferenceProfileRunner {
    // Forward/reverse conditions, with four-thread baseline bookends to expose time drift.
    fun run(context: Context, finiteGuardExperiment: Boolean = false,
            executionContext: String = "debug instrumentation; no foreground Activity"): File {
        val order = if (finiteGuardExperiment) listOf(4,4,2,2,2,2,4,4) else listOf(4,1,2,8,8,2,1,4)
        val validations = if (finiteGuardExperiment) listOf(false,true,false,true,true,false,true,false) else List(8) { false }
        val report = BenchmarkReport(context,"synthetic_profile")
        val blocks = JSONArray()
        report.root.put("runtime","MNN 3.6.1 CPU Session, default normal precision; per-block thread count")
            .put("mnn_threads",JSONObject.NULL).put("thread_order",JSONArray(order))
            .put("experiment",if (finiteGuardExperiment) "finite_guard_v1" else "thread_stage_v1")
            .put("range_validation_order",JSONArray(validations))
            .put("warmup_iterations_per_block",20).put("measured_iterations_per_block",60)
            .put("preprocessing_in_timed_region",false).put("camera_included",false)
            .put("calibration_and_filter_included",false)
            .put("execution_context",executionContext)
        fun environment(): JSONObject {
            val power = context.getSystemService(PowerManager::class.java)
            val allowed = runCatching { File("/proc/self/status").readLines().firstOrNull { it.startsWith("Cpus_allowed_list:") } }.getOrNull()
            val cgroup = runCatching { File("/proc/self/cgroup").readText() }.getOrNull()
            val threadPath="/proc/self/task/${Process.myTid()}"
            val threadAllowed=runCatching { File("$threadPath/status").readLines().firstOrNull { it.startsWith("Cpus_allowed_list:") } }.getOrNull()
            val threadCgroup=runCatching { File("$threadPath/cgroup").readText() }.getOrNull()
            return JSONObject().put("power_save",power.isPowerSaveMode).put("interactive",power.isInteractive)
                .put("thermal_status",if (Build.VERSION.SDK_INT>=29) power.currentThermalStatus else -1)
                .put("allowed_cpus",allowed ?: JSONObject.NULL).put("process_cgroup",cgroup ?: JSONObject.NULL)
                .put("calling_thread_allowed_cpus",threadAllowed ?: JSONObject.NULL)
                .put("calling_thread_cgroup",threadCgroup ?: JSONObject.NULL)
        }
        try {
            Preprocessor().use { prep ->
                order.forEachIndexed { index,threads ->
                    val rangeValidation=validations[index]
                    val block = JSONObject().put("index",index).put("threads",threads)
                        .put("range_validation",rangeValidation).put("before",environment())
                    MnnEstimator(context,threads).use { model ->
                        val fixtures = JSONArray()
                        SyntheticFixtures.images().forEach { f ->
                            val input = prep.prepare(f.frame,f.crops)
                            val ordinary = model.infer(input)
                            val profiled = model.profileInference(input,rangeValidation).features
                            check(ordinary.indices.all { kotlin.math.abs(ordinary[it]-profiled[it])<=1e-5f })
                            fixtures.put(JSONObject().put("name",f.name).put("output",BenchmarkReport.array(profiled)))
                        }
                        block.put("fixtures",fixtures)
                        val fixture = SyntheticFixtures.images().first()
                        val input = prep.prepare(fixture.frame,fixture.crops)
                        repeat(20) { model.profileInference(input,rangeValidation) }
                        val samples = JSONArray()
                        val wallStart = SystemClock.elapsedRealtimeNanos()
                        val cpuStart = Process.getElapsedCpuTime()
                        repeat(60) { iteration ->
                            val p = model.profileInference(input,rangeValidation)
                            samples.put(JSONObject().put("iteration",iteration).put("validation_ms",p.validationMs)
                                .put("input_copy_ms",p.inputCopyMs).put("native_run_ms",p.nativeRunMs)
                                .put("output_read_ms",p.outputReadMs).put("total_ms",p.totalMs))
                        }
                        block.put("measurement_wall_ms",(SystemClock.elapsedRealtimeNanos()-wallStart)/1e6)
                            .put("measurement_process_cpu_ms",Process.getElapsedCpuTime()-cpuStart)
                            .put("samples",samples)
                        listOf("validation_ms","input_copy_ms","native_run_ms","output_read_ms","total_ms").forEach { key ->
                            block.put(key,BenchmarkReport.stats((0 until samples.length()).map { samples.getJSONObject(it).getDouble(key) }))
                        }
                    }
                    block.put("after",environment()); blocks.put(block)
                }
            }
            report.root.put("outcome","completed_pending_external_parity")
        } catch (e: Throwable) {
            report.root.put("outcome","failed").put("error",e.toString())
        }
        report.root.put("blocks",blocks)
        return report.save()
    }
}
