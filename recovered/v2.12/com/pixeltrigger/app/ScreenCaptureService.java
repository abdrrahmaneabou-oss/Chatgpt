package com.pixeltrigger.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.location.LocationRequestCompat;
import java.nio.ByteBuffer;
import java.util.Iterator;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.UByte;
import kotlin.Unit;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import kotlin.jdk7.AutoCloseableKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Ref;
import kotlin.math.MathKt;
import kotlin.ranges.RangesKt;
import kotlin.text.StringsKt;

/* JADX INFO: compiled from: ScreenCaptureService.kt */
/* JADX INFO: loaded from: classes3.dex */
@Metadata(m50d1 = {"\u0000\u0087\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0014\n\u0002\u0018\u0002\n\u0002\b\u0005*\u0001\u000f\u0018\u0000 \u009a\u00012\u00020\u0001:\u0006\u0099\u0001\u009a\u0001\u009b\u0001B\u0005¢\u0006\u0002\u0010\u0002J\u0010\u0010?\u001a\u00020@2\u0006\u0010A\u001a\u00020\u0004H\u0002JY\u0010B\u001a\u00020@2\u0006\u0010C\u001a\u00020!2\u0006\u0010D\u001a\u00020\u001f2\n\b\u0002\u0010E\u001a\u0004\u0018\u00010\n2\u001c\b\u0002\u0010F\u001a\u0016\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020\n\u0012\u0004\u0012\u00020@\u0018\u00010G2\u0010\b\u0002\u0010H\u001a\n\u0012\u0004\u0012\u00020@\u0018\u00010IH\u0002¢\u0006\u0002\u0010JJ\b\u0010K\u001a\u00020LH\u0002J!\u0010M\u001a\u00020@2\u0006\u0010D\u001a\u00020\u001f2\n\b\u0002\u0010E\u001a\u0004\u0018\u00010\nH\u0002¢\u0006\u0002\u0010NJ\b\u0010O\u001a\u00020@H\u0002J\b\u0010P\u001a\u00020@H\u0002J\u0016\u0010Q\u001a\u00020!2\f\u0010R\u001a\b\u0012\u0004\u0012\u00020@0IH\u0002J\u0018\u0010S\u001a\u00020\u00142\u0006\u0010T\u001a\u00020\n2\u0006\u0010U\u001a\u00020\nH\u0002J\b\u0010V\u001a\u00020@H\u0002J\b\u0010W\u001a\u00020@H\u0002J\b\u0010X\u001a\u00020YH\u0002J\u0010\u0010Z\u001a\u00020\n2\u0006\u0010[\u001a\u00020\nH\u0002J\u0010\u0010\\\u001a\u00020@2\u0006\u0010]\u001a\u00020\u0012H\u0002J\u0018\u0010^\u001a\u00020_2\u0006\u0010U\u001a\u00020\n2\u0006\u0010`\u001a\u00020\nH\u0002J&\u0010a\u001a\u00020b2\u0006\u0010c\u001a\u00020d2\f\u0010R\u001a\b\u0012\u0004\u0012\u00020@0I2\u0006\u0010e\u001a\u00020\fH\u0002J\u0010\u0010f\u001a\u00020\n2\u0006\u0010g\u001a\u00020hH\u0002J8\u0010i\u001a\u0010\u0012\u0004\u0012\u00020h\u0012\u0004\u0012\u00020h\u0018\u00010j2\b\u0010D\u001a\u0004\u0018\u00010\u001f2\u0006\u0010k\u001a\u00020\n2\u0006\u0010T\u001a\u00020\n2\u0006\u0010U\u001a\u00020\nH\u0002J\u0014\u0010l\u001a\u0004\u0018\u00010m2\b\u0010n\u001a\u0004\u0018\u00010oH\u0016J\b\u0010p\u001a\u00020@H\u0016J\b\u0010q\u001a\u00020@H\u0016J\"\u0010r\u001a\u00020\n2\b\u0010n\u001a\u0004\u0018\u00010o2\u0006\u0010s\u001a\u00020\n2\u0006\u0010t\u001a\u00020\nH\u0016J\u0018\u0010u\u001a\u00020\u001f2\u0006\u0010T\u001a\u00020\n2\u0006\u0010U\u001a\u00020\nH\u0002J\b\u0010v\u001a\u00020@H\u0002J\u0010\u0010w\u001a\u00020@2\u0006\u0010x\u001a\u00020yH\u0002J\u0018\u0010z\u001a\u00020@2\u0006\u0010A\u001a\u00020\u00042\u0006\u0010]\u001a\u00020\u0012H\u0002J\u0012\u0010{\u001a\u0004\u0018\u00010o2\u0006\u0010n\u001a\u00020oH\u0002J\b\u0010|\u001a\u00020@H\u0002JC\u0010}\u001a\u00020@2\b\u0010C\u001a\u0004\u0018\u00010!2\b\u0010D\u001a\u0004\u0018\u00010\u001f2\u0014\u0010i\u001a\u0010\u0012\u0004\u0012\u00020h\u0012\u0004\u0012\u00020h\u0018\u00010j2\n\b\u0002\u0010E\u001a\u0004\u0018\u00010\nH\u0002¢\u0006\u0002\u0010~J\b\u0010\u007f\u001a\u00020@H\u0002J\t\u0010\u0080\u0001\u001a\u00020@H\u0002J%\u0010\u0081\u0001\u001a\u00030\u0082\u00012\u0007\u0010\u0083\u0001\u001a\u00020\n2\u0007\u0010\u0084\u0001\u001a\u00020\n2\u0007\u0010\u0085\u0001\u001a\u00020hH\u0002J7\u0010\u0086\u0001\u001a\u0004\u0018\u00010\u00042\u0006\u0010x\u001a\u00020y2\u0007\u0010\u0087\u0001\u001a\u00020\n2\u0007\u0010\u0088\u0001\u001a\u00020\n2\u0007\u0010\u0089\u0001\u001a\u00020h2\u0007\u0010\u008a\u0001\u001a\u00020hH\u0002J\u001b\u0010\u008b\u0001\u001a\u00020@2\u0007\u0010\u008c\u0001\u001a\u00020\n2\u0007\u0010\u008d\u0001\u001a\u00020oH\u0002J\t\u0010\u008e\u0001\u001a\u00020@H\u0002J\u0012\u0010\u008f\u0001\u001a\u00020@2\u0007\u0010\u0090\u0001\u001a\u00020dH\u0002J\t\u0010\u0091\u0001\u001a\u00020@H\u0002J\u0012\u0010\u0092\u0001\u001a\u00020b2\u0007\u0010\u0093\u0001\u001a\u00020dH\u0002J\t\u0010\u0094\u0001\u001a\u00020@H\u0002J\u0013\u0010\u0095\u0001\u001a\u00020@2\b\u0010\u0096\u0001\u001a\u00030\u0097\u0001H\u0002J\u0019\u0010\u0098\u0001\u001a\u00020@2\u0006\u0010A\u001a\u00020\u00042\u0006\u0010]\u001a\u00020\u0012H\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u00020\u000fX\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u0010R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0014X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0015\u001a\u0004\u0018\u00010\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0012X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0006X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0012X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u001bX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u001c\u001a\u0004\u0018\u00010\u001dX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u001fX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010 \u001a\u0004\u0018\u00010!X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\"\u001a\u0004\u0018\u00010\u001fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020$X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010'\u001a\u00020(X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010)\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010*\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010+\u001a\u0004\u0018\u00010\u001fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010,\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010-\u001a\u0004\u0018\u00010.X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010/\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u00100\u001a\u0004\u0018\u00010\u001fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u00101\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u00102\u001a\u0004\u0018\u000103X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u00104\u001a\u000205X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u00106\u001a\u0004\u0018\u000107X\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u00108\u001a\b\u0018\u000109R\u00020:X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010;\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010<\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010=\u001a\u00020>X\u0082.¢\u0006\u0002\n\u0000¨\u0006\u009c\u0001"}, m51d2 = {"Lcom/pixeltrigger/app/ScreenCaptureService;", "Landroid/app/Service;", "()V", "armedWhiteSample", "Lcom/pixeltrigger/app/ScreenCaptureService$ColorSample;", "captureHandler", "Landroid/os/Handler;", "captureThread", "Landroid/os/HandlerThread;", "changedFrames", "", "circlesVisible", "", "densityDpi", "displayListener", "com/pixeltrigger/app/ScreenCaptureService$displayListener$1", "Lcom/pixeltrigger/app/ScreenCaptureService$displayListener$1;", "firedAtMs", "", "imageReader", "Landroid/media/ImageReader;", "latestColorSample", "latestSampleAtMs", "mainHandler", "manualRearmRequestedAtMs", "manualRearmWhiteFrames", "mediaProjection", "Landroid/media/projection/MediaProjection;", "menuButton", "Landroid/widget/TextView;", "menuButtonParams", "Landroid/view/WindowManager$LayoutParams;", "menuPanel", "Landroid/view/View;", "menuPanelParams", "preferences", "Landroid/content/SharedPreferences;", "rearmDelayEnabled", "rearmSeconds", "refreshDisplayRunnable", "Ljava/lang/Runnable;", "screenHeight", "screenWidth", "sensorParams", "sensorTouchSize", "sensorView", "Lcom/pixeltrigger/app/SensorOverlayView;", "sensorVisibleDiameter", "targetParams", "targetTouchSize", "targetView", "Lcom/pixeltrigger/app/TargetOverlayView;", "triggerState", "Lcom/pixeltrigger/app/ScreenCaptureService$TriggerState;", "virtualDisplay", "Landroid/hardware/display/VirtualDisplay;", "wakeLock", "Landroid/os/PowerManager$WakeLock;", "Landroid/os/PowerManager;", "whiteFrames", "whiteRearmEnabled", "windowManager", "Landroid/view/WindowManager;", "arm", "", "sample", "attachDrag", "view", "params", "visibleDiameter", "onMoved", "Lkotlin/Function2;", "onClick", "Lkotlin/Function0;", "(Landroid/view/View;Landroid/view/WindowManager$LayoutParams;Ljava/lang/Integer;Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function0;)V", "buildNotification", "Landroid/app/Notification;", "clampOverlayPosition", "(Landroid/view/WindowManager$LayoutParams;Ljava/lang/Integer;)V", "clearOneTimeRearmRequest", "closeMenu", "completeStopCard", "action", "createImageReader", "width", "height", "createNotificationChannel", "createOverlays", "currentScreenBounds", "Landroid/graphics/Rect;", "dp", "value", "fire", "now", "matchWrap", "Landroid/widget/LinearLayout$LayoutParams;", "bottomMargin", "menuActionButton", "Landroid/widget/Button;", "textValue", "", "danger", "mmToPx", "mm", "", "normalizedCenter", "Lkotlin/Pair;", "size", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "flags", "startId", "overlayParams", "performTargetTap", "processImage", "image", "Landroid/media/Image;", "processOneTimeRearmOverride", "projectionIntent", "refreshDisplayGeometry", "repositionOverlay", "(Landroid/view/View;Landroid/view/WindowManager$LayoutParams;Lkotlin/Pair;Ljava/lang/Integer;)V", "requestOneTimeRearmOverride", "resetTriggerForSensorMove", "roundedBackground", "Landroid/graphics/drawable/GradientDrawable;", "fill", "stroke", "radiusDp", "sampleCircularRegion", "centerX", "centerY", "radiusX", "radiusY", "setupProjection", "resultCode", "resultData", "showMenu", "showRearmMessage", "message", "shutdownCompletely", "smallControlButton", "label", "toggleMenu", "updateSensorStatus", NotificationCompat.CATEGORY_STATUS, "Lcom/pixeltrigger/app/SensorStatus;", "updateTriggerEngine", "ColorSample", "Companion", "TriggerState", "app_debug"}, m52k = 1, m53mv = {1, 9, 0}, m55xi = 48)
public final class ScreenCaptureService extends Service {
    public static final String ACTION_START = "com.pixeltrigger.app.action.START";
    public static final String ACTION_STOP = "com.pixeltrigger.app.action.STOP";
    private static final int ARM_WHITE_AVERAGE_CHROMA = 50;
    private static final int ARM_WHITE_AVERAGE_LUMINANCE = 195;
    private static final float ARM_WHITE_COVERAGE = 0.6f;
    private static final String CHANNEL_ID = "pixel_trigger_monitor";
    private static final long DISPLAY_REFRESH_DELAY_MS = 250;
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final int HOLD_WHITE_AVERAGE_CHROMA = 70;
    private static final int HOLD_WHITE_AVERAGE_LUMINANCE = 170;
    private static final float HOLD_WHITE_COVERAGE = 0.35f;
    private static final String KEY_REARM_DELAY_ENABLED = "rearm_delay_enabled";
    private static final String KEY_REARM_SECONDS = "rearm_seconds";
    private static final String KEY_SENSOR_X = "sensor_x";
    private static final String KEY_SENSOR_Y = "sensor_y";
    private static final String KEY_TARGET_X = "target_x";
    private static final String KEY_TARGET_Y = "target_y";
    private static final String KEY_TIMED_REARM_LEGACY = "timed_rearm";
    private static final String KEY_WHITE_REARM = "white_rearm";
    private static final long MANUAL_REARM_MENU_SETTLE_MS = 35;
    private static final long MANUAL_REARM_TIMEOUT_MS = 500;
    private static final int MANUAL_REARM_WHITE_FRAMES = 2;
    private static final int MIN_CHANGE_CHANNEL_DELTA = 30;
    private static final int MIN_CHANGE_CHROMA_RISE = 24;
    private static final int MIN_CHANGE_LUMINANCE_DROP = 26;
    private static final float MIN_CHANGE_WHITE_COVERAGE_DROP = 0.35f;
    private static final int MIN_SAMPLE_PIXELS = 3;
    private static final int NOTIFICATION_ID = 2207;
    private static final String PREFS_NAME = "pixel_trigger_settings";
    private static final int REQUIRED_ARM_FRAMES = 3;
    private static final int REQUIRED_CHANGE_FRAMES = 1;
    private static final int REQUIRED_REARM_FRAMES = 3;
    private static final float SENSOR_DIAMETER_MM = 0.8f;
    private static final long TAP_DURATION_MS = 1;
    private static final long TARGET_RESTORE_DELAY_MS = 40;
    private static final int WHITE_PIXEL_LUMINANCE = 195;
    private static final int WHITE_PIXEL_MAX_CHROMA = 55;
    private static final int WHITE_PIXEL_MIN_CHANNEL = 175;
    private ColorSample armedWhiteSample;
    private Handler captureHandler;
    private HandlerThread captureThread;
    private int changedFrames;
    private int densityDpi;
    private long firedAtMs;
    private ImageReader imageReader;
    private ColorSample latestColorSample;
    private long latestSampleAtMs;
    private long manualRearmRequestedAtMs;
    private int manualRearmWhiteFrames;
    private MediaProjection mediaProjection;
    private TextView menuButton;
    private WindowManager.LayoutParams menuButtonParams;
    private View menuPanel;
    private WindowManager.LayoutParams menuPanelParams;
    private SharedPreferences preferences;
    private volatile boolean rearmDelayEnabled;
    private int screenHeight;
    private int screenWidth;
    private WindowManager.LayoutParams sensorParams;
    private int sensorTouchSize;
    private SensorOverlayView sensorView;
    private int sensorVisibleDiameter;
    private WindowManager.LayoutParams targetParams;
    private int targetTouchSize;
    private TargetOverlayView targetView;
    private VirtualDisplay virtualDisplay;
    private PowerManager.WakeLock wakeLock;
    private int whiteFrames;
    private WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean circlesVisible = true;
    private volatile boolean whiteRearmEnabled = true;
    private volatile int rearmSeconds = 10;
    private TriggerState triggerState = TriggerState.WAITING_FOR_WHITE;
    private final Runnable refreshDisplayRunnable = new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda0
        /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
        @Override // java.lang.Runnable
        public final void run() {
            ScreenCaptureService.refreshDisplayRunnable$lambda$0(this.f$0);
        }
    };
    private final ScreenCaptureService$displayListener$1 displayListener = new DisplayManager.DisplayListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$displayListener$1
        @Override // android.hardware.display.DisplayManager.DisplayListener
        public void onDisplayAdded(int displayId) {
        }

        @Override // android.hardware.display.DisplayManager.DisplayListener
        public void onDisplayRemoved(int displayId) {
        }

        @Override // android.hardware.display.DisplayManager.DisplayListener
        public void onDisplayChanged(int displayId) {
            if (displayId == 0 && this.this$0.mediaProjection != null) {
                this.this$0.mainHandler.removeCallbacks(this.this$0.refreshDisplayRunnable);
                this.this$0.mainHandler.postDelayed(this.this$0.refreshDisplayRunnable, 250L);
            }
        }
    };

    /* JADX INFO: compiled from: ScreenCaptureService.kt */
    @Metadata(m50d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0005\b\u0082\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005¨\u0006\u0006"}, m51d2 = {"Lcom/pixeltrigger/app/ScreenCaptureService$TriggerState;", "", "(Ljava/lang/String;I)V", "WAITING_FOR_WHITE", "ARMED", "WAITING_REARM", "app_debug"}, m52k = 1, m53mv = {1, 9, 0}, m55xi = 48)
    private enum TriggerState {
        WAITING_FOR_WHITE,
        ARMED,
        WAITING_REARM;

        private static final /* synthetic */ EnumEntries $ENTRIES = EnumEntriesKt.enumEntries($VALUES);

        public static EnumEntries<TriggerState> getEntries() {
            return $ENTRIES;
        }
    }

    /* JADX INFO: compiled from: ScreenCaptureService.kt */
    @Metadata(m52k = 3, m53mv = {1, 9, 0}, m55xi = 48)
    public /* synthetic */ class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;

        static {
            int[] iArr = new int[TriggerState.values().length];
            try {
                iArr[TriggerState.WAITING_FOR_WHITE.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[TriggerState.ARMED.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[TriggerState.WAITING_REARM.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            $EnumSwitchMapping$0 = iArr;
        }
    }

    /* JADX INFO: compiled from: ScreenCaptureService.kt */
    @Metadata(m50d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\n\n\u0002\u0010\u000b\n\u0002\b\u0011\n\u0002\u0010\u000e\n\u0000\b\u0082\b\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0003\u0012\u0006\u0010\t\u001a\u00020\u0003¢\u0006\u0002\u0010\nJ\t\u0010\u0017\u001a\u00020\u0003HÆ\u0003J\t\u0010\u0018\u001a\u00020\u0003HÆ\u0003J\t\u0010\u0019\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001a\u001a\u00020\u0007HÆ\u0003J\t\u0010\u001b\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001c\u001a\u00020\u0003HÆ\u0003JE\u0010\u001d\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00032\b\b\u0002\u0010\t\u001a\u00020\u0003HÆ\u0001J\u0013\u0010\u001e\u001a\u00020\u00122\b\u0010\u001f\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010 \u001a\u00020\u0003HÖ\u0001J\u000e\u0010!\u001a\u00020\u00122\u0006\u0010\"\u001a\u00020\u0000J\t\u0010#\u001a\u00020$HÖ\u0001R\u0011\u0010\u0005\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\t\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\r\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\fR\u0011\u0010\b\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\fR\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\fR\u0011\u0010\u0011\u001a\u00020\u00128F¢\u0006\u0006\u001a\u0004\b\u0011\u0010\u0013R\u0011\u0010\u0014\u001a\u00020\u00128F¢\u0006\u0006\u001a\u0004\b\u0014\u0010\u0013R\u0011\u0010\u0006\u001a\u00020\u0007¢\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016¨\u0006%"}, m51d2 = {"Lcom/pixeltrigger/app/ScreenCaptureService$ColorSample;", "", "averageRed", "", "averageGreen", "averageBlue", "whiteRatio", "", "averageLuminance", "averageChroma", "(IIIFII)V", "getAverageBlue", "()I", "getAverageChroma", "getAverageGreen", "getAverageLuminance", "getAverageRed", "isArmingWhite", "", "()Z", "isHoldingWhite", "getWhiteRatio", "()F", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "other", "hashCode", "isMeaningfulChangeFrom", "reference", "toString", "", "app_debug"}, m52k = 1, m53mv = {1, 9, 0}, m55xi = 48)
    private static final /* data */ class ColorSample {
        private final int averageBlue;
        private final int averageChroma;
        private final int averageGreen;
        private final int averageLuminance;
        private final int averageRed;
        private final float whiteRatio;

        public static /* synthetic */ ColorSample copy$default(ColorSample colorSample, int i, int i2, int i3, float f, int i4, int i5, int i6, Object obj) {
            if ((i6 & 1) != 0) {
                i = colorSample.averageRed;
            }
            if ((i6 & 2) != 0) {
                i2 = colorSample.averageGreen;
            }
            int i7 = i2;
            if ((i6 & 4) != 0) {
                i3 = colorSample.averageBlue;
            }
            int i8 = i3;
            if ((i6 & 8) != 0) {
                f = colorSample.whiteRatio;
            }
            float f2 = f;
            if ((i6 & 16) != 0) {
                i4 = colorSample.averageLuminance;
            }
            int i9 = i4;
            if ((i6 & 32) != 0) {
                i5 = colorSample.averageChroma;
            }
            return colorSample.copy(i, i7, i8, f2, i9, i5);
        }

        /* JADX INFO: renamed from: component1, reason: from getter */
        public final int getAverageRed() {
            return this.averageRed;
        }

        /* JADX INFO: renamed from: component2, reason: from getter */
        public final int getAverageGreen() {
            return this.averageGreen;
        }

        /* JADX INFO: renamed from: component3, reason: from getter */
        public final int getAverageBlue() {
            return this.averageBlue;
        }

        /* JADX INFO: renamed from: component4, reason: from getter */
        public final float getWhiteRatio() {
            return this.whiteRatio;
        }

        /* JADX INFO: renamed from: component5, reason: from getter */
        public final int getAverageLuminance() {
            return this.averageLuminance;
        }

        /* JADX INFO: renamed from: component6, reason: from getter */
        public final int getAverageChroma() {
            return this.averageChroma;
        }

        public final ColorSample copy(int averageRed, int averageGreen, int averageBlue, float whiteRatio, int averageLuminance, int averageChroma) {
            return new ColorSample(averageRed, averageGreen, averageBlue, whiteRatio, averageLuminance, averageChroma);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ColorSample)) {
                return false;
            }
            ColorSample colorSample = (ColorSample) other;
            return this.averageRed == colorSample.averageRed && this.averageGreen == colorSample.averageGreen && this.averageBlue == colorSample.averageBlue && Float.compare(this.whiteRatio, colorSample.whiteRatio) == 0 && this.averageLuminance == colorSample.averageLuminance && this.averageChroma == colorSample.averageChroma;
        }

        public int hashCode() {
            return (((((((((Integer.hashCode(this.averageRed) * 31) + Integer.hashCode(this.averageGreen)) * 31) + Integer.hashCode(this.averageBlue)) * 31) + Float.hashCode(this.whiteRatio)) * 31) + Integer.hashCode(this.averageLuminance)) * 31) + Integer.hashCode(this.averageChroma);
        }

        public String toString() {
            return "ColorSample(averageRed=" + this.averageRed + ", averageGreen=" + this.averageGreen + ", averageBlue=" + this.averageBlue + ", whiteRatio=" + this.whiteRatio + ", averageLuminance=" + this.averageLuminance + ", averageChroma=" + this.averageChroma + ")";
        }

        public ColorSample(int averageRed, int averageGreen, int averageBlue, float whiteRatio, int averageLuminance, int averageChroma) {
            this.averageRed = averageRed;
            this.averageGreen = averageGreen;
            this.averageBlue = averageBlue;
            this.whiteRatio = whiteRatio;
            this.averageLuminance = averageLuminance;
            this.averageChroma = averageChroma;
        }

        public final int getAverageRed() {
            return this.averageRed;
        }

        public final int getAverageGreen() {
            return this.averageGreen;
        }

        public final int getAverageBlue() {
            return this.averageBlue;
        }

        public final float getWhiteRatio() {
            return this.whiteRatio;
        }

        public final int getAverageLuminance() {
            return this.averageLuminance;
        }

        public final int getAverageChroma() {
            return this.averageChroma;
        }

        public final boolean isArmingWhite() {
            return this.whiteRatio >= ScreenCaptureService.ARM_WHITE_COVERAGE && this.averageLuminance >= 195 && this.averageChroma <= 50;
        }

        public final boolean isHoldingWhite() {
            return this.whiteRatio >= 0.35f && this.averageLuminance >= ScreenCaptureService.HOLD_WHITE_AVERAGE_LUMINANCE && this.averageChroma <= ScreenCaptureService.HOLD_WHITE_AVERAGE_CHROMA;
        }

        public final boolean isMeaningfulChangeFrom(ColorSample reference) {
            Intrinsics.checkNotNullParameter(reference, "reference");
            int channelDelta = Math.max(Math.abs(this.averageRed - reference.averageRed), Math.max(Math.abs(this.averageGreen - reference.averageGreen), Math.abs(this.averageBlue - reference.averageBlue)));
            int luminanceDrop = reference.averageLuminance - this.averageLuminance;
            int chromaRise = this.averageChroma - reference.averageChroma;
            float coverageDrop = reference.whiteRatio - this.whiteRatio;
            return channelDelta >= 30 || luminanceDrop >= 26 || chromaRise >= 24 || coverageDrop >= 0.35f;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void refreshDisplayRunnable$lambda$0(ScreenCaptureService this$0) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.refreshDisplayGeometry();
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        Object systemService = getSystemService("window");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
        this.windowManager = (WindowManager) systemService;
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, 0);
        Intrinsics.checkNotNullExpressionValue(sharedPreferences, "getSharedPreferences(...)");
        this.preferences = sharedPreferences;
        Object systemService2 = getSystemService("display");
        Intrinsics.checkNotNull(systemService2, "null cannot be cast to non-null type android.hardware.display.DisplayManager");
        ((DisplayManager) systemService2).registerDisplayListener(this.displayListener, this.mainHandler);
        SharedPreferences sharedPreferences2 = this.preferences;
        SharedPreferences sharedPreferences3 = null;
        if (sharedPreferences2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences2 = null;
        }
        this.whiteRearmEnabled = sharedPreferences2.getBoolean(KEY_WHITE_REARM, true);
        SharedPreferences sharedPreferences4 = this.preferences;
        if (sharedPreferences4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences4 = null;
        }
        SharedPreferences sharedPreferences5 = this.preferences;
        if (sharedPreferences5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences5 = null;
        }
        this.rearmDelayEnabled = sharedPreferences4.getBoolean(KEY_REARM_DELAY_ENABLED, sharedPreferences5.getBoolean(KEY_TIMED_REARM_LEGACY, false));
        SharedPreferences sharedPreferences6 = this.preferences;
        if (sharedPreferences6 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
        } else {
            sharedPreferences3 = sharedPreferences6;
        }
        this.rearmSeconds = RangesKt.coerceIn(sharedPreferences3.getInt(KEY_REARM_SECONDS, 10), 5, 60);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        Object systemService3 = getSystemService("power");
        Intrinsics.checkNotNull(systemService3, "null cannot be cast to non-null type android.os.PowerManager");
        PowerManager.WakeLock $this$onCreate_u24lambda_u241 = ((PowerManager) systemService3).newWakeLock(1, getPackageName() + ":PixelMonitor");
        $this$onCreate_u24lambda_u241.acquire();
        this.wakeLock = $this$onCreate_u24lambda_u241;
    }

    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (action != null) {
            switch (action.hashCode()) {
                case 1017468990:
                    if (action.equals(ACTION_STOP)) {
                        shutdownCompletely();
                    }
                    break;
                case 1476754310:
                    if (action.equals(ACTION_START) && this.mediaProjection == null) {
                        int resultCode = intent.getIntExtra("result_code", 0);
                        Intent resultData = projectionIntent(intent);
                        if (resultCode == 0 || resultData == null) {
                            stopSelf();
                        } else {
                            setupProjection(resultCode, resultData);
                        }
                    }
                    break;
            }
            return 2;
        }
        return 2;
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final Intent projectionIntent(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return (Intent) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        return (Intent) intent.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private final void setupProjection(int resultCode, Intent resultData) {
        Rect rectCurrentScreenBounds = currentScreenBounds();
        this.screenWidth = rectCurrentScreenBounds.width();
        this.screenHeight = rectCurrentScreenBounds.height();
        this.densityDpi = getResources().getDisplayMetrics().densityDpi;
        HandlerThread handlerThread = new HandlerThread("PixelTriggerCapture", -8);
        handlerThread.start();
        this.captureThread = handlerThread;
        HandlerThread handlerThread2 = this.captureThread;
        Intrinsics.checkNotNull(handlerThread2);
        this.captureHandler = new Handler(handlerThread2.getLooper());
        Object systemService = getSystemService("media_projection");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.media.projection.MediaProjectionManager");
        MediaProjection mediaProjection = ((MediaProjectionManager) systemService).getMediaProjection(resultCode, resultData);
        mediaProjection.registerCallback(new MediaProjection.Callback() { // from class: com.pixeltrigger.app.ScreenCaptureService$setupProjection$2$1
            @Override // android.media.projection.MediaProjection.Callback
            public void onStop() {
                this.this$0.stopSelf();
            }
        }, this.mainHandler);
        this.mediaProjection = mediaProjection;
        this.imageReader = createImageReader(this.screenWidth, this.screenHeight);
        MediaProjection mediaProjection2 = this.mediaProjection;
        VirtualDisplay virtualDisplayCreateVirtualDisplay = null;
        if (mediaProjection2 != null) {
            int i = this.screenWidth;
            int i2 = this.screenHeight;
            int i3 = this.densityDpi;
            ImageReader imageReader = this.imageReader;
            virtualDisplayCreateVirtualDisplay = mediaProjection2.createVirtualDisplay("PixelTriggerDisplay", i, i2, i3, 16, imageReader != null ? imageReader.getSurface() : null, null, this.captureHandler);
        }
        this.virtualDisplay = virtualDisplayCreateVirtualDisplay;
        this.mainHandler.post(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda15
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // java.lang.Runnable
            public final void run() {
                ScreenCaptureService.setupProjection$lambda$4(this.f$0);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void setupProjection$lambda$4(ScreenCaptureService this$0) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.createOverlays();
        this$0.updateSensorStatus(SensorStatus.WAITING);
    }

    private final Rect currentScreenBounds() {
        WindowManager windowManager = null;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowManager windowManager2 = this.windowManager;
            if (windowManager2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("windowManager");
            } else {
                windowManager = windowManager2;
            }
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            Intrinsics.checkNotNull(bounds);
            return bounds;
        }
        Rect it = new Rect();
        WindowManager windowManager3 = this.windowManager;
        if (windowManager3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("windowManager");
        } else {
            windowManager = windowManager3;
        }
        Display defaultDisplay = windowManager.getDefaultDisplay();
        Intrinsics.checkNotNullExpressionValue(defaultDisplay, "getDefaultDisplay(...)");
        DisplayCompatKt.getRealRect(defaultDisplay, it);
        return it;
    }

    private final ImageReader createImageReader(int width, int height) {
        ImageReader reader = ImageReader.newInstance(width, height, 1, 2);
        Intrinsics.checkNotNullExpressionValue(reader, "newInstance(...)");
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda1
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.media.ImageReader.OnImageAvailableListener
            public final void onImageAvailable(ImageReader imageReader) throws Exception {
                ScreenCaptureService.createImageReader$lambda$8$lambda$7(this.f$0, imageReader);
            }
        }, this.captureHandler);
        return reader;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void createImageReader$lambda$8$lambda$7(ScreenCaptureService this$0, ImageReader source) throws Exception {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Image imageAcquireLatestImage = source.acquireLatestImage();
        if (imageAcquireLatestImage != null) {
            Image image = imageAcquireLatestImage;
            try {
                Image image2 = image;
                this$0.processImage(image2);
                Unit unit = Unit.INSTANCE;
                AutoCloseableKt.closeFinally(image, null);
            } catch (Throwable th) {
                try {
                    throw th;
                } catch (Throwable th2) {
                    AutoCloseableKt.closeFinally(image, th);
                    throw th2;
                }
            }
        }
    }

    private final void refreshDisplayGeometry() {
        Rect bounds = currentScreenBounds();
        final int newWidth = bounds.width();
        final int newHeight = bounds.height();
        final int newDensityDpi = getResources().getDisplayMetrics().densityDpi;
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }
        if (newWidth == this.screenWidth && newHeight == this.screenHeight && newDensityDpi == this.densityDpi) {
            return;
        }
        int oldWidth = RangesKt.coerceAtLeast(this.screenWidth, 1);
        int oldHeight = RangesKt.coerceAtLeast(this.screenHeight, 1);
        Pair<Float, Float> pairNormalizedCenter = normalizedCenter(this.sensorParams, this.sensorTouchSize, oldWidth, oldHeight);
        Pair<Float, Float> pairNormalizedCenter2 = normalizedCenter(this.targetParams, this.targetTouchSize, oldWidth, oldHeight);
        WindowManager.LayoutParams layoutParams = this.menuButtonParams;
        int menuSize = layoutParams != null ? layoutParams.width : m48dp(58);
        Pair<Float, Float> pairNormalizedCenter3 = normalizedCenter(this.menuButtonParams, menuSize, oldWidth, oldHeight);
        closeMenu();
        this.screenWidth = newWidth;
        this.screenHeight = newHeight;
        this.densityDpi = newDensityDpi;
        Handler handler = this.captureHandler;
        if (handler != null) {
            handler.post(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda10
                /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
                @Override // java.lang.Runnable
                public final void run() {
                    ScreenCaptureService.refreshDisplayGeometry$lambda$9(this.f$0, newWidth, newHeight, newDensityDpi);
                }
            });
        }
        SensorOverlayView sensorOverlayView = this.sensorView;
        WindowManager.LayoutParams layoutParams2 = this.sensorParams;
        SensorOverlayView sensorOverlayView2 = this.sensorView;
        repositionOverlay(sensorOverlayView, layoutParams2, pairNormalizedCenter, sensorOverlayView2 != null ? Integer.valueOf(sensorOverlayView2.getOuterDiameterPx()) : null);
        TargetOverlayView targetOverlayView = this.targetView;
        WindowManager.LayoutParams layoutParams3 = this.targetParams;
        TargetOverlayView targetOverlayView2 = this.targetView;
        repositionOverlay(targetOverlayView, layoutParams3, pairNormalizedCenter2, targetOverlayView2 != null ? Integer.valueOf(targetOverlayView2.getVisibleDiameterPx()) : null);
        repositionOverlay$default(this, this.menuButton, this.menuButtonParams, pairNormalizedCenter3, null, 8, null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void refreshDisplayGeometry$lambda$9(ScreenCaptureService this$0, int $newWidth, int $newHeight, int $newDensityDpi) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        ImageReader replacementReader = this$0.createImageReader($newWidth, $newHeight);
        ImageReader oldReader = this$0.imageReader;
        this$0.imageReader = replacementReader;
        VirtualDisplay virtualDisplay = this$0.virtualDisplay;
        if (virtualDisplay != null) {
            virtualDisplay.resize($newWidth, $newHeight, $newDensityDpi);
        }
        VirtualDisplay virtualDisplay2 = this$0.virtualDisplay;
        if (virtualDisplay2 != null) {
            virtualDisplay2.setSurface(replacementReader.getSurface());
        }
        if (oldReader != null) {
            oldReader.setOnImageAvailableListener(null, null);
        }
        if (oldReader != null) {
            oldReader.close();
        }
    }

    private final Pair<Float, Float> normalizedCenter(WindowManager.LayoutParams params, int size, int width, int height) {
        if (params == null) {
            return null;
        }
        return new Pair<>(Float.valueOf(RangesKt.coerceIn((params.x + (size / 2.0f)) / width, 0.0f, 1.0f)), Float.valueOf(RangesKt.coerceIn((params.y + (size / 2.0f)) / height, 0.0f, 1.0f)));
    }

    static /* synthetic */ void repositionOverlay$default(ScreenCaptureService screenCaptureService, View view, WindowManager.LayoutParams layoutParams, Pair pair, Integer num, int i, Object obj) {
        if ((i & 8) != 0) {
            num = null;
        }
        screenCaptureService.repositionOverlay(view, layoutParams, pair, num);
    }

    private final void repositionOverlay(View view, WindowManager.LayoutParams params, Pair<Float, Float> normalizedCenter, Integer visibleDiameter) {
        if (view == null || params == null || normalizedCenter == null) {
            return;
        }
        int width = RangesKt.coerceAtLeast(view.getWidth() > 0 ? view.getWidth() : params.width, 1);
        int height = RangesKt.coerceAtLeast(view.getHeight() > 0 ? view.getHeight() : params.height, 1);
        params.x = MathKt.roundToInt((normalizedCenter.getFirst().floatValue() * this.screenWidth) - (width / 2.0f));
        params.y = MathKt.roundToInt((normalizedCenter.getSecond().floatValue() * this.screenHeight) - (height / 2.0f));
        clampOverlayPosition(params, visibleDiameter);
        try {
            Result.Companion companion = Result.INSTANCE;
            ScreenCaptureService $this$repositionOverlay_u24lambda_u2410 = this;
            WindowManager windowManager = $this$repositionOverlay_u24lambda_u2410.windowManager;
            if (windowManager == null) {
                Intrinsics.throwUninitializedPropertyAccessException("windowManager");
                windowManager = null;
            }
            windowManager.updateViewLayout(view, params);
            Result.m129constructorimpl(Unit.INSTANCE);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            Result.m129constructorimpl(ResultKt.createFailure(th));
        }
    }

    private final void createOverlays() {
        if (this.sensorView != null) {
            return;
        }
        this.sensorVisibleDiameter = RangesKt.coerceAtLeast(mmToPx(SENSOR_DIAMETER_MM), 1);
        int targetVisibleDiameter = RangesKt.coerceAtLeast(mmToPx(5.0f), m48dp(12));
        SensorOverlayView sensor = new SensorOverlayView(this, this.sensorVisibleDiameter);
        this.sensorView = sensor;
        this.sensorTouchSize = Math.max(m48dp(48), sensor.getOuterDiameterPx() + m48dp(30));
        this.targetTouchSize = Math.max(m48dp(52), m48dp(24) + targetVisibleDiameter);
        WindowManager.LayoutParams $this$createOverlays_u24lambda_u2411 = overlayParams(this.sensorTouchSize, this.sensorTouchSize);
        SharedPreferences sharedPreferences = this.preferences;
        WindowManager windowManager = null;
        if (sharedPreferences == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences = null;
        }
        $this$createOverlays_u24lambda_u2411.x = sharedPreferences.getInt(KEY_SENSOR_X, (this.screenWidth / 2) - (this.sensorTouchSize / 2));
        SharedPreferences sharedPreferences2 = this.preferences;
        if (sharedPreferences2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences2 = null;
        }
        $this$createOverlays_u24lambda_u2411.y = sharedPreferences2.getInt(KEY_SENSOR_Y, (this.screenHeight / 2) - (this.sensorTouchSize / 2));
        this.sensorParams = $this$createOverlays_u24lambda_u2411;
        WindowManager.LayoutParams layoutParams = this.sensorParams;
        Intrinsics.checkNotNull(layoutParams);
        clampOverlayPosition(layoutParams, Integer.valueOf(sensor.getOuterDiameterPx()));
        WindowManager windowManager2 = this.windowManager;
        if (windowManager2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("windowManager");
            windowManager2 = null;
        }
        windowManager2.addView(sensor, this.sensorParams);
        WindowManager.LayoutParams layoutParams2 = this.sensorParams;
        Intrinsics.checkNotNull(layoutParams2);
        attachDrag$default(this, sensor, layoutParams2, Integer.valueOf(sensor.getOuterDiameterPx()), new Function2<Integer, Integer, Unit>() { // from class: com.pixeltrigger.app.ScreenCaptureService.createOverlays.2
            {
                super(2);
            }

            /* JADX DEBUG: Method arguments types fixed to match base method, original types: [java.lang.Object, java.lang.Object] */
            /* JADX DEBUG: Return type fixed from 'java.lang.Object' to match base method */
            @Override // kotlin.jvm.functions.Function2
            public /* bridge */ /* synthetic */ Unit invoke(Integer num, Integer num2) {
                invoke(num.intValue(), num2.intValue());
                return Unit.INSTANCE;
            }

            public final void invoke(int x, int y) {
                ScreenCaptureService.this.resetTriggerForSensorMove();
                SharedPreferences sharedPreferences3 = ScreenCaptureService.this.preferences;
                if (sharedPreferences3 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("preferences");
                    sharedPreferences3 = null;
                }
                sharedPreferences3.edit().putInt(ScreenCaptureService.KEY_SENSOR_X, x).putInt(ScreenCaptureService.KEY_SENSOR_Y, y).apply();
            }
        }, null, 16, null);
        this.targetView = new TargetOverlayView(this, targetVisibleDiameter);
        WindowManager.LayoutParams $this$createOverlays_u24lambda_u2412 = overlayParams(this.targetTouchSize, this.targetTouchSize);
        SharedPreferences sharedPreferences3 = this.preferences;
        if (sharedPreferences3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences3 = null;
        }
        $this$createOverlays_u24lambda_u2412.x = sharedPreferences3.getInt(KEY_TARGET_X, (this.screenWidth / 2) + m48dp(HOLD_WHITE_AVERAGE_CHROMA));
        SharedPreferences sharedPreferences4 = this.preferences;
        if (sharedPreferences4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences4 = null;
        }
        $this$createOverlays_u24lambda_u2412.y = sharedPreferences4.getInt(KEY_TARGET_Y, (this.screenHeight / 2) - (this.targetTouchSize / 2));
        this.targetParams = $this$createOverlays_u24lambda_u2412;
        WindowManager.LayoutParams layoutParams3 = this.targetParams;
        Intrinsics.checkNotNull(layoutParams3);
        clampOverlayPosition(layoutParams3, Integer.valueOf(targetVisibleDiameter));
        WindowManager windowManager3 = this.windowManager;
        if (windowManager3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("windowManager");
            windowManager3 = null;
        }
        windowManager3.addView(this.targetView, this.targetParams);
        TargetOverlayView targetOverlayView = this.targetView;
        Intrinsics.checkNotNull(targetOverlayView);
        WindowManager.LayoutParams layoutParams4 = this.targetParams;
        Intrinsics.checkNotNull(layoutParams4);
        attachDrag$default(this, targetOverlayView, layoutParams4, Integer.valueOf(targetVisibleDiameter), new Function2<Integer, Integer, Unit>() { // from class: com.pixeltrigger.app.ScreenCaptureService.createOverlays.4
            {
                super(2);
            }

            /* JADX DEBUG: Method arguments types fixed to match base method, original types: [java.lang.Object, java.lang.Object] */
            /* JADX DEBUG: Return type fixed from 'java.lang.Object' to match base method */
            @Override // kotlin.jvm.functions.Function2
            public /* bridge */ /* synthetic */ Unit invoke(Integer num, Integer num2) {
                invoke(num.intValue(), num2.intValue());
                return Unit.INSTANCE;
            }

            public final void invoke(int x, int y) {
                SharedPreferences sharedPreferences5 = ScreenCaptureService.this.preferences;
                if (sharedPreferences5 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("preferences");
                    sharedPreferences5 = null;
                }
                sharedPreferences5.edit().putInt(ScreenCaptureService.KEY_TARGET_X, x).putInt(ScreenCaptureService.KEY_TARGET_Y, y).apply();
            }
        }, null, 16, null);
        TextView $this$createOverlays_u24lambda_u2413 = new TextView(this);
        $this$createOverlays_u24lambda_u2413.setText("PT");
        $this$createOverlays_u24lambda_u2413.setTextSize(18.0f);
        $this$createOverlays_u24lambda_u2413.setTypeface(Typeface.DEFAULT_BOLD);
        $this$createOverlays_u24lambda_u2413.setGravity(17);
        $this$createOverlays_u24lambda_u2413.setTextColor(-1);
        $this$createOverlays_u24lambda_u2413.setBackground(roundedBackground(Color.rgb(91, 54, 221), Color.rgb(152, 128, 255), 24.0f));
        this.menuButton = $this$createOverlays_u24lambda_u2413;
        int menuSize = m48dp(58);
        WindowManager.LayoutParams $this$createOverlays_u24lambda_u2414 = overlayParams(menuSize, menuSize);
        $this$createOverlays_u24lambda_u2414.x = RangesKt.coerceAtLeast((this.screenWidth - menuSize) - m48dp(14), 0);
        $this$createOverlays_u24lambda_u2414.y = m48dp(72);
        this.menuButtonParams = $this$createOverlays_u24lambda_u2414;
        WindowManager windowManager4 = this.windowManager;
        if (windowManager4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("windowManager");
        } else {
            windowManager = windowManager4;
        }
        windowManager.addView(this.menuButton, this.menuButtonParams);
        TextView textView = this.menuButton;
        Intrinsics.checkNotNull(textView);
        WindowManager.LayoutParams layoutParams5 = this.menuButtonParams;
        Intrinsics.checkNotNull(layoutParams5);
        attachDrag$default(this, textView, layoutParams5, null, null, new Function0<Unit>() { // from class: com.pixeltrigger.app.ScreenCaptureService.createOverlays.7
            {
                super(0);
            }

            /* JADX DEBUG: Return type fixed from 'java.lang.Object' to match base method */
            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX DEBUG: Possible override for method kotlin.jvm.functions.Function0.invoke()Ljava/lang/Object; */
            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                ScreenCaptureService.this.toggleMenu();
            }
        }, 12, null);
    }

    private final WindowManager.LayoutParams overlayParams(int width, int height) {
        WindowManager.LayoutParams $this$overlayParams_u24lambda_u2415 = new WindowManager.LayoutParams(width, height, 2038, 776, -3);
        $this$overlayParams_u24lambda_u2415.gravity = 8388659;
        return $this$overlayParams_u24lambda_u2415;
    }

    /* JADX DEBUG: Don't trust debug lines info. Repeating lines: [402=4] */
    /* JADX DEBUG: Multi-variable search result rejected for r7v0, resolved type: com.pixeltrigger.app.ScreenCaptureService */
    /* JADX WARN: Multi-variable type inference failed */
    static /* synthetic */ void attachDrag$default(ScreenCaptureService screenCaptureService, View view, WindowManager.LayoutParams layoutParams, Integer num, Function2 function2, Function0 function0, int i, Object obj) {
        screenCaptureService.attachDrag(view, layoutParams, (i & 4) != 0 ? null : num, (i & 8) != 0 ? null : function2, (i & 16) != 0 ? null : function0);
    }

    private final void attachDrag(final View view, final WindowManager.LayoutParams params, final Integer visibleDiameter, final Function2<? super Integer, ? super Integer, Unit> onMoved, final Function0<Unit> onClick) {
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final Ref.FloatRef downRawX = new Ref.FloatRef();
        final Ref.FloatRef downRawY = new Ref.FloatRef();
        final Ref.IntRef startX = new Ref.IntRef();
        final Ref.IntRef startY = new Ref.IntRef();
        final Ref.BooleanRef moved = new Ref.BooleanRef();
        view.setOnTouchListener(new View.OnTouchListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda13
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.view.View.OnTouchListener
            public final boolean onTouch(View view2, MotionEvent motionEvent) {
                return ScreenCaptureService.attachDrag$lambda$17(downRawX, downRawY, startX, params, startY, moved, touchSlop, this, visibleDiameter, onMoved, onClick, view, view2, motionEvent);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final boolean attachDrag$lambda$17(Ref.FloatRef downRawX, Ref.FloatRef downRawY, Ref.IntRef startX, WindowManager.LayoutParams params, Ref.IntRef startY, Ref.BooleanRef moved, int $touchSlop, ScreenCaptureService this$0, Integer $visibleDiameter, Function2 $onMoved, Function0 $onClick, View view, View view2, MotionEvent event) {
        Intrinsics.checkNotNullParameter(downRawX, "$downRawX");
        Intrinsics.checkNotNullParameter(downRawY, "$downRawY");
        Intrinsics.checkNotNullParameter(startX, "$startX");
        Intrinsics.checkNotNullParameter(params, "$params");
        Intrinsics.checkNotNullParameter(startY, "$startY");
        Intrinsics.checkNotNullParameter(moved, "$moved");
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(view, "$view");
        switch (event.getActionMasked()) {
            case 0:
                downRawX.element = event.getRawX();
                downRawY.element = event.getRawY();
                startX.element = params.x;
                startY.element = params.y;
                moved.element = false;
                return true;
            case 1:
            case 3:
                if (moved.element) {
                    if ($onMoved != null) {
                        $onMoved.invoke(Integer.valueOf(params.x), Integer.valueOf(params.y));
                    }
                } else if ($onClick != null) {
                    $onClick.invoke();
                }
                return true;
            case 2:
                float dx = event.getRawX() - downRawX.element;
                float dy = event.getRawY() - downRawY.element;
                if (Math.abs(dx) > $touchSlop || Math.abs(dy) > $touchSlop) {
                    moved.element = true;
                }
                params.x = startX.element + MathKt.roundToInt(dx);
                params.y = startY.element + MathKt.roundToInt(dy);
                this$0.clampOverlayPosition(params, $visibleDiameter);
                try {
                    Result.Companion companion = Result.INSTANCE;
                    WindowManager windowManager = this$0.windowManager;
                    if (windowManager == null) {
                        Intrinsics.throwUninitializedPropertyAccessException("windowManager");
                        windowManager = null;
                    }
                    windowManager.updateViewLayout(view, params);
                    Result.m129constructorimpl(Unit.INSTANCE);
                    break;
                } catch (Throwable th) {
                    Result.Companion companion2 = Result.INSTANCE;
                    Result.m129constructorimpl(ResultKt.createFailure(th));
                }
                return true;
            default:
                return false;
        }
    }

    static /* synthetic */ void clampOverlayPosition$default(ScreenCaptureService screenCaptureService, WindowManager.LayoutParams layoutParams, Integer num, int i, Object obj) {
        if ((i & 2) != 0) {
            num = null;
        }
        screenCaptureService.clampOverlayPosition(layoutParams, num);
    }

    /* JADX DEBUG: Don't trust debug lines info. Repeating lines: [454=4, 457=4] */
    private final void clampOverlayPosition(WindowManager.LayoutParams params, Integer visibleDiameter) {
        int iMax;
        int width = RangesKt.coerceAtLeast(params.width, 1);
        int height = RangesKt.coerceAtLeast(params.height, 1);
        int verticalPadding = 0;
        if (visibleDiameter != null) {
            int it = visibleDiameter.intValue();
            iMax = Math.max(0, (width - RangesKt.coerceAtMost(it, width)) / 2);
        } else {
            iMax = 0;
        }
        int horizontalPadding = iMax;
        if (visibleDiameter != null) {
            int it2 = visibleDiameter.intValue();
            verticalPadding = Math.max(0, (height - RangesKt.coerceAtMost(it2, height)) / 2);
        }
        int minX = -horizontalPadding;
        int minY = -verticalPadding;
        int maxX = Math.max(minX, (this.screenWidth - width) + horizontalPadding);
        int maxY = Math.max(minY, (this.screenHeight - height) + verticalPadding);
        params.x = RangesKt.coerceIn(params.x, minX, maxX);
        params.y = RangesKt.coerceIn(params.y, minY, maxY);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void toggleMenu() {
        if (this.menuPanel == null) {
            showMenu();
        } else {
            closeMenu();
        }
    }

    private final void showMenu() {
        if (this.menuPanel != null) {
            return;
        }
        LinearLayout $this$showMenu_u24lambda_u2420 = new LinearLayout(this);
        $this$showMenu_u24lambda_u2420.setOrientation(1);
        $this$showMenu_u24lambda_u2420.setGravity(1);
        $this$showMenu_u24lambda_u2420.setLayoutDirection(1);
        $this$showMenu_u24lambda_u2420.setPadding(m48dp(18), m48dp(18), m48dp(18), m48dp(18));
        $this$showMenu_u24lambda_u2420.setBackground(roundedBackground(Color.argb(248, 246, 246, 248), Color.rgb(190, 190, 198), 24.0f));
        Button circlesButton = menuActionButton("إظهار / إخفاء الدائرتين", new Function0<Unit>() { // from class: com.pixeltrigger.app.ScreenCaptureService$showMenu$circlesButton$1
            {
                super(0);
            }

            /* JADX DEBUG: Return type fixed from 'java.lang.Object' to match base method */
            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX DEBUG: Possible override for method kotlin.jvm.functions.Function0.invoke()Ljava/lang/Object; */
            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                this.this$0.circlesVisible = !this.this$0.circlesVisible;
                SensorOverlayView sensorOverlayView = this.this$0.sensorView;
                if (sensorOverlayView != null) {
                    sensorOverlayView.setVisibility(this.this$0.circlesVisible ? 0 : 8);
                }
                TargetOverlayView targetOverlayView = this.this$0.targetView;
                if (targetOverlayView != null) {
                    targetOverlayView.setVisibility(this.this$0.circlesVisible ? 0 : 8);
                }
            }
        }, false);
        $this$showMenu_u24lambda_u2420.addView(circlesButton, matchWrap(m48dp(56), m48dp(8)));
        View stopCard = completeStopCard(new Function0<Unit>() { // from class: com.pixeltrigger.app.ScreenCaptureService$showMenu$stopCard$1
            {
                super(0);
            }

            /* JADX DEBUG: Return type fixed from 'java.lang.Object' to match base method */
            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX DEBUG: Possible override for method kotlin.jvm.functions.Function0.invoke()Ljava/lang/Object; */
            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                this.this$0.shutdownCompletely();
            }
        });
        $this$showMenu_u24lambda_u2420.addView(stopCard, matchWrap(-2, m48dp(18)));
        TextView $this$showMenu_u24lambda_u2421 = new TextView(this);
        $this$showMenu_u24lambda_u2421.setText("إعادة التسليح");
        $this$showMenu_u24lambda_u2421.setTextSize(21.0f);
        $this$showMenu_u24lambda_u2421.setTypeface(Typeface.DEFAULT_BOLD);
        $this$showMenu_u24lambda_u2421.setTextColor(Color.rgb(25, 25, 31));
        $this$showMenu_u24lambda_u2421.setGravity(17);
        $this$showMenu_u24lambda_u2420.addView($this$showMenu_u24lambda_u2421, matchWrap(-2, m48dp(10)));
        Switch $this$showMenu_u24lambda_u2422 = new Switch(this);
        $this$showMenu_u24lambda_u2422.setText("إعادة التسليح عند عودة اللون الأبيض");
        $this$showMenu_u24lambda_u2422.setTextSize(17.0f);
        $this$showMenu_u24lambda_u2422.setTextColor(Color.rgb(28, 28, 34));
        $this$showMenu_u24lambda_u2422.setChecked(this.whiteRearmEnabled);
        $this$showMenu_u24lambda_u2422.setPadding(m48dp(8), m48dp(10), m48dp(8), m48dp(10));
        $this$showMenu_u24lambda_u2420.addView($this$showMenu_u24lambda_u2422, matchWrap(-2, m48dp(4)));
        final Switch $this$showMenu_u24lambda_u2423 = new Switch(this);
        $this$showMenu_u24lambda_u2423.setText("تأخير إعادة التسليح");
        $this$showMenu_u24lambda_u2423.setTextSize(17.0f);
        $this$showMenu_u24lambda_u2423.setTextColor(Color.rgb(28, 28, 34));
        $this$showMenu_u24lambda_u2423.setChecked(this.rearmDelayEnabled);
        $this$showMenu_u24lambda_u2423.setPadding(m48dp(8), m48dp(10), m48dp(8), m48dp(10));
        $this$showMenu_u24lambda_u2420.addView($this$showMenu_u24lambda_u2423, matchWrap(-2, m48dp(4)));
        TextView $this$showMenu_u24lambda_u2424 = new TextView(this);
        $this$showMenu_u24lambda_u2424.setText("يبدأ التأخير بعد الضغطة، ولا يحدث التسليح إلا بعد انتهاء المدة وعودة الأبيض.");
        $this$showMenu_u24lambda_u2424.setTextSize(13.0f);
        $this$showMenu_u24lambda_u2424.setTextColor(Color.rgb(92, 92, LocationRequestCompat.QUALITY_LOW_POWER));
        $this$showMenu_u24lambda_u2424.setGravity(17);
        $this$showMenu_u24lambda_u2424.setPadding(m48dp(8), 0, m48dp(8), m48dp(8));
        $this$showMenu_u24lambda_u2420.addView($this$showMenu_u24lambda_u2424, matchWrap(-2, m48dp(4)));
        TextView $this$showMenu_u24lambda_u2425 = new TextView(this);
        $this$showMenu_u24lambda_u2425.setText("مدة التأخير (5–60 ثانية)");
        $this$showMenu_u24lambda_u2425.setTextSize(15.0f);
        $this$showMenu_u24lambda_u2425.setTextColor(Color.rgb(65, 65, 75));
        $this$showMenu_u24lambda_u2425.setGravity(17);
        $this$showMenu_u24lambda_u2420.addView($this$showMenu_u24lambda_u2425, matchWrap(-2, m48dp(6)));
        final LinearLayout $this$showMenu_u24lambda_u2426 = new LinearLayout(this);
        $this$showMenu_u24lambda_u2426.setOrientation(0);
        $this$showMenu_u24lambda_u2426.setGravity(17);
        $this$showMenu_u24lambda_u2420.addView($this$showMenu_u24lambda_u2426, matchWrap(m48dp(54), m48dp(16)));
        final Button minus = smallControlButton("−");
        final EditText $this$showMenu_u24lambda_u2427 = new EditText(this);
        $this$showMenu_u24lambda_u2427.setText(String.valueOf(this.rearmSeconds));
        $this$showMenu_u24lambda_u2427.setTextSize(18.0f);
        $this$showMenu_u24lambda_u2427.setTextColor(Color.rgb(20, 20, 26));
        $this$showMenu_u24lambda_u2427.setGravity(17);
        $this$showMenu_u24lambda_u2427.setInputType(2);
        $this$showMenu_u24lambda_u2427.setImeOptions(6);
        $this$showMenu_u24lambda_u2427.setSelectAllOnFocus(true);
        $this$showMenu_u24lambda_u2427.setBackground(roundedBackground(-1, Color.rgb(190, 190, 198), 14.0f));
        final Button plus = smallControlButton("+");
        $this$showMenu_u24lambda_u2426.addView(minus, new LinearLayout.LayoutParams(m48dp(64), m48dp(52)));
        LinearLayout.LayoutParams $this$showMenu_u24lambda_u2428 = new LinearLayout.LayoutParams(0, m48dp(52), 1.0f);
        $this$showMenu_u24lambda_u2428.setMarginStart(m48dp(10));
        $this$showMenu_u24lambda_u2428.setMarginEnd(m48dp(10));
        Unit unit = Unit.INSTANCE;
        $this$showMenu_u24lambda_u2426.addView($this$showMenu_u24lambda_u2427, $this$showMenu_u24lambda_u2428);
        $this$showMenu_u24lambda_u2426.addView(plus, new LinearLayout.LayoutParams(m48dp(64), m48dp(52)));
        minus.setOnClickListener(new View.OnClickListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda3
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ScreenCaptureService.showMenu$lambda$29(this.f$0, $this$showMenu_u24lambda_u2427, view);
            }
        });
        plus.setOnClickListener(new View.OnClickListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda4
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ScreenCaptureService.showMenu$lambda$30(this.f$0, $this$showMenu_u24lambda_u2427, view);
            }
        });
        $this$showMenu_u24lambda_u2427.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda5
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.widget.TextView.OnEditorActionListener
            public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                return ScreenCaptureService.showMenu$lambda$31($this$showMenu_u24lambda_u2427, this, textView, i, keyEvent);
            }
        });
        $this$showMenu_u24lambda_u2427.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda6
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.view.View.OnFocusChangeListener
            public final void onFocusChange(View view, boolean z) {
                ScreenCaptureService.showMenu$lambda$32($this$showMenu_u24lambda_u2427, this, view, z);
            }
        });
        final Button oneTimeRearmButton = menuActionButton("تفعيل التسليح الآن لمرة واحدة", new Function0<Unit>() { // from class: com.pixeltrigger.app.ScreenCaptureService$showMenu$oneTimeRearmButton$1
            {
                super(0);
            }

            /* JADX DEBUG: Return type fixed from 'java.lang.Object' to match base method */
            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX DEBUG: Possible override for method kotlin.jvm.functions.Function0.invoke()Ljava/lang/Object; */
            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                this.this$0.requestOneTimeRearmOverride();
            }
        }, false);
        $this$showMenu_u24lambda_u2420.addView(oneTimeRearmButton, matchWrap(m48dp(56), m48dp(5)));
        TextView $this$showMenu_u24lambda_u2433 = new TextView(this);
        $this$showMenu_u24lambda_u2433.setText("يتجاهل الزمن المتبقي لهذه الدورة فقط، ويتسلح إذا كان الأبيض موجودًا فعلًا.");
        $this$showMenu_u24lambda_u2433.setTextSize(13.0f);
        $this$showMenu_u24lambda_u2433.setTextColor(Color.rgb(92, 92, LocationRequestCompat.QUALITY_LOW_POWER));
        $this$showMenu_u24lambda_u2433.setGravity(17);
        $this$showMenu_u24lambda_u2433.setPadding(m48dp(8), 0, m48dp(8), m48dp(10));
        $this$showMenu_u24lambda_u2420.addView($this$showMenu_u24lambda_u2433, matchWrap(-2, m48dp(4)));
        $this$showMenu_u24lambda_u2422.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda7
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                ScreenCaptureService.showMenu$lambda$34(this.f$0, $this$showMenu_u24lambda_u2423, minus, plus, $this$showMenu_u24lambda_u2427, oneTimeRearmButton, $this$showMenu_u24lambda_u2426, compoundButton, z);
            }
        });
        $this$showMenu_u24lambda_u2423.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda8
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                ScreenCaptureService.showMenu$lambda$35(this.f$0, $this$showMenu_u24lambda_u2423, minus, plus, $this$showMenu_u24lambda_u2427, oneTimeRearmButton, $this$showMenu_u24lambda_u2426, compoundButton, z);
            }
        });
        showMenu$updateDelayControls($this$showMenu_u24lambda_u2423, this, minus, plus, $this$showMenu_u24lambda_u2427, oneTimeRearmButton, $this$showMenu_u24lambda_u2426);
        Button closeButton = menuActionButton("إغلاق القائمة", new Function0<Unit>() { // from class: com.pixeltrigger.app.ScreenCaptureService$showMenu$closeButton$1
            {
                super(0);
            }

            /* JADX DEBUG: Return type fixed from 'java.lang.Object' to match base method */
            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX DEBUG: Possible override for method kotlin.jvm.functions.Function0.invoke()Ljava/lang/Object; */
            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                this.this$0.closeMenu();
            }
        }, false);
        $this$showMenu_u24lambda_u2420.addView(closeButton, matchWrap(m48dp(56), 0));
        Drawable panelBackground = $this$showMenu_u24lambda_u2420.getBackground();
        $this$showMenu_u24lambda_u2420.setBackground(null);
        ScrollView $this$showMenu_u24lambda_u2436 = new ScrollView(this);
        $this$showMenu_u24lambda_u2436.setFillViewport(true);
        $this$showMenu_u24lambda_u2436.setVerticalScrollBarEnabled(true);
        $this$showMenu_u24lambda_u2436.setOverScrollMode(1);
        $this$showMenu_u24lambda_u2436.setBackground(panelBackground);
        $this$showMenu_u24lambda_u2436.setClipToOutline(true);
        $this$showMenu_u24lambda_u2436.addView($this$showMenu_u24lambda_u2420, new FrameLayout.LayoutParams(-1, -2));
        int menuHeight = Math.min(RangesKt.coerceAtLeast(this.screenHeight - m48dp(16), m48dp(180)), m48dp(680));
        this.menuPanel = $this$showMenu_u24lambda_u2436;
        WindowManager.LayoutParams $this$showMenu_u24lambda_u2437 = new WindowManager.LayoutParams(RangesKt.coerceAtMost(this.screenWidth - m48dp(28), m48dp(420)), menuHeight, 2038, 288, -3);
        $this$showMenu_u24lambda_u2437.gravity = 49;
        $this$showMenu_u24lambda_u2437.x = 0;
        $this$showMenu_u24lambda_u2437.y = Math.max(m48dp(8), (this.screenHeight - menuHeight) / 2);
        $this$showMenu_u24lambda_u2437.softInputMode = 16;
        this.menuPanelParams = $this$showMenu_u24lambda_u2437;
        WindowManager windowManager = this.windowManager;
        if (windowManager == null) {
            Intrinsics.throwUninitializedPropertyAccessException("windowManager");
            windowManager = null;
        }
        windowManager.addView($this$showMenu_u24lambda_u2436, this.menuPanelParams);
    }

    private static final void showMenu$commitDuration(ScreenCaptureService this$0, EditText durationInput, String raw) {
        Integer intOrNull = StringsKt.toIntOrNull(raw);
        int value = RangesKt.coerceIn(intOrNull != null ? intOrNull.intValue() : this$0.rearmSeconds, 5, 60);
        this$0.rearmSeconds = value;
        durationInput.setText(String.valueOf(value));
        SharedPreferences sharedPreferences = this$0.preferences;
        if (sharedPreferences == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences = null;
        }
        sharedPreferences.edit().putInt(KEY_REARM_SECONDS, value).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showMenu$lambda$29(ScreenCaptureService this$0, EditText durationInput, View it) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(durationInput, "$durationInput");
        showMenu$commitDuration(this$0, durationInput, String.valueOf(this$0.rearmSeconds - 1));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showMenu$lambda$30(ScreenCaptureService this$0, EditText durationInput, View it) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(durationInput, "$durationInput");
        showMenu$commitDuration(this$0, durationInput, String.valueOf(this$0.rearmSeconds + 1));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final boolean showMenu$lambda$31(EditText durationInput, ScreenCaptureService this$0, TextView textView, int actionId, KeyEvent keyEvent) {
        Intrinsics.checkNotNullParameter(durationInput, "$durationInput");
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (actionId == 6) {
            showMenu$commitDuration(this$0, durationInput, durationInput.getText().toString());
            durationInput.clearFocus();
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showMenu$lambda$32(EditText durationInput, ScreenCaptureService this$0, View view, boolean hasFocus) {
        Intrinsics.checkNotNullParameter(durationInput, "$durationInput");
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (!hasFocus) {
            showMenu$commitDuration(this$0, durationInput, durationInput.getText().toString());
        }
    }

    private static final void showMenu$updateDelayControls(Switch delaySwitch, ScreenCaptureService this$0, Button minus, Button plus, EditText durationInput, Button oneTimeRearmButton, LinearLayout durationRow) {
        delaySwitch.setEnabled(this$0.whiteRearmEnabled);
        delaySwitch.setAlpha(this$0.whiteRearmEnabled ? 1.0f : 0.45f);
        boolean durationEnabled = this$0.whiteRearmEnabled && this$0.rearmDelayEnabled;
        minus.setEnabled(durationEnabled);
        plus.setEnabled(durationEnabled);
        durationInput.setEnabled(durationEnabled);
        oneTimeRearmButton.setEnabled(durationEnabled);
        durationRow.setAlpha(durationEnabled ? 1.0f : 0.45f);
        oneTimeRearmButton.setAlpha(durationEnabled ? 1.0f : 0.45f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showMenu$lambda$34(ScreenCaptureService this$0, Switch delaySwitch, Button minus, Button plus, EditText durationInput, Button oneTimeRearmButton, LinearLayout durationRow, CompoundButton compoundButton, boolean checked) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(delaySwitch, "$delaySwitch");
        Intrinsics.checkNotNullParameter(minus, "$minus");
        Intrinsics.checkNotNullParameter(plus, "$plus");
        Intrinsics.checkNotNullParameter(durationInput, "$durationInput");
        Intrinsics.checkNotNullParameter(oneTimeRearmButton, "$oneTimeRearmButton");
        Intrinsics.checkNotNullParameter(durationRow, "$durationRow");
        this$0.whiteRearmEnabled = checked;
        SharedPreferences sharedPreferences = this$0.preferences;
        if (sharedPreferences == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences = null;
        }
        sharedPreferences.edit().putBoolean(KEY_WHITE_REARM, checked).apply();
        showMenu$updateDelayControls(delaySwitch, this$0, minus, plus, durationInput, oneTimeRearmButton, durationRow);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showMenu$lambda$35(ScreenCaptureService this$0, Switch delaySwitch, Button minus, Button plus, EditText durationInput, Button oneTimeRearmButton, LinearLayout durationRow, CompoundButton compoundButton, boolean checked) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(delaySwitch, "$delaySwitch");
        Intrinsics.checkNotNullParameter(minus, "$minus");
        Intrinsics.checkNotNullParameter(plus, "$plus");
        Intrinsics.checkNotNullParameter(durationInput, "$durationInput");
        Intrinsics.checkNotNullParameter(oneTimeRearmButton, "$oneTimeRearmButton");
        Intrinsics.checkNotNullParameter(durationRow, "$durationRow");
        this$0.rearmDelayEnabled = checked;
        SharedPreferences sharedPreferences = this$0.preferences;
        if (sharedPreferences == null) {
            Intrinsics.throwUninitializedPropertyAccessException("preferences");
            sharedPreferences = null;
        }
        sharedPreferences.edit().putBoolean(KEY_REARM_DELAY_ENABLED, checked).apply();
        showMenu$updateDelayControls(delaySwitch, this$0, minus, plus, durationInput, oneTimeRearmButton, durationRow);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void closeMenu() {
        Object objM129constructorimpl;
        View panel = this.menuPanel;
        if (panel != null) {
            try {
                Result.Companion companion = Result.INSTANCE;
                ScreenCaptureService $this$closeMenu_u24lambda_u2439_u24lambda_u2438 = this;
                WindowManager windowManager = $this$closeMenu_u24lambda_u2439_u24lambda_u2438.windowManager;
                if (windowManager == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("windowManager");
                    windowManager = null;
                }
                windowManager.removeView(panel);
                objM129constructorimpl = Result.m129constructorimpl(Unit.INSTANCE);
            } catch (Throwable th) {
                Result.Companion companion2 = Result.INSTANCE;
                objM129constructorimpl = Result.m129constructorimpl(ResultKt.createFailure(th));
            }
            Result.m128boximpl(objM129constructorimpl);
        }
        this.menuPanel = null;
        this.menuPanelParams = null;
    }

    private final View completeStopCard(final Function0<Unit> action) {
        LinearLayout $this$completeStopCard_u24lambda_u2443 = new LinearLayout(this);
        $this$completeStopCard_u24lambda_u2443.setOrientation(1);
        $this$completeStopCard_u24lambda_u2443.setGravity(17);
        $this$completeStopCard_u24lambda_u2443.setPadding(m48dp(18), m48dp(14), m48dp(18), m48dp(14));
        $this$completeStopCard_u24lambda_u2443.setBackground(roundedBackground(Color.rgb(250, 226, 229), Color.rgb(211, LocationRequestCompat.QUALITY_LOW_POWER, 119), 18.0f));
        $this$completeStopCard_u24lambda_u2443.setClickable(true);
        $this$completeStopCard_u24lambda_u2443.setFocusable(true);
        $this$completeStopCard_u24lambda_u2443.setContentDescription("إيقاف تشغيل المراقبة وتعطيل النافذة المنبثقة");
        $this$completeStopCard_u24lambda_u2443.setOnClickListener(new View.OnClickListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda11
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ScreenCaptureService.completeStopCard$lambda$43$lambda$40(action, view);
            }
        });
        TextView $this$completeStopCard_u24lambda_u2443_u24lambda_u2441 = new TextView(this);
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2441.setText("إيقاف تشغيل المراقبة");
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2441.setTextSize(18.0f);
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2441.setTypeface(Typeface.DEFAULT_BOLD);
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2441.setGravity(17);
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2441.setTextColor(Color.rgb(120, 27, 38));
        $this$completeStopCard_u24lambda_u2443.addView($this$completeStopCard_u24lambda_u2443_u24lambda_u2441, matchWrap(-2, m48dp(3)));
        TextView $this$completeStopCard_u24lambda_u2443_u24lambda_u2442 = new TextView(this);
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2442.setText("وتعطيل النافذة المنبثقة");
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2442.setTextSize(14.0f);
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2442.setGravity(17);
        $this$completeStopCard_u24lambda_u2443_u24lambda_u2442.setTextColor(Color.rgb(153, 48, 61));
        $this$completeStopCard_u24lambda_u2443.addView($this$completeStopCard_u24lambda_u2443_u24lambda_u2442, matchWrap(-2, 0));
        return $this$completeStopCard_u24lambda_u2443;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void completeStopCard$lambda$43$lambda$40(Function0 action, View it) {
        Intrinsics.checkNotNullParameter(action, "$action");
        action.invoke();
    }

    private final Button menuActionButton(String textValue, final Function0<Unit> action, boolean danger) {
        int i;
        int i2;
        int i3;
        Button $this$menuActionButton_u24lambda_u2445 = new Button(this);
        $this$menuActionButton_u24lambda_u2445.setText(textValue);
        $this$menuActionButton_u24lambda_u2445.setTextSize(17.0f);
        $this$menuActionButton_u24lambda_u2445.setAllCaps(false);
        $this$menuActionButton_u24lambda_u2445.setTextColor(danger ? Color.rgb(120, 27, 38) : Color.rgb(28, 28, 34));
        if (danger) {
            i = 226;
            i2 = 229;
            i3 = 250;
        } else {
            i = 225;
            i2 = 227;
            i3 = 224;
        }
        $this$menuActionButton_u24lambda_u2445.setBackground(roundedBackground(Color.rgb(i3, i, i2), danger ? Color.rgb(211, LocationRequestCompat.QUALITY_LOW_POWER, 119) : Color.rgb(205, 205, 210), 14.0f));
        $this$menuActionButton_u24lambda_u2445.setOnClickListener(new View.OnClickListener() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda16
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ScreenCaptureService.menuActionButton$lambda$45$lambda$44(action, view);
            }
        });
        return $this$menuActionButton_u24lambda_u2445;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void menuActionButton$lambda$45$lambda$44(Function0 action, View it) {
        Intrinsics.checkNotNullParameter(action, "$action");
        action.invoke();
    }

    private final Button smallControlButton(String label) {
        Button $this$smallControlButton_u24lambda_u2446 = new Button(this);
        $this$smallControlButton_u24lambda_u2446.setText(label);
        $this$smallControlButton_u24lambda_u2446.setTextSize(23.0f);
        $this$smallControlButton_u24lambda_u2446.setTypeface(Typeface.DEFAULT_BOLD);
        $this$smallControlButton_u24lambda_u2446.setAllCaps(false);
        $this$smallControlButton_u24lambda_u2446.setTextColor(Color.rgb(28, 28, 34));
        $this$smallControlButton_u24lambda_u2446.setBackground(roundedBackground(Color.rgb(224, 225, 227), Color.rgb(205, 205, 210), 14.0f));
        return $this$smallControlButton_u24lambda_u2446;
    }

    private final LinearLayout.LayoutParams matchWrap(int height, int bottomMargin) {
        LinearLayout.LayoutParams $this$matchWrap_u24lambda_u2447 = new LinearLayout.LayoutParams(-1, height);
        $this$matchWrap_u24lambda_u2447.bottomMargin = bottomMargin;
        return $this$matchWrap_u24lambda_u2447;
    }

    private final void processImage(Image image) {
        long now = SystemClock.elapsedRealtime();
        WindowManager.LayoutParams params = this.sensorParams;
        if (params == null || this.screenWidth <= 0 || this.screenHeight <= 0) {
            return;
        }
        Rect crop = image.getCropRect();
        if (crop.width() > 0 && crop.height() > 0) {
            int screenCenterX = params.x + (this.sensorTouchSize / 2);
            int screenCenterY = params.y + (this.sensorTouchSize / 2);
            int centerX = RangesKt.coerceIn(MathKt.roundToInt(crop.left + ((screenCenterX * crop.width()) / this.screenWidth)), crop.left, crop.right - 1);
            int centerY = RangesKt.coerceIn(MathKt.roundToInt(crop.top + ((screenCenterY * crop.height()) / this.screenHeight)), crop.top, crop.bottom - 1);
            float screenRadius = this.sensorVisibleDiameter / 2.0f;
            float radiusX = Math.max(0.5f, (crop.width() * screenRadius) / this.screenWidth);
            float radiusY = Math.max(0.5f, (crop.height() * screenRadius) / this.screenHeight);
            ColorSample sample = sampleCircularRegion(image, centerX, centerY, radiusX, radiusY);
            if (sample == null) {
                return;
            }
            this.latestColorSample = sample;
            this.latestSampleAtMs = now;
            processOneTimeRearmOverride(sample, now);
            if (this.triggerState != TriggerState.ARMED || this.armedWhiteSample != sample) {
                updateTriggerEngine(sample, now);
            }
        }
    }

    private final ColorSample sampleCircularRegion(Image image, int centerX, int centerY, float radiusX, float radiusY) {
        Rect crop;
        Image.Plane plane;
        int offset;
        int i = centerX;
        int i2 = centerY;
        Rect crop2 = image.getCropRect();
        boolean z = false;
        if (!(i < crop2.right && crop2.left <= i)) {
            return null;
        }
        int i3 = crop2.top;
        if (i2 < crop2.bottom && i3 <= i2) {
            z = true;
        }
        if (!z) {
            return null;
        }
        Image.Plane[] planes = image.getPlanes();
        Intrinsics.checkNotNullExpressionValue(planes, "getPlanes(...)");
        Image.Plane plane2 = (Image.Plane) ArraysKt.firstOrNull(planes);
        if (plane2 == null) {
            return null;
        }
        ByteBuffer buffer = plane2.getBuffer();
        int pixelStride = plane2.getPixelStride();
        int rowStride = plane2.getRowStride();
        if (pixelStride >= 3 && rowStride > 0) {
            long greenTotal = 0;
            long blueTotal = 0;
            long luminanceTotal = 0;
            long chromaTotal = 0;
            int whiteCount = 0;
            int count = 0;
            int bufferBaseOffset = buffer.position();
            long redTotal = 0;
            int minX = RangesKt.coerceAtLeast((int) Math.floor(i - radiusX), crop2.left);
            int maxX = RangesKt.coerceAtMost((int) Math.ceil(i + radiusX), crop2.right - 1);
            int minY = RangesKt.coerceAtLeast((int) Math.floor(i2 - radiusY), crop2.top);
            int maxY = RangesKt.coerceAtMost((int) Math.ceil(i2 + radiusY), crop2.bottom - 1);
            int y = minY;
            if (y <= maxY) {
                while (true) {
                    float normalizedY = (y - i2) / radiusY;
                    int x = minX;
                    if (x <= maxX) {
                        while (true) {
                            crop = crop2;
                            float normalizedX = (x - i) / radiusX;
                            if ((normalizedX * normalizedX) + (normalizedY * normalizedY) > 1.0f || (offset = bufferBaseOffset + (y * rowStride) + (x * pixelStride)) < 0) {
                                plane = plane2;
                            } else {
                                plane = plane2;
                                if (offset + 2 < buffer.limit()) {
                                    int red = buffer.get(offset) & UByte.MAX_VALUE;
                                    int green = buffer.get(offset + 1) & UByte.MAX_VALUE;
                                    int minX2 = offset + 2;
                                    int blue = buffer.get(minX2) & UByte.MAX_VALUE;
                                    int minimumChannel = Math.min(red, Math.min(green, blue));
                                    int maximumChannel = Math.max(red, Math.max(green, blue));
                                    int rowStride2 = maximumChannel - minimumChannel;
                                    int maximumChannel2 = green * 183;
                                    int luminance = (((red * 54) + maximumChannel2) + (blue * 19)) >> 8;
                                    redTotal += (long) red;
                                    greenTotal += (long) green;
                                    blueTotal += (long) blue;
                                    luminanceTotal += (long) luminance;
                                    chromaTotal += (long) rowStride2;
                                    if (luminance >= 195 && minimumChannel >= WHITE_PIXEL_MIN_CHANNEL && rowStride2 <= WHITE_PIXEL_MAX_CHROMA) {
                                        whiteCount++;
                                    }
                                    count++;
                                }
                            }
                            if (x == maxX) {
                                break;
                            }
                            x++;
                            i = centerX;
                            crop2 = crop;
                            plane2 = plane;
                            minX = minX;
                            buffer = buffer;
                            rowStride = rowStride;
                            minY = minY;
                            normalizedY = normalizedY;
                        }
                    } else {
                        crop = crop2;
                        plane = plane2;
                        minX = minX;
                        buffer = buffer;
                        rowStride = rowStride;
                        minY = minY;
                    }
                    if (y == maxY) {
                        break;
                    }
                    y++;
                    i = centerX;
                    i2 = centerY;
                    crop2 = crop;
                    plane2 = plane;
                    minX = minX;
                    buffer = buffer;
                    rowStride = rowStride;
                    minY = minY;
                }
            }
            int whiteCount2 = whiteCount;
            int count2 = count;
            long redTotal2 = redTotal;
            if (count2 < 3) {
                return null;
            }
            return new ColorSample((int) (redTotal2 / ((long) count2)), (int) (greenTotal / ((long) count2)), (int) (blueTotal / ((long) count2)), whiteCount2 / count2, (int) (luminanceTotal / ((long) count2)), (int) (chromaTotal / ((long) count2)));
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void requestOneTimeRearmOverride() {
        if (!this.whiteRearmEnabled || !this.rearmDelayEnabled) {
            Toast.makeText(this, "فعّل تأخير إعادة التسليح أولًا", 0).show();
            return;
        }
        closeMenu();
        Handler handler = this.captureHandler;
        if (handler == null) {
            Toast.makeText(this, "المراقبة غير جاهزة", 0).show();
        } else {
            handler.post(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda14
                /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
                @Override // java.lang.Runnable
                public final void run() {
                    ScreenCaptureService.requestOneTimeRearmOverride$lambda$48(this.f$0);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void requestOneTimeRearmOverride$lambda$48(ScreenCaptureService this$0) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (this$0.triggerState != TriggerState.WAITING_REARM) {
            this$0.showRearmMessage("لا يوجد تأخير جارٍ لتجاوزه");
        } else {
            this$0.manualRearmRequestedAtMs = SystemClock.elapsedRealtime();
            this$0.manualRearmWhiteFrames = 0;
        }
    }

    private final void processOneTimeRearmOverride(ColorSample sample, long now) {
        int i;
        long requestedAt = this.manualRearmRequestedAtMs;
        if (requestedAt == 0) {
            return;
        }
        if (this.triggerState != TriggerState.WAITING_REARM || !this.whiteRearmEnabled || !this.rearmDelayEnabled) {
            clearOneTimeRearmRequest();
            return;
        }
        if (now - requestedAt < MANUAL_REARM_MENU_SETTLE_MS) {
            return;
        }
        if (sample.isArmingWhite()) {
            i = this.manualRearmWhiteFrames + 1;
        } else {
            i = 0;
        }
        this.manualRearmWhiteFrames = i;
        if (this.manualRearmWhiteFrames >= 2) {
            clearOneTimeRearmRequest();
            arm(sample);
            showRearmMessage("تم تفعيل التسليح وتجاوز التأخير لهذه المرة");
        } else if (now - requestedAt >= MANUAL_REARM_TIMEOUT_MS) {
            clearOneTimeRearmRequest();
            showRearmMessage("لم يتم التسليح: اللون الأبيض غير موجود");
        }
    }

    private final void clearOneTimeRearmRequest() {
        this.manualRearmRequestedAtMs = 0L;
        this.manualRearmWhiteFrames = 0;
    }

    private final void showRearmMessage(final String message) {
        this.mainHandler.post(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda18
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // java.lang.Runnable
            public final void run() {
                ScreenCaptureService.showRearmMessage$lambda$49(this.f$0, message);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showRearmMessage$lambda$49(ScreenCaptureService this$0, String message) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(message, "$message");
        Toast.makeText(this$0, message, 0).show();
    }

    private final void updateTriggerEngine(ColorSample sample, long now) {
        boolean z = true;
        switch (WhenMappings.$EnumSwitchMapping$0[this.triggerState.ordinal()]) {
            case 1:
                this.whiteFrames = sample.isArmingWhite() ? this.whiteFrames + 1 : 0;
                if (this.whiteFrames >= 3) {
                    arm(sample);
                }
                break;
            case 2:
                ColorSample reference = this.armedWhiteSample;
                boolean meaningfulChange = reference != null && sample.isMeaningfulChangeFrom(reference);
                boolean stillWhite = sample.isHoldingWhite() && !meaningfulChange;
                if (stillWhite) {
                    this.changedFrames = 0;
                } else {
                    this.changedFrames = meaningfulChange ? this.changedFrames + 1 : 0;
                    if (this.changedFrames >= 1) {
                        fire(now);
                    }
                }
                break;
            case 3:
                this.whiteFrames = sample.isArmingWhite() ? this.whiteFrames + 1 : 0;
                boolean whiteReady = this.whiteRearmEnabled && this.whiteFrames >= 3;
                if (this.rearmDelayEnabled && now - this.firedAtMs < ((long) this.rearmSeconds) * 1000) {
                    z = false;
                }
                boolean delayReady = z;
                if (whiteReady && delayReady) {
                    arm(sample);
                    break;
                }
                break;
        }
    }

    private final void arm(ColorSample sample) {
        this.triggerState = TriggerState.ARMED;
        this.armedWhiteSample = sample;
        this.whiteFrames = 0;
        this.changedFrames = 0;
        updateSensorStatus(SensorStatus.ARMED);
    }

    private final void fire(long now) {
        this.triggerState = TriggerState.WAITING_REARM;
        clearOneTimeRearmRequest();
        this.armedWhiteSample = null;
        this.firedAtMs = now;
        this.whiteFrames = 0;
        this.changedFrames = 0;
        updateSensorStatus(SensorStatus.FIRED);
        this.mainHandler.post(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda2
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // java.lang.Runnable
            public final void run() {
                ScreenCaptureService.fire$lambda$50(this.f$0);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void fire$lambda$50(ScreenCaptureService this$0) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.performTargetTap();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void resetTriggerForSensorMove() {
        this.triggerState = TriggerState.WAITING_FOR_WHITE;
        clearOneTimeRearmRequest();
        this.armedWhiteSample = null;
        this.whiteFrames = 0;
        this.changedFrames = 0;
        updateSensorStatus(SensorStatus.WAITING);
    }

    private final void updateSensorStatus(final SensorStatus status) {
        this.mainHandler.post(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda9
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // java.lang.Runnable
            public final void run() {
                ScreenCaptureService.updateSensorStatus$lambda$51(this.f$0, status);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void updateSensorStatus$lambda$51(ScreenCaptureService this$0, SensorStatus status) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(status, "$status");
        SensorOverlayView sensorOverlayView = this$0.sensorView;
        if (sensorOverlayView != null) {
            sensorOverlayView.setStatus(status);
        }
    }

    private final void performTargetTap() {
        final WindowManager.LayoutParams params;
        final TargetOverlayView view = this.targetView;
        if (view == null || (params = this.targetParams) == null) {
            return;
        }
        final float tapX = params.x + (this.targetTouchSize / 2.0f);
        final float tapY = params.y + (this.targetTouchSize / 2.0f);
        final int originalFlags = params.flags;
        params.flags = originalFlags | 16;
        try {
            Result.Companion companion = Result.INSTANCE;
            ScreenCaptureService $this$performTargetTap_u24lambda_u2452 = this;
            WindowManager windowManager = $this$performTargetTap_u24lambda_u2452.windowManager;
            if (windowManager == null) {
                Intrinsics.throwUninitializedPropertyAccessException("windowManager");
                windowManager = null;
            }
            windowManager.updateViewLayout(view, params);
            Result.m129constructorimpl(Unit.INSTANCE);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            Result.m129constructorimpl(ResultKt.createFailure(th));
        }
        view.setAlpha(0.35f);
        this.mainHandler.postAtFrontOfQueue(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda17
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // java.lang.Runnable
            public final void run() {
                ScreenCaptureService.performTargetTap$lambda$55(tapX, tapY, this, params, originalFlags, view);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void performTargetTap$lambda$55(float $tapX, float $tapY, final ScreenCaptureService this$0, final WindowManager.LayoutParams params, final int $originalFlags, final TargetOverlayView view) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(params, "$params");
        Intrinsics.checkNotNullParameter(view, "$view");
        TriggerAccessibilityService.INSTANCE.tap($tapX, $tapY, TAP_DURATION_MS);
        this$0.mainHandler.postDelayed(new Runnable() { // from class: com.pixeltrigger.app.ScreenCaptureService$$ExternalSyntheticLambda12
            /* JADX DEBUG: Don't trust debug lines info. Lines numbers was adjusted: min line is 0 */
            @Override // java.lang.Runnable
            public final void run() {
                ScreenCaptureService.performTargetTap$lambda$55$lambda$54(params, $originalFlags, view, this$0);
            }
        }, TARGET_RESTORE_DELAY_MS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void performTargetTap$lambda$55$lambda$54(WindowManager.LayoutParams params, int $originalFlags, TargetOverlayView view, ScreenCaptureService this$0) {
        Intrinsics.checkNotNullParameter(params, "$params");
        Intrinsics.checkNotNullParameter(view, "$view");
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        params.flags = $originalFlags;
        view.setAlpha(1.0f);
        try {
            Result.Companion companion = Result.INSTANCE;
            WindowManager windowManager = this$0.windowManager;
            if (windowManager == null) {
                Intrinsics.throwUninitializedPropertyAccessException("windowManager");
                windowManager = null;
            }
            windowManager.updateViewLayout(view, params);
            Result.m129constructorimpl(Unit.INSTANCE);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            Result.m129constructorimpl(ResultKt.createFailure(th));
        }
    }

    private final Notification buildNotification() {
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, new Intent(this, (Class<?>) MainActivity.class), 201326592);
        Intent $this$buildNotification_u24lambda_u2456 = new Intent(this, (Class<?>) ScreenCaptureService.class);
        $this$buildNotification_u24lambda_u2456.setAction(ACTION_STOP);
        Unit unit = Unit.INSTANCE;
        PendingIntent stopIntent = PendingIntent.getService(this, 1, $this$buildNotification_u24lambda_u2456, 201326592);
        Notification notificationBuild = new NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("PixelTrigger يعمل").setContentText("مراقبة نقطة اللون مفعّلة").setContentIntent(openIntent).addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopIntent).setOngoing(true).setPriority(-1).build();
        Intrinsics.checkNotNullExpressionValue(notificationBuild, "build(...)");
        return notificationBuild;
    }

    private final void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "مراقبة PixelTrigger", 2);
        ((NotificationManager) getSystemService(NotificationManager.class)).createNotificationChannel(channel);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void shutdownCompletely() {
        this.triggerState = TriggerState.WAITING_FOR_WHITE;
        clearOneTimeRearmRequest();
        this.armedWhiteSample = null;
        this.whiteFrames = 0;
        this.changedFrames = 0;
        closeMenu();
        Iterable $this$forEach$iv = CollectionsKt.listOfNotNull((Object[]) new View[]{this.sensorView, this.targetView, this.menuButton});
        for (Object element$iv : $this$forEach$iv) {
            View view = (View) element$iv;
            try {
                Result.Companion companion = Result.INSTANCE;
                ScreenCaptureService $this$shutdownCompletely_u24lambda_u2458_u24lambda_u2457 = this;
                WindowManager windowManager = $this$shutdownCompletely_u24lambda_u2458_u24lambda_u2457.windowManager;
                if (windowManager == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("windowManager");
                    windowManager = null;
                }
                windowManager.removeViewImmediate(view);
                Result.m129constructorimpl(Unit.INSTANCE);
            } catch (Throwable th) {
                Result.Companion companion2 = Result.INSTANCE;
                Result.m129constructorimpl(ResultKt.createFailure(th));
            }
        }
        this.sensorView = null;
        this.targetView = null;
        this.menuButton = null;
        this.sensorParams = null;
        this.targetParams = null;
        this.menuButtonParams = null;
        this.circlesVisible = false;
        stopForeground(1);
        stopSelf();
    }

    @Override // android.app.Service
    public void onDestroy() {
        Iterator it;
        this.mainHandler.removeCallbacks(this.refreshDisplayRunnable);
        try {
            Result.Companion companion = Result.INSTANCE;
            ScreenCaptureService $this$onDestroy_u24lambda_u2459 = this;
            Object systemService = $this$onDestroy_u24lambda_u2459.getSystemService("display");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.hardware.display.DisplayManager");
            ((DisplayManager) systemService).unregisterDisplayListener($this$onDestroy_u24lambda_u2459.displayListener);
            Result.m129constructorimpl(Unit.INSTANCE);
            while (true) {
                WindowManager windowManager = null;
                if (!it.hasNext()) {
                    break;
                }
                Object element$iv = it.next();
                View view = (View) element$iv;
                try {
                    Result.Companion companion2 = Result.INSTANCE;
                    ScreenCaptureService $this$onDestroy_u24lambda_u2461_u24lambda_u2460 = this;
                    WindowManager windowManager2 = $this$onDestroy_u24lambda_u2461_u24lambda_u2460.windowManager;
                    if (windowManager2 == null) {
                        Intrinsics.throwUninitializedPropertyAccessException("windowManager");
                    } else {
                        windowManager = windowManager2;
                    }
                    windowManager.removeView(view);
                    Result.m129constructorimpl(Unit.INSTANCE);
                } catch (Throwable th) {
                    Result.Companion companion3 = Result.INSTANCE;
                    Result.m129constructorimpl(ResultKt.createFailure(th));
                }
            }
        } catch (Throwable th2) {
            Result.Companion companion4 = Result.INSTANCE;
            Result.m129constructorimpl(ResultKt.createFailure(th2));
        }
        closeMenu();
        Iterable $this$forEach$iv = CollectionsKt.listOfNotNull((Object[]) new View[]{this.sensorView, this.targetView, this.menuButton});
        it = $this$forEach$iv.iterator();
        this.sensorView = null;
        this.targetView = null;
        this.menuButton = null;
        ImageReader imageReader = this.imageReader;
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
        }
        ImageReader imageReader2 = this.imageReader;
        if (imageReader2 != null) {
            imageReader2.close();
        }
        this.imageReader = null;
        VirtualDisplay virtualDisplay = this.virtualDisplay;
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        this.virtualDisplay = null;
        MediaProjection mediaProjection = this.mediaProjection;
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        this.mediaProjection = null;
        HandlerThread handlerThread = this.captureThread;
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        this.captureThread = null;
        this.captureHandler = null;
        PowerManager.WakeLock it2 = this.wakeLock;
        if (it2 != null) {
            if (!it2.isHeld()) {
                it2 = null;
            }
            if (it2 != null) {
                it2.release();
            }
        }
        this.wakeLock = null;
        stopForeground(1);
        super.onDestroy();
    }

    private final GradientDrawable roundedBackground(int fill, int stroke, float radiusDp) {
        GradientDrawable $this$roundedBackground_u24lambda_u2463 = new GradientDrawable();
        $this$roundedBackground_u24lambda_u2463.setShape(0);
        $this$roundedBackground_u24lambda_u2463.setColor(fill);
        $this$roundedBackground_u24lambda_u2463.setCornerRadius(m48dp((int) radiusDp));
        $this$roundedBackground_u24lambda_u2463.setStroke(m48dp(1), stroke);
        return $this$roundedBackground_u24lambda_u2463;
    }

    /* JADX WARN: Code duplicated, block: B:18:0x003f  */
    private final int mmToPx(float mm) {
        boolean z;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float fallbackDpi = metrics.densityDpi;
        Float fValueOf = Float.valueOf(metrics.xdpi);
        float it = fValueOf.floatValue();
        boolean z2 = false;
        if ((Float.isInfinite(it) || Float.isNaN(it)) ? false : true) {
            if (100.0f <= it && it <= 1000.0f) {
                z = true;
            } else {
                z = false;
            }
        } else {
            z = false;
        }
        if (!z) {
            fValueOf = null;
        }
        float xDpi = fValueOf != null ? fValueOf.floatValue() : fallbackDpi;
        Float fValueOf2 = Float.valueOf(metrics.ydpi);
        float it2 = fValueOf2.floatValue();
        if ((Float.isInfinite(it2) || Float.isNaN(it2)) ? false : true) {
            if (100.0f <= it2 && it2 <= 1000.0f) {
                z2 = true;
            }
        }
        Float f = z2 ? fValueOf2 : null;
        float yDpi = f != null ? f.floatValue() : fallbackDpi;
        float physicalDpi = (xDpi + yDpi) / 2.0f;
        return RangesKt.coerceAtLeast(MathKt.roundToInt((mm / 25.4f) * physicalDpi), 1);
    }

    /* JADX INFO: renamed from: dp */
    private final int m48dp(int value) {
        return MathKt.roundToInt(value * getResources().getDisplayMetrics().density);
    }
}
