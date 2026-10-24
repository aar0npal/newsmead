"""Host OpenCV serialization contract, synthetic only; not Android execution evidence.

Run with mgazenet-benchmark/build/reference-env/Scripts/python.exe.
The already pinned reference environment supplies OpenCV 4.11.0 and NumPy.
"""
import tempfile
import unittest
from pathlib import Path


class SvrPersistenceTest(unittest.TestCase):
    def test_two_regressors_survive_save_load_and_new_process(self):
        import cv2
        import numpy as np
        import subprocess
        import sys
        self.assertEqual(cv2.__version__, "4.11.0")
        features = np.array([[((i // 45) * (j % 7 + 1) + (i % 45) * .001) / 16.0
                              for j in range(258)] for i in range(720)], dtype=np.float32)
        labels = np.array([[.1 + (i // 45 % 4) * .8 / 3, .1 + (i // 180) * .8 / 3]
                           for i in range(720)], dtype=np.float32)
        queries = np.array([[i * .031 + j % 7 * .1 for j in range(258)]
                            for i in range(20)], dtype=np.float32)
        with tempfile.TemporaryDirectory(prefix="newsmead-synthetic-svr-") as folder:
            expected = []
            for axis in range(2):
                model = cv2.ml.SVM_create()
                model.setType(cv2.ml.SVM_EPS_SVR)
                model.setKernel(cv2.ml.SVM_RBF)
                model.setC(1.0); model.setGamma(.005); model.setP(.001)
                model.setTermCriteria((cv2.TERM_CRITERIA_MAX_ITER, 10000, 1e-4))
                self.assertTrue(model.train(features, cv2.ml.ROW_SAMPLE, labels[:, axis]))
                path = str(Path(folder) / f"{axis}.xml")
                model.save(path)
                restored = cv2.ml.SVM_load(path)
                before = model.predict(queries)[1]
                np.testing.assert_array_equal(before, restored.predict(queries)[1])
                self.assertTrue(np.isfinite(before).all())
                self.assertEqual(restored.getTermCriteria()[0:2], (cv2.TERM_CRITERIA_MAX_ITER, 10000))
                expected.append(before.ravel().tolist())
            # Recreate queries instead of persisting any feature arrays.
            code = '''import cv2,numpy as np,json,sys
q=np.array([[i*.031+j%7*.1 for j in range(258)] for i in range(20)],dtype=np.float32)
print(json.dumps([cv2.ml.SVM_load(sys.argv[1]+"/"+str(a)+".xml").predict(q)[1].ravel().tolist() for a in range(2)]))'''
            import json
            actual = json.loads(subprocess.check_output([sys.executable, "-c", code, folder], text=True))
            self.assertEqual(expected, actual)
        features.fill(0); labels.fill(0); queries.fill(0)


if __name__ == "__main__":
    unittest.main()
