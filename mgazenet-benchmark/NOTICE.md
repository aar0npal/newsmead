# Research benchmark attribution

The public MGazeNet `base.mnn` and adapted GazeFollower preprocessing/crop
algorithms come from Gancheng Zhu's GazeFollower, pinned to
`553920edcb7998c029828677f50f6d8eb4a16249` (v1.0.2).
Upstream declares Creative Commons Attribution–NonCommercial–ShareAlike 4.0.
The adaptations in `GazeGeometry.kt`, `Preprocessor.kt`, and the corresponding
reference preprocessing in `tools/gaze/mgazenet/reference.py` retain that license.
`AccuracySession.kt` also adapts the source calibration target indices, grid
fractions and timing under the same license. Changes include physical-screen
labels for a measured viewport, draw-acknowledged target timing, finite deadlines,
independent held-out windows and in-memory-only personal calibration.
Changes include Kotlin/Android buffers, NCHW packing for the Session API,
explicit invalid-input handling, and synthetic-only reference checks.

Source: https://github.com/GanchengZhu/GazeFollower/tree/553920edcb7998c029828677f50f6d8eb4a16249
License: https://creativecommons.org/licenses/by-nc-sa/4.0/

MNN 3.6.1 is from Alibaba's MNN project, commit
`d407447ed56c4121a11ccbd266dc184ca1ead0c2`, under Apache 2.0.
`MNNNetNative.java` retains the upstream JNI names and signatures, with only
the CPU methods needed for this benchmark exposed.
Source: https://github.com/alibaba/MNN/tree/d407447ed56c4121a11ccbd266dc184ca1ead0c2

The explicit vendor fetch places the full GazeFollower and MNN license texts
in APK assets under `notices/`. The benchmark also uses OpenCV, AndroidX,
MediaPipe, and the C++ runtime supplied with MNN; their respective upstream
terms continue to apply. This isolated research build grants no additional
rights to the pretrained models and is not a commercial distribution approval.
