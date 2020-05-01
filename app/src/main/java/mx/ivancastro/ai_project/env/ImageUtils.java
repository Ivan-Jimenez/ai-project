package mx.ivancastro.ai_project.env;

import android.graphics.Bitmap;
import android.os.Environment;

import java.io.File;

public class ImageUtils {
    static final int kMaxChannelValue = 262143;

    private static final Logger LOGGER = new Logger();

    public static int getYUVByteSize(final int width, final int height) {
        final int ySize = width * height;
        final int uvSize = ((width + 1) / 2) * ((height + 1) / 2) * 2;
        return ySize + uvSize;
    }

    public static void saveBitmap(final Bitmap bitmap) { saveBitmap(bitmap, "preview.png"); }

    public static void saveBitmap(final Bitmap, final String filename) {
        final String root = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";

    }
}
