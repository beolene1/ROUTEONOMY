package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import com.routeonomy.app.domain.GeoPoint;
import com.routeonomy.app.domain.Place;
import com.routeonomy.app.domain.Region;
import java.util.ArrayList;
import java.util.List;

/**
 * In-app route sketch: demo route line, region circles, origin/destination and the current position.
 * No map tiles or network: the Pleos navi map renders white on the AVD, so the demo draws its own panel.
 * Coordinates use a local equirectangular projection, which is accurate enough at city scale.
 */
final class RouteMapView extends View {
    private static final double METERS_PER_DEGREE = 111_320.0;
    // Figma map tone: pale beige-grey ground, dark green route, mint region circles.
    private static final int BACKGROUND = 0xffecebe4;
    private static final int GRID = 0xfff5f4ef;
    private static final int ROUTE = UiStyle.GREEN;
    private static final int INK = UiStyle.INK;
    private static final int ACCENT = 0xff6c9181;
    private static final int ACTIVE = UiStyle.GREEN;
    private static final int POSITION = UiStyle.INK;
    private static final int LABEL = UiStyle.MUTED;

    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF card = new RectF();
    private List<GeoPoint> route = new ArrayList<>();
    private List<Region> regions = new ArrayList<>();
    private Place origin, destination;
    private GeoPoint current;
    private String highlightedRegionId;
    private boolean regionEntered;
    private String caption = "";
    private String sourceLabel = "";
    // Projection state, recomputed per draw.
    private double minLat, maxLat, minLon, lonScale, unitsToPx;
    private float offsetX, offsetY;

    RouteMapView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    void setRoute(List<GeoPoint> route, Place origin, Place destination, List<Region> regions) {
        this.route = new ArrayList<>(route);
        this.origin = origin;
        this.destination = destination;
        this.regions = new ArrayList<>(regions);
        invalidate();
    }

