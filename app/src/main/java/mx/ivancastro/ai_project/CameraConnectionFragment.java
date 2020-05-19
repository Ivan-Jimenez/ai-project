package mx.ivancastro.ai_project;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.browse.MediaBrowser.ConnectionCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import mx.ivancastro.ai_project.customview.AutoFitTextureView;
import mx.ivancastro.ai_project.env.Logger;

public class CameraConnectionFragment extends Fragment {
    private static final Logger LOGGER = new Logger();

    private static final int MINIMUM_PREVIEW_SIZE = 320;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private final OnImageAvailableListener imageListener;
    private final Size inputSize;
    private final int layout;

    private final ConnectionCallback cameraConnectionCallback;
    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull CaptureResult partialResult) { }

                @Override
                public void onCaptureCompleted(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result) { }
            };

    private String cameraId;
    private AutoFitTextureView textureView;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private Integer sensorOrientation;
    private Size previewSize;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            };

    private void openCamera(final int width, final int height) {

    }

    private void setUpCameraOutputs() {
        final Activity activity = getActivity();
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            final StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            previewSize = chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture.class),
                    inputSize.getWidth(),
                    inputSize.getHeight());

            final int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }
        } catch (final CameraAccessException e) {
            LOGGER.e(e, "Exception!");
        } catch (final NullPointerException e) {
            ErrorDialog.newInstance("This device doesn't support Camera2 API.");
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            throw new IllegalStateException("This device doesn't support Camera2 API.");
        }

        cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
    }

    private void ConfigureTransform(final int viewWidth, final int viewHeight) {
        final Activity activity = getActivity();
        if (null == textureView || null == previewSize || null == activity) {
            return;
        }
        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270)
    }

    public interface ConnectionCallback {
        void onPreviewSizeChosen(Size size, int cameraRotation);
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(final Size lhs, final Size rhs) {
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight()
            );
        }
    }

    public static class ErrorDialog extends DialogFragment {
        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(final String message) {
            final ErrorDialog dialog = new ErrorDialog();
            final Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(
                            android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog, final int which) {
                                    activity.finish();
                                }
                            }
                    )
                    .create();
        }
    }
}
