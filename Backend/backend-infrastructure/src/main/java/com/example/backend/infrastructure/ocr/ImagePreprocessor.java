package com.example.backend.infrastructure.ocr;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bytedeco.opencv.global.opencv_core.CV_8U;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_COLOR;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public final class ImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ImagePreprocessor.class);
    private static final int ADAPTIVE_BLOCK_SIZE = 15;
    private static final double ADAPTIVE_C = 7.0;
    private static final String OUTPUT_FORMAT = ".png";

    private ImagePreprocessor() {}

    public static byte[] preprocess(byte[] imageBytes) {
        Mat src = null, gray = null, blurred = null, binary = null;
        BytePointer bufPtr = null;
        try {
            src = imdecode(new Mat(1, imageBytes.length, CV_8U, new BytePointer(imageBytes)), IMREAD_COLOR);
            if (src.empty()) {
                return imageBytes;
            }

            gray = new Mat();
            cvtColor(src, gray, COLOR_BGR2GRAY);

            blurred = new Mat();
            GaussianBlur(gray, blurred, new Size(3, 3), 0);

            binary = new Mat();
            adaptiveThreshold(blurred, binary, 255,
                    ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY,
                    ADAPTIVE_BLOCK_SIZE, ADAPTIVE_C);

            bufPtr = new BytePointer();
            imencode(OUTPUT_FORMAT, binary, bufPtr);
            byte[] result = new byte[(int) bufPtr.limit()];
            bufPtr.get(result);
            return result;
        } catch (Throwable t) {
            log.warn("Image preprocessing failed, falling back to original bytes: {}", t.getMessage());
            return imageBytes;
        } finally {
            release(src);
            release(gray);
            release(blurred);
            release(binary);
            if (bufPtr != null) {
                bufPtr.close();
            }
        }
    }

    private static void release(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }
}