    /** @param highlightedRegionId predicted or entered region, drawn emphasized; null for none. */
    void setState(GeoPoint current, String highlightedRegionId, boolean regionEntered, String caption, String sourceLabel) {
        this.current = current;
        this.highlightedRegionId = highlightedRegionId;
        this.regionEntered = regionEntered;
        this.caption = caption == null ? "" : caption;
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(BACKGROUND);
        drawGrid(canvas);
        if (route.size() < 2) return;
        float top = 96 * dp; // room for the caption
        project(getWidth(), getHeight(), top);

        for (Region region : regions) drawRegion(canvas, region);

        path.reset();
        for (int i = 0; i < route.size(); i++) {
            float x = x(route.get(i)), y = y(route.get(i));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        stroke.setColor(Color.WHITE);
        stroke.setStrokeWidth(12 * dp);
        canvas.drawPath(path, stroke);
        stroke.setColor(ROUTE);
        stroke.setStrokeWidth(7 * dp);
        canvas.drawPath(path, stroke);

        if (origin != null) drawPlace(canvas, origin, INK);
        if (destination != null) drawPlace(canvas, destination, ACTIVE);
        if (current != null && visible(current)) {
            float x = x(current), y = y(current);
            fill.setColor(0x33151815);
            canvas.drawCircle(x, y, 22 * dp, fill);
            fill.setColor(Color.WHITE);
            canvas.drawCircle(x, y, 11 * dp, fill);
            fill.setColor(POSITION);
            canvas.drawCircle(x, y, 8 * dp, fill);
        }
        drawCaption(canvas);
    }

    private void drawRegion(Canvas canvas, Region region) {
        GeoPoint c = region.getCenter();
        // Only regions inside the route's bounding box; others (e.g. 혜화역 north of Seoul Station) would sit under the caption.
        if (c.getLatitude() < minLat || c.getLatitude() > maxLat || c.getLongitude() < minLon || x(c) > getWidth()) return;
        boolean highlighted = region.getId().equals(highlightedRegionId);
        float cx = x(region.getCenter()), cy = y(region.getCenter());
        float r = (float) (region.getRadiusMeters() / METERS_PER_DEGREE * unitsToPx);
        fill.setColor(highlighted && regionEntered ? 0x99d7ebe0 : highlighted ? 0x66d7ebe0 : 0x22a9ada4);
        canvas.drawCircle(cx, cy, r, fill);
        stroke.setColor(highlighted ? ACCENT : 0xffb9bab2);
        stroke.setStrokeWidth((highlighted ? 3 : 1.5f) * dp);
        canvas.drawCircle(cx, cy, r, stroke);
        text.setColor(highlighted ? ACTIVE : LABEL);
        text.setTextSize(15 * dp);
        text.setFakeBoldText(highlighted);
        canvas.drawText(region.getName(), cx + r + 6 * dp, cy + 5 * dp, text);
    }

    /** Faint street-like grid so the panel reads as a map without real tiles. */
    private void drawGrid(Canvas canvas) {
        stroke.setColor(GRID);
        stroke.setStrokeWidth(3 * dp);
        float step = 64 * dp;
        for (float x = step / 2; x < getWidth(); x += step) canvas.drawLine(x, 0, x, getHeight(), stroke);
        for (float y = step / 2; y < getHeight(); y += step) canvas.drawLine(0, y, getWidth(), y, stroke);
    }

    private void drawPlace(Canvas canvas, Place place, int color) {
        float x = x(place.getLocation()), y = y(place.getLocation());
        fill.setColor(Color.WHITE);
        canvas.drawCircle(x, y, 10 * dp, fill);
        fill.setColor(color);
        canvas.drawCircle(x, y, 7 * dp, fill);
        text.setColor(INK);
        text.setTextSize(16 * dp);
        text.setFakeBoldText(true);
        canvas.drawText(place.getName(), x + 14 * dp, y + 6 * dp, text);
    }

    private void drawCaption(Canvas canvas) {
        float pad = 16 * dp;
        card.set(pad, pad, getWidth() - pad, 84 * dp);
        fill.setColor(0xf2ffffff);
        canvas.drawRoundRect(card, 14 * dp, 14 * dp, fill);
        text.setColor(INK);
        text.setFakeBoldText(true);
        text.setTextSize(18 * dp);
        canvas.drawText(ellipsize(caption, card.width() - 2 * pad), card.left + pad, card.top + 30 * dp, text);
        text.setFakeBoldText(false);
        text.setColor(LABEL);
        text.setTextSize(13 * dp);
        canvas.drawText(ellipsize(sourceLabel, card.width() - 2 * pad), card.left + pad, card.top + 54 * dp, text);
    }

    private String ellipsize(String value, float width) {
        if (text.measureText(value) <= width) return value;
        int end = text.breakText(value, true, width - text.measureText("…"), null);
        return value.substring(0, Math.max(0, end)) + "…";
    }

    /** Fits the route (and origin/destination) into the view below [top], keeping the aspect ratio. */
    private void project(int width, int height, float top) {
        minLat = Double.MAX_VALUE; maxLat = -Double.MAX_VALUE; minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (GeoPoint p : route) {
            minLat = Math.min(minLat, p.getLatitude()); maxLat = Math.max(maxLat, p.getLatitude());
            minLon = Math.min(minLon, p.getLongitude()); maxLon = Math.max(maxLon, p.getLongitude());
        }
        lonScale = Math.cos(Math.toRadians((minLat + maxLat) / 2));
        double spanX = Math.max((maxLon - minLon) * lonScale, 1e-6), spanY = Math.max(maxLat - minLat, 1e-6);
        float pad = 28 * dp, labelRoom = 120 * dp; // labels are drawn to the right of points
        float availW = Math.max(1, width - 2 * pad - labelRoom), availH = Math.max(1, height - top - 2 * pad);
        unitsToPx = Math.min(availW / spanX, availH / spanY);
        offsetX = (float) (pad + (availW - spanX * unitsToPx) / 2);
        offsetY = (float) (top + pad + (availH - spanY * unitsToPx) / 2);
    }

    private float x(GeoPoint p) { return offsetX + (float) ((p.getLongitude() - minLon) * lonScale * unitsToPx); }
    private float y(GeoPoint p) { return offsetY + (float) ((maxLat - p.getLatitude()) * unitsToPx); }
    private boolean visible(GeoPoint p) {
        float x = x(p), y = y(p);
        return x > -50 * dp && x < getWidth() + 50 * dp && y > -50 * dp && y < getHeight() + 50 * dp;
    }
}
