package com.taobao.android.mnn;

/** Minimal CPU-only declarations matching MNN 3.6.1 source/jni/mnnnetnative.cpp.
 * Upstream ABI: Apache-2.0, Alibaba MNN; see bundled notices. Keep this package.
 */
public final class MNNNetNative {
    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("MNN");
        System.loadLibrary("mnncore");
    }
    public static native long nativeCreateNetFromBuffer(byte[] data);
    public static native long nativeReleaseNet(long net);
    public static native long nativeCreateSession(long net, int backend, int threads, String[] saved, String[] outputs);
    public static native int nativeRunSession(long net, long session);
    public static native long nativeGetSessionInput(long net, long session, String name);
    public static native long nativeGetSessionOutput(long net, long session, String name);
    public static native int[] nativeTensorGetDimensions(long tensor);
    public static native void nativeSetInputFloatData(long net, long tensor, float[] data);
    public static native int nativeTensorGetData(long tensor, float[] data);
    private MNNNetNative() {}
}
