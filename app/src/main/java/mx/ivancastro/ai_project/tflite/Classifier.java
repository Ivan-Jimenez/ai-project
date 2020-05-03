package mx.ivancastro.ai_project.tflite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.Trace;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import mx.ivancastro.ai_project.env.Logger;

public abstract class Classifier {
    private static final Logger LOGGER = new Logger();

    public enum Device {
        CPU,
        GPU
    }

    private static final int MAX_RESULTS = 2;

    private MappedByteBuffer tfliteModel;

    private final int imageSizeX;

    private final int imageSizeY;

    private GpuDelegate gpuDelegate = null;

    protected Interpreter tflite;

    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    private List<String> labels;

    private TensorImage inputImageBuffer;

    private final TensorBuffer outputProbabilityBuffer;

    private final TensorProcessor probabilityProcessor;

    public static Classifier create(Activity activity, Device device, int numThreads) throws IOException {
        return new ClassifierFloatMobileNet(activity, device, numThreads);
    }

    public static class Recognition {
        private final String id;
        private final String title;
        private final Float confidence;

        private RectF location;

        public Recognition(final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getTitle() { return title; }

        public Float getConfidence() { return confidence; }

        @Override
        public String toString() {
            String resultString = "";

            if (id != null) {
                resultString += "[" + id + "]";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }

    protected Classifier(Activity activity, Device device, int numThreads) throws IOException {
        tfliteModel = FileUtil.loadMappedFile(activity, getModelPath());

        switch (device) {
            case GPU:
                gpuDelegate = new GpuDelegate();
                tfliteOptions.addDelegate(gpuDelegate);
                break;
            case CPU:
                break;
        }

        tfliteOptions.setNumThreads(numThreads);
        tflite = new Interpreter(tfliteModel, tfliteOptions);

        labels = FileUtil.loadLabels(activity, getLabelPath());

        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape();
        imageSizeX = imageShape[1];
        imageSizeY = imageShape[2];
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        int probabilityTensorIndex = 0;
        int[] probabilityShape = tflite.getOutputTensor(probabilityTensorIndex).shape();
        DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();

        inputImageBuffer = new TensorImage(imageDataType);

        outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);
        probabilityProcessor = new TensorProcessor.Builder().add(getPostprocessNormalizeOp()).build();

        LOGGER.d("Created a Tensorfloe Lite Image Classifier.");
    }

    public List<Recognition> recognizeImage(final Bitmap bitmap, int sensorOrientation) {
        Trace.beginSection("recognizeImage");

        Trace.beginSection("loadImage");
        long startTimeForLoadImage = SystemClock.uptimeMillis();
        inputImageBuffer = loadImage(bitmap, sensorOrientation);
        long endTimeForLoadImage = SystemClock.currentThreadTimeMillis();
        Trace.endSection();
        LOGGER.v("Time to load the image: " + (endTimeForLoadImage - startTimeForLoadImage));

        Trace.beginSection("runInference");
        long startTimeForReference = SystemClock.uptimeMillis();
        tflite.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());
        long endTimeForReference = SystemClock.uptimeMillis();
        Trace.endSection();
        LOGGER.v("Time cost to run model inference: " + (endTimeForReference - startTimeForReference));

        Map<String, Float> labeledProbability = new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer)).getMapWithFloatValue();
        Trace.endSection();

        return getTopKProbability(labeledProbability);
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }

        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }

        tfliteModel = null;
    }

    public int getImageSizeX() { return imageSizeX; }

    public int getImageSizeY() { return imageSizeY; }

    private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
        inputImageBuffer.load(bitmap);

        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int numRotation = sensorOrientation / 90;

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .add(new Rot90Op(numRotation))
                .add(getPreprocessNormalizeOp())
                .build();
        return imageProcessor.process(inputImageBuffer);
    }

    private static List<Recognition> getTopKProbability(Map<String, Float> labelProb) {
        PriorityQueue<Recognition> priorityQueue = new PriorityQueue<>(
                MAX_RESULTS,
                new Comparator<Recognition>() {
                    @Override
                    public int compare(Recognition lhs , Recognition rhs) {
                        return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                    }
                }
        );

        for (Map.Entry<String, Float> entry : labelProb.entrySet()) {
            priorityQueue.add(new Recognition("" + entry.getKey(), entry.getKey(), entry.getValue(), null));
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionSize = Math.min(priorityQueue.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionSize; ++i) {
            recognitions.add(priorityQueue.poll());
        }
        return recognitions;
    }

    protected abstract String getModelPath();

    protected abstract String getLabelPath();

    protected abstract TensorOperator getPreprocessNormalizeOp();

    protected abstract TensorOperator getPostprocessNormalizeOp();
}
