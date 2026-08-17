package com.pixeltrigger.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: OverlayViews.kt */
/* JADX INFO: loaded from: classes3.dex */
@Metadata(m50d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0000\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005¢\u0006\u0002\u0010\u0006J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u0010H\u0014R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\bX\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f¨\u0006\u0011"}, m51d2 = {"Lcom/pixeltrigger/app/TargetOverlayView;", "Landroid/view/View;", "context", "Landroid/content/Context;", "visibleDiameterPx", "", "(Landroid/content/Context;I)V", "crossPaint", "Landroid/graphics/Paint;", "fillPaint", "strokePaint", "getVisibleDiameterPx", "()I", "onDraw", "", "canvas", "Landroid/graphics/Canvas;", "app_debug"}, m52k = 1, m53mv = {1, 9, 0}, m55xi = 48)
public final class TargetOverlayView extends View {
    private final Paint crossPaint;
    private final Paint fillPaint;
    private final Paint strokePaint;
    private final int visibleDiameterPx;

    public final int getVisibleDiameterPx() {
        return this.visibleDiameterPx;
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public TargetOverlayView(Context context, int visibleDiameterPx) {
        super(context);
        Intrinsics.checkNotNullParameter(context, "context");
        this.visibleDiameterPx = visibleDiameterPx;
        Paint $this$fillPaint_u24lambda_u240 = new Paint(1);
        $this$fillPaint_u24lambda_u240.setStyle(Paint.Style.FILL);
        $this$fillPaint_u24lambda_u240.setColor(Color.argb(120, 112, 76, 255));
        this.fillPaint = $this$fillPaint_u24lambda_u240;
        Paint $this$strokePaint_u24lambda_u241 = new Paint(1);
        $this$strokePaint_u24lambda_u241.setStyle(Paint.Style.STROKE);
        $this$strokePaint_u24lambda_u241.setStrokeWidth(getResources().getDisplayMetrics().density * 1.5f);
        $this$strokePaint_u24lambda_u241.setColor(Color.rgb(186, 169, 255));
        this.strokePaint = $this$strokePaint_u24lambda_u241;
        Paint $this$crossPaint_u24lambda_u242 = new Paint(1);
        $this$crossPaint_u24lambda_u242.setStyle(Paint.Style.STROKE);
        $this$crossPaint_u24lambda_u242.setStrokeWidth(getResources().getDisplayMetrics().density);
        $this$crossPaint_u24lambda_u242.setColor(-1);
        this.crossPaint = $this$crossPaint_u24lambda_u242;
        setBackgroundColor(0);
        setContentDescription("دائرة تنفيذ الضغطة");
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        Intrinsics.checkNotNullParameter(canvas, "canvas");
        super.onDraw(canvas);
        float cx = getWidth() / 2.0f;
        float cy = getHeight() / 2.0f;
        float radius = this.visibleDiameterPx / 2.0f;
        canvas.drawCircle(cx, cy, radius, this.fillPaint);
        canvas.drawCircle(cx, cy, radius, this.strokePaint);
        float arm = radius * 0.45f;
        canvas.drawLine(cx - arm, cy, cx + arm, cy, this.crossPaint);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, this.crossPaint);
    }
}
