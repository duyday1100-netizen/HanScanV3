package com.hanscan.v3.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.hanscan.v3.model.OcrChar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectionOverlayView extends View {
    public interface Listener {
        void onTap(int index);
        void onDragSelection(List<Integer> indices);
    }

    private final Paint normalFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint normalStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<OcrChar> chars = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();
    private int imageW = 1, imageH = 1;
    private int downIndex = -1, lastIndex = -1;
    private float downX, downY;
    private boolean moved = false;
    private Listener listener;

    public SelectionOverlayView(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
        normalFill.setColor(Color.argb(28, 123, 151, 255));
        normalStroke.setColor(Color.rgb(137,160,255));
        normalStroke.setStyle(Paint.Style.STROKE); normalStroke.setStrokeWidth(dp(1.8f));
        selectedFill.setColor(Color.argb(72,80,223,184));
        selectedStroke.setColor(Color.rgb(80,223,184));
        selectedStroke.setStyle(Paint.Style.STROKE); selectedStroke.setStrokeWidth(dp(3f));
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setData(List<OcrChar> chars, int imageW, int imageH) {
        this.chars = chars == null ? new ArrayList<>() : chars;
        this.imageW = Math.max(1, imageW);
        this.imageH = Math.max(1, imageH);
        selected.clear();
        invalidate();
    }

    public void clearData() {
        chars = new ArrayList<>(); selected.clear(); invalidate();
    }

    public void setSelectedIndices(List<Integer> ids) {
        selected.clear(); if (ids != null) selected.addAll(ids); invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Transform t = transform();
        for (int i = 0; i < chars.size(); i++) {
            RectF r = toView(chars.get(i).box, t);
            float rad = Math.min(dp(6), Math.max(dp(2), r.height() * .08f));
            if (selected.contains(i)) {
                canvas.drawRoundRect(r, rad, rad, selectedFill);
                canvas.drawRoundRect(r, rad, rad, selectedStroke);
            } else {
                canvas.drawRoundRect(r, rad, rad, normalFill);
                canvas.drawRoundRect(r, rad, rad, normalStroke);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (chars.isEmpty()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); moved = false;
                downIndex = hit(e.getX(), e.getY()); lastIndex = downIndex;
                if (downIndex >= 0) {
                    selected.clear(); selected.add(downIndex); invalidate();
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (downIndex < 0) return false;
                if (Math.hypot(e.getX()-downX, e.getY()-downY) > dp(7)) moved = true;
                int idx = hit(e.getX(), e.getY());
                if (idx >= 0 && idx != lastIndex) {
                    lastIndex = idx;
                    List<Integer> range = contiguousRange(downIndex, idx);
                    selected.clear(); selected.addAll(range); invalidate();
                    if (listener != null) listener.onDragSelection(range);
                }
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (downIndex >= 0) {
                    if (!moved) {
                        if (listener != null) listener.onTap(downIndex);
                    } else {
                        List<Integer> range = contiguousRange(downIndex, lastIndex >= 0 ? lastIndex : downIndex);
                        if (listener != null) listener.onDragSelection(range);
                    }
                    downIndex = -1; lastIndex = -1;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                downIndex = -1; lastIndex = -1; return true;
        }
        return super.onTouchEvent(e);
    }

    private List<Integer> contiguousRange(int a, int b) {
        List<Integer> out = new ArrayList<>();
        int lo = Math.min(a,b), hi = Math.max(a,b);
        for (int i = lo; i <= hi && i < chars.size(); i++) out.add(i);
        return out;
    }

    private int hit(float vx, float vy) {
        Transform t = transform();
        float ix = (vx - t.offX) / t.scale;
        float iy = (vy - t.offY) / t.scale;
        int best = -1; float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < chars.size(); i++) {
            RectF r = chars.get(i).box;
            float pad = Math.max(7, r.height() * .20f);
            if (ix >= r.left-pad && ix <= r.right+pad && iy >= r.top-pad && iy <= r.bottom+pad) {
                float cx = r.centerX(), cy = r.centerY();
                float d = (ix-cx)*(ix-cx) + (iy-cy)*(iy-cy);
                if (d < bestDist) { bestDist = d; best = i; }
            }
        }
        return best;
    }

    private RectF toView(RectF r, Transform t) {
        return new RectF(r.left*t.scale+t.offX, r.top*t.scale+t.offY, r.right*t.scale+t.offX, r.bottom*t.scale+t.offY);
    }

    private Transform transform() {
        float sx = getWidth() / (float)imageW;
        float sy = getHeight() / (float)imageH;
        float scale = Math.min(sx, sy);
        float dw = imageW * scale, dh = imageH * scale;
        return new Transform(scale, (getWidth()-dw)/2f, (getHeight()-dh)/2f);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private static class Transform { final float scale, offX, offY; Transform(float s,float x,float y){scale=s;offX=x;offY=y;} }
}
