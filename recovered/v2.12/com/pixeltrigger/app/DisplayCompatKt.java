package com.pixeltrigger.app;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Display;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: DisplayCompat.kt */
/* JADX INFO: loaded from: classes3.dex */
@Metadata(m50d1 = {"\u0000\u0012\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u001a\u0014\u0010\u0000\u001a\u00020\u0001*\u00020\u00022\u0006\u0010\u0003\u001a\u00020\u0004H\u0000¨\u0006\u0005"}, m51d2 = {"getRealRect", "", "Landroid/view/Display;", "output", "Landroid/graphics/Rect;", "app_debug"}, m52k = 2, m53mv = {1, 9, 0}, m55xi = 48)
public final class DisplayCompatKt {
    public static final void getRealRect(Display $this$getRealRect, Rect output) {
        Intrinsics.checkNotNullParameter($this$getRealRect, "<this>");
        Intrinsics.checkNotNullParameter(output, "output");
        DisplayMetrics metrics = new DisplayMetrics();
        $this$getRealRect.getRealMetrics(metrics);
        output.set(0, 0, metrics.widthPixels, metrics.heightPixels);
    }
}
