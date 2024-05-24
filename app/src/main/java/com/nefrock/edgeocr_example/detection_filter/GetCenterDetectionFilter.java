package com.nefrock.edgeocr_example.detection_filter;

import android.graphics.RectF;

import com.nefrock.edgeocr.Detection;
import com.nefrock.edgeocr.DetectionFilter;

import java.util.Collections;
import java.util.List;

public class GetCenterDetectionFilter extends DetectionFilter {
    @Override
    public List<Detection> filter(List<Detection> list) {
        Detection mostLikelyBox = null;
        double minDistance = Double.MAX_VALUE;
        for (Detection detection: list) {
            RectF box = detection.getBoundingBox();
            double distance = Math.pow(box.centerX()-0.5, 2) + Math.pow(box.centerY()-0.5, 2);
            if (distance < minDistance) {
                minDistance = distance;
                mostLikelyBox = detection;
            }
        }
        if ( mostLikelyBox != null) {
            return Collections.singletonList(mostLikelyBox);
        } else {
            return Collections.emptyList();
        }
    }
}
