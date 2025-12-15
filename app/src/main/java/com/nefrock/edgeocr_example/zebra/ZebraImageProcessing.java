package com.nefrock.edgeocr_example.zebra;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;

public final class ZebraImageProcessing {
    private ZebraImageProcessing() {}

    public static Bitmap toBitmap(byte[] data, String imageFormat, int orientation, int stride, int width, int height) {
        if (data == null || imageFormat == null) return null;
        if ("YUV".equalsIgnoreCase(imageFormat)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, new int[]{stride, stride});
            yuvImage.compressToJpeg(new Rect(0, 0, stride, height), 100, out);
            byte[] imageBytes = out.toByteArray();
            Bitmap base = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (orientation == 0) return base;
            Matrix m = new Matrix();
            m.postRotate(orientation);
            return Bitmap.createBitmap(base, 0, 0, base.getWidth(), base.getHeight(), m, true);
        } else if ("Y8".equalsIgnoreCase(imageFormat)) {
            return y8ToBitmap(data, orientation, stride, height);
        }
        return null;
    }

    private static Bitmap y8ToBitmap(byte[] data, int orientation, int stride, int height) {
        int len = data.length;
        int[] pixels = new int[len];
        for (int i = 0; i < len; i++) {
            int p = data[i] & 0xFF;
            pixels[i] = 0xFF000000 | (p << 16) | (p << 8) | p;
        }
        Bitmap base = Bitmap.createBitmap(pixels, stride, height, Bitmap.Config.ARGB_8888);
        if (orientation == 0) return base;
        Matrix m = new Matrix();
        m.postRotate(orientation);
        return Bitmap.createBitmap(base, 0, 0, base.getWidth(), base.getHeight(), m, true);
    }
}

