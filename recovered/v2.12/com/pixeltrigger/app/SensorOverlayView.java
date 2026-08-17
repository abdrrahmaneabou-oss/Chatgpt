package com.pixeltrigger.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import androidx.core.app.NotificationCompat;
import kotlin.Metadata;
import kotlin.NoWhenBranchMatchedException;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: OverlayViews.kt */
/* JADX INFO: loaded from: classes3.dex */
@Metadata(m50d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0000\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005¢\u0006\u0002\u0010\u0006J\u0010\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014H\u0014J\u000e\u0010\u0015\u001a\u00020\u00122\u0006\u0010\u0016\u001a\u00020\u0010R\u0011\u0010\u0007\u001a\u00020\u00058F¢\u0006\u0006\u001a\u0004\b\b\u0010\tR\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\tR\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006\u0017"}, m51d2 = {"Lcom/pixeltrigger/app/SensorOverlayView;", "Landroid/view/View;", "context", "Landroid/content/Context;", "sampleDiameterPx", "", "(Landroid/content/Context;I)V", "outerDiameterPx", "getOuterDiameterPx", "()I", "ringGapPx", "", "ringPaint", "Landroid/graphics/Paint;", "getSampleDiameterPx", NotificationCompat.CATEGORY_STATUS, "Lcom/pixeltrigger/app/SensorStatus;", "onDraw", "", "canvas", "Landroid/graphics/Canvas;", "setStatus", "value", "app_debug"}, m52k = 1, m53mv = {1, 9, 0}, m55xi = 48)
public final class SensorOverlayView extends View {
    private final float ringGapPx;
    private final Paint ringPaint;
    private final int sampleDiameterPx;
    private SensorStatus status;

    /* JADX INFO: compiled from: OverlayViews.kt */
    @Metadata(m52k = 3, m53mv = {1, 9, 0}, m55xi = 48)
    public /* synthetic */ class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;

        static {
            int[] iArr = new int[SensorStatus.values().length];
            try {
                iArr[SensorStatus.WAITING.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[SensorStatus.ARMED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[SensorStatus.FIRED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            $EnumSwitchMapping$0 = iArr;
        }
    }

    public final int getSampleDiameterPx() {
        return this.sampleDiameterPx;
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public SensorOverlayView(Context context, int sampleDiameterPx) {
        super(context);
        Intrinsics.checkNotNullParameter(context, "context");
        this.sampleDiameterPx = sampleDiameterPx;
        this.ringGapPx = Math.max(1.0f, this.sampleDiameterPx * 0.1f);
        Paint $this$ringPaint_u24lambda_u240 = new Paint(1);
        $this$ringPaint_u24lambda_u240.setStyle(Paint.Style.STROKE);
        $this$ringPaint_u24lambda_u240.setStrokeWidth(Math.max(1.0f, this.sampleDiameterPx * 0.08f));
        $this$ringPaint_u24lambda_u240.setAlpha(220);
        this.ringPaint = $this$ringPaint_u24lambda_u240;
        this.status = SensorStatus.WAITING;
        setBackgroundColor(0);
        setContentDescription("دائرة الاستشعار");
    }

    public final int getOuterDiameterPx() {
        return (int) Math.ceil(this.sampleDiameterPx + (this.ringGapPx * 2.0f) + this.ringPaint.getStrokeWidth());
    }

    public final void setStatus(SensorStatus value) {
        Intrinsics.checkNotNullParameter(value, "value");
        if (this.status == value) {
            return;
        }
        this.status = value;
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        int iRgb;
        Intrinsics.checkNotNullParameter(canvas, "canvas");
        super.onDraw(canvas);
        float cx = getWidth() / 2.0f;
        float cy = getHeight() / 2.0f;
        float monitoredRadius = this.sampleDiameterPx / 2.0f;
        float ringRadius = this.ringGapPx + monitoredRadius + (this.ringPaint.getStrokeWidth() / 2.0f);
        Paint paint = this.ringPaint;
        switch (WhenMappings.$EnumSwitchMapping$0[this.status.ordinal()]) {
            case 1:
                iRgb = Color.rgb(255, 190, 80);
                break;
            case 2:
                iRgb = Color.rgb(68, 229, 170);
                break;
            case 3:
                iRgb = Color.rgb(255, 87, 111);
                break;
            default:
                throw new NoWhenBranchMatchedException();
        }
        paint.setColor(iRgb);
        canvas.drawCircle(cx, cy, ringRadius, this.ringPaint);
    }
}
