package com.nefrock.edgeocr_example.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.nefrock.edgeocr.model.Detection;

import java.util.List;

public class BoxesOverlay extends View {
    private final static int textSize = 25;
    private final static int strokeWidth = 5;
    private final static int cornerRadius = 4;
    private RectF[] boundingBoxes = new RectF[]{};
    private String[] texts = new String[]{};
    private RectF[] textBoxes = new RectF[]{};
    final Paint boxPaint;
    final Paint emptyBoxPaint;
    final Paint textBoxPaint;
    final Paint textPaint;

    public BoxesOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint = new Paint();
        boxPaint.setColor(Color.BLACK);
        boxPaint.setStrokeWidth(strokeWidth);
        boxPaint.setStyle(Paint.Style.STROKE);
        emptyBoxPaint = new Paint(boxPaint);
        emptyBoxPaint.setAlpha(128);
        textBoxPaint = new Paint(boxPaint);
        textBoxPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setBoxes(List<Detection> detections) {
        RectF[] newBoundingBoxes = new RectF[detections.size()];
        String[] newTexts = new String[detections.size()];
        RectF[] newTextBoxes = new RectF[detections.size()];
        for (int i = 0; i < detections.size(); ++i) {
            RectF relativeBoundingBox = detections.get(i).getBoundingBox();
            RectF boundingBox = new RectF(
                    getWidth() * relativeBoundingBox.left - strokeWidth,
                    getHeight() * relativeBoundingBox.top - strokeWidth,
                    getWidth() * relativeBoundingBox.right + strokeWidth,
                    getHeight() * relativeBoundingBox.bottom + strokeWidth);
            newBoundingBoxes[i] = boundingBox;
            String text = detections.get(i).getText();
            if (text.length() == 0) continue;

            int nChars = textPaint.breakText(text, true, boundingBox.width(), null);
            if (nChars < text.length()) text = text.substring(0, Math.max(nChars - 2, 0)) + "...";
            newTexts[i] = text;
            newTextBoxes[i] = new RectF(
                    boundingBox.left, boundingBox.bottom - cornerRadius,
                    boundingBox.right, boundingBox.bottom - cornerRadius + textSize);
        }
        boundingBoxes = newBoundingBoxes;
        texts = newTexts;
        textBoxes = newTextBoxes;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < boundingBoxes.length; ++i) {
            boolean hasText = texts[i] != null && texts[i].length() != 0;
            canvas.drawRoundRect(
                    boundingBoxes[i], cornerRadius, cornerRadius,
                    hasText ? boxPaint : emptyBoxPaint);
            if (!hasText) continue;
            canvas.drawRoundRect(textBoxes[i], cornerRadius, cornerRadius, textBoxPaint);
            canvas.drawText(
                    texts[i],
                    textBoxes[i].centerX(), textBoxes[i].bottom - cornerRadius, textPaint);
        }
    }
}
