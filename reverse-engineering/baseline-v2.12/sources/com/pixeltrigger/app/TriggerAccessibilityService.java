package com.pixeltrigger.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Path;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.app.NotificationCompat;
import java.util.Collection;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* JADX INFO: compiled from: TriggerAccessibilityService.kt */
/* JADX INFO: loaded from: classes3.dex */
@Metadata(d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0002\u0018\u0000 \u00112\u00020\u0001:\u0001\u0011B\u0005¢\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u0016J\b\u0010\u0007\u001a\u00020\u0004H\u0016J\b\u0010\b\u001a\u00020\u0004H\u0016J\b\u0010\t\u001a\u00020\u0004H\u0014J \u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\r2\b\b\u0002\u0010\u000f\u001a\u00020\u0010¨\u0006\u0012"}, d2 = {"Lcom/pixeltrigger/app/TriggerAccessibilityService;", "Landroid/accessibilityservice/AccessibilityService;", "()V", "onAccessibilityEvent", "", NotificationCompat.CATEGORY_EVENT, "Landroid/view/accessibility/AccessibilityEvent;", "onDestroy", "onInterrupt", "onServiceConnected", "performTap", "", "x", "", "y", "durationMs", "", "Companion", "app_debug"}, m32k = 1, mv = {1, 9, 0}, xi = 48)
public final class TriggerAccessibilityService extends AccessibilityService {

    /* JADX INFO: renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private static volatile TriggerAccessibilityService instance;

    @Override // android.accessibilityservice.AccessibilityService
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
    }

    @Override // android.app.Service
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    public static /* synthetic */ boolean performTap$default(TriggerAccessibilityService triggerAccessibilityService, float f, float f2, long j, int i, Object obj) {
        if ((i & 4) != 0) {
            j = 1;
        }
        return triggerAccessibilityService.performTap(f, f2, j);
    }

    public final boolean performTap(float x, float y, long durationMs) {
        Path $this$performTap_u24lambda_u240 = new Path();
        $this$performTap_u24lambda_u240.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription($this$performTap_u24lambda_u240, 0L, durationMs);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    /* JADX INFO: compiled from: TriggerAccessibilityService.kt */
    @Metadata(d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u000e\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ \u0010\t\u001a\u00020\u00062\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000b2\b\b\u0002\u0010\r\u001a\u00020\u000eR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006\u000f"}, d2 = {"Lcom/pixeltrigger/app/TriggerAccessibilityService$Companion;", "", "()V", "instance", "Lcom/pixeltrigger/app/TriggerAccessibilityService;", "isEnabled", "", "context", "Landroid/content/Context;", "tap", "x", "", "y", "durationMs", "", "app_debug"}, m32k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public static /* synthetic */ boolean tap$default(Companion companion, float f, float f2, long j, int i, Object obj) {
            if ((i & 4) != 0) {
                j = 1;
            }
            return companion.tap(f, f2, j);
        }

        public final boolean tap(float x, float y, long durationMs) {
            TriggerAccessibilityService triggerAccessibilityService = TriggerAccessibilityService.instance;
            return triggerAccessibilityService != null && triggerAccessibilityService.performTap(x, y, durationMs);
        }

        public final boolean isEnabled(Context context) {
            Intrinsics.checkNotNullParameter(context, "context");
            String expected = new ComponentName(context, (Class<?>) TriggerAccessibilityService.class).flattenToString();
            Intrinsics.checkNotNullExpressionValue(expected, "flattenToString(...)");
            String enabledServices = Settings.Secure.getString(context.getContentResolver(), "enabled_accessibility_services");
            if (enabledServices == null) {
                return false;
            }
            Iterable $this$any$iv = StringsKt.split$default((CharSequence) enabledServices, new char[]{':'}, false, 0, 6, (Object) null);
            if (($this$any$iv instanceof Collection) && ((Collection) $this$any$iv).isEmpty()) {
                return false;
            }
            for (Object element$iv : $this$any$iv) {
                String it = (String) element$iv;
                if (StringsKt.equals(it, expected, true)) {
                    return true;
                }
            }
            return false;
        }
    }
}
