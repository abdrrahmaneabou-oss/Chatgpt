package com.pixeltrigger.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import java.util.Collection;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.scheduling.WorkQueueKt;

/* JADX INFO: compiled from: MainActivity.kt */
/* JADX INFO: loaded from: classes3.dex */
@Metadata(d1 = {"\u0000~\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J6\u0010\u0013\u001a\u00020\u00042\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00172\u0006\u0010\u0019\u001a\u00020\u00172\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001c0\u001bH\u0002J\b\u0010\u001d\u001a\u00020\u001cH\u0002J\b\u0010\u001e\u001a\u00020\u001fH\u0002J\u0010\u0010 \u001a\u00020!2\u0006\u0010\"\u001a\u00020!H\u0002J\b\u0010#\u001a\u00020$H\u0002J\u0012\u0010%\u001a\u00020\u001c2\b\u0010&\u001a\u0004\u0018\u00010'H\u0014J\b\u0010(\u001a\u00020\u001cH\u0014J\b\u0010)\u001a\u00020\u001cH\u0002J\b\u0010*\u001a\u00020\u001cH\u0002J\b\u0010+\u001a\u00020\u001cH\u0002J \u0010,\u001a\u00020-2\u0006\u0010.\u001a\u00020!2\u0006\u0010/\u001a\u00020!2\u0006\u00100\u001a\u000201H\u0002J\u0018\u00102\u001a\u00020\u001c2\u0006\u00103\u001a\u00020\u00042\u0006\u00104\u001a\u000205H\u0002J\b\u00106\u001a\u00020\u001cH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004¢\u0006\u0002\n\u0000R\u001b\u0010\n\u001a\u00020\u000b8BX\u0082\u0084\u0002¢\u0006\f\n\u0004\b\u000e\u0010\u000f\u001a\u0004\b\f\u0010\rR\u000e\u0010\u0010\u001a\u00020\u0004X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082.¢\u0006\u0002\n\u0000¨\u00067"}, d2 = {"Lcom/pixeltrigger/app/MainActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "accessibilityStatus", "Landroid/widget/TextView;", "batteryStatus", "overlayStatus", "projectionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "Landroid/content/Intent;", "projectionManager", "Landroid/media/projection/MediaProjectionManager;", "getProjectionManager", "()Landroid/media/projection/MediaProjectionManager;", "projectionManager$delegate", "Lkotlin/Lazy;", "readinessLabel", "startButton", "Landroid/widget/Button;", "addRequirement", "parent", "Landroid/widget/LinearLayout;", "number", "", "title", "subtitle", "onClick", "Lkotlin/Function0;", "", "beginMonitoringOrOpenMissingSetting", "buildContent", "Landroid/view/View;", "dp", "", "value", "matchWrap", "Landroid/widget/LinearLayout$LayoutParams;", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onResume", "requestBatteryExemption", "requestNotificationPermissionIfNeeded", "requestOverlayPermission", "roundedBackground", "Landroid/graphics/drawable/GradientDrawable;", "fill", "stroke", "radiusDp", "", "setStatus", "view", "ready", "", "updateReadiness", "app_debug"}, m32k = 1, mv = {1, 9, 0}, xi = 48)
public final class MainActivity extends AppCompatActivity {
    private TextView accessibilityStatus;
    private TextView batteryStatus;
    private TextView overlayStatus;
    private TextView readinessLabel;
    private Button startButton;

    /* JADX INFO: renamed from: projectionManager$delegate, reason: from kotlin metadata */
    private final Lazy projectionManager = LazyKt.lazy(new Function0<MediaProjectionManager>() { // from class: com.pixeltrigger.app.MainActivity$projectionManager$2
        {
            super(0);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // kotlin.jvm.functions.Function0
        public final MediaProjectionManager invoke() {
            Object systemService = this.this$0.getSystemService("media_projection");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.media.projection.MediaProjectionManager");
            return (MediaProjectionManager) systemService;
        }
    });
    private final ActivityResultLauncher<Intent> projectionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback() { // from class: com.pixeltrigger.app.MainActivity$$ExternalSyntheticLambda0
        @Override // androidx.activity.result.ActivityResultCallback
        public final void onActivityResult(Object obj) {
            MainActivity.projectionLauncher$lambda$1(this.f$0, (ActivityResult) obj);
        }
    });

    private final MediaProjectionManager getProjectionManager() {
        return (MediaProjectionManager) this.projectionManager.getValue();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void projectionLauncher$lambda$1(MainActivity this$0, ActivityResult result) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(result, "result");
        Intent data = result.getData();
        if (result.getResultCode() == -1 && data != null) {
            Intent serviceIntent = new Intent(this$0, (Class<?>) ScreenCaptureService.class);
            serviceIntent.setAction(ScreenCaptureService.ACTION_START);
            serviceIntent.putExtra("result_code", result.getResultCode());
            serviceIntent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
            ContextCompat.startForegroundService(this$0, serviceIntent);
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(5, 9, 20));
        getWindow().setNavigationBarColor(Color.rgb(5, 9, 20));
        setContentView(buildContent());
        requestNotificationPermissionIfNeeded();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        updateReadiness();
    }

    private final View buildContent() {
        TextView textView;
        ScrollView $this$buildContent_u24lambda_u242 = new ScrollView(this);
        $this$buildContent_u24lambda_u242.setBackgroundColor(Color.rgb(5, 9, 20));
        $this$buildContent_u24lambda_u242.setLayoutDirection(1);
        LinearLayout $this$buildContent_u24lambda_u243 = new LinearLayout(this);
        $this$buildContent_u24lambda_u243.setOrientation(1);
        $this$buildContent_u24lambda_u243.setGravity(1);
        $this$buildContent_u24lambda_u243.setPadding(dp(24), dp(28), dp(24), dp(36));
        $this$buildContent_u24lambda_u242.addView($this$buildContent_u24lambda_u243);
        TextView $this$buildContent_u24lambda_u244 = new TextView(this);
        $this$buildContent_u24lambda_u244.setText("PixelTrigger");
        $this$buildContent_u24lambda_u244.setTextSize(34.0f);
        $this$buildContent_u24lambda_u244.setTextColor(-1);
        $this$buildContent_u24lambda_u244.setTypeface(Typeface.DEFAULT_BOLD);
        $this$buildContent_u24lambda_u244.setGravity(17);
        $this$buildContent_u24lambda_u243.addView($this$buildContent_u24lambda_u244, matchWrap());
        TextView $this$buildContent_u24lambda_u245 = new TextView(this);
        $this$buildContent_u24lambda_u245.setText("PRECISION AUTOMATION  •  v2.12");
        $this$buildContent_u24lambda_u245.setTextSize(13.0f);
        $this$buildContent_u24lambda_u245.setLetterSpacing(0.18f);
        $this$buildContent_u24lambda_u245.setTextColor(Color.rgb(132, 141, 169));
        $this$buildContent_u24lambda_u245.setGravity(17);
        LinearLayout.LayoutParams $this$buildContent_u24lambda_u246 = matchWrap();
        $this$buildContent_u24lambda_u246.bottomMargin = dp(32);
        Unit unit = Unit.INSTANCE;
        $this$buildContent_u24lambda_u243.addView($this$buildContent_u24lambda_u245, $this$buildContent_u24lambda_u246);
        LinearLayout $this$buildContent_u24lambda_u247 = new LinearLayout(this);
        $this$buildContent_u24lambda_u247.setOrientation(1);
        $this$buildContent_u24lambda_u247.setGravity(17);
        $this$buildContent_u24lambda_u247.setPadding(dp(22), dp(24), dp(22), dp(24));
        $this$buildContent_u24lambda_u247.setBackground(roundedBackground(Color.rgb(15, 17, 43), Color.rgb(93, 70, 157), 26.0f));
        LinearLayout.LayoutParams $this$buildContent_u24lambda_u248 = matchWrap();
        $this$buildContent_u24lambda_u248.bottomMargin = dp(28);
        Unit unit2 = Unit.INSTANCE;
        $this$buildContent_u24lambda_u243.addView($this$buildContent_u24lambda_u247, $this$buildContent_u24lambda_u248);
        TextView $this$buildContent_u24lambda_u249 = new TextView(this);
        $this$buildContent_u24lambda_u249.setText("جاهز لالتقاط التغيّر");
        $this$buildContent_u24lambda_u249.setTextSize(30.0f);
        $this$buildContent_u24lambda_u249.setTextColor(-1);
        $this$buildContent_u24lambda_u249.setTypeface(Typeface.DEFAULT_BOLD);
        $this$buildContent_u24lambda_u249.setGravity(17);
        LinearLayout.LayoutParams $this$buildContent_u24lambda_u2410 = matchWrap();
        $this$buildContent_u24lambda_u2410.bottomMargin = dp(12);
        Unit unit3 = Unit.INSTANCE;
        $this$buildContent_u24lambda_u247.addView($this$buildContent_u24lambda_u249, $this$buildContent_u24lambda_u2410);
        TextView $this$buildContent_u24lambda_u2411 = new TextView(this);
        $this$buildContent_u24lambda_u2411.setText("ضع دائرة الاستشعار فوق المنطقة البيضاء، ودائرة الهدف فوق موضع الضغطة.");
        $this$buildContent_u24lambda_u2411.setTextSize(17.0f);
        $this$buildContent_u24lambda_u2411.setTextColor(Color.rgb(184, 189, 211));
        $this$buildContent_u24lambda_u2411.setGravity(17);
        LinearLayout.LayoutParams $this$buildContent_u24lambda_u2412 = matchWrap();
        $this$buildContent_u24lambda_u2412.bottomMargin = dp(24);
        Unit unit4 = Unit.INSTANCE;
        $this$buildContent_u24lambda_u247.addView($this$buildContent_u24lambda_u2411, $this$buildContent_u24lambda_u2412);
        Button $this$buildContent_u24lambda_u2414 = new Button(this);
        $this$buildContent_u24lambda_u2414.setText("بدء المراقبة");
        $this$buildContent_u24lambda_u2414.setTextSize(18.0f);
        $this$buildContent_u24lambda_u2414.setTextColor(-1);
        $this$buildContent_u24lambda_u2414.setTypeface(Typeface.DEFAULT_BOLD);
        $this$buildContent_u24lambda_u2414.setAllCaps(false);
        $this$buildContent_u24lambda_u2414.setBackground(roundedBackground(Color.rgb(112, 76, 255), Color.rgb(166, 145, 255), 22.0f));
        $this$buildContent_u24lambda_u2414.setOnClickListener(new View.OnClickListener() { // from class: com.pixeltrigger.app.MainActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.buildContent$lambda$14$lambda$13(this.f$0, view);
            }
        });
        this.startButton = $this$buildContent_u24lambda_u2414;
        Button button = this.startButton;
        if (button == null) {
            Intrinsics.throwUninitializedPropertyAccessException("startButton");
            button = null;
        }
        $this$buildContent_u24lambda_u247.addView(button, new LinearLayout.LayoutParams(-1, dp(62)));
        LinearLayout $this$buildContent_u24lambda_u2415 = new LinearLayout(this);
        $this$buildContent_u24lambda_u2415.setOrientation(0);
        $this$buildContent_u24lambda_u2415.setGravity(16);
        LinearLayout.LayoutParams $this$buildContent_u24lambda_u2416 = matchWrap();
        $this$buildContent_u24lambda_u2416.bottomMargin = dp(14);
        Unit unit5 = Unit.INSTANCE;
        $this$buildContent_u24lambda_u243.addView($this$buildContent_u24lambda_u2415, $this$buildContent_u24lambda_u2416);
        TextView $this$buildContent_u24lambda_u2417 = new TextView(this);
        $this$buildContent_u24lambda_u2417.setText("0 / 3 جاهز");
        $this$buildContent_u24lambda_u2417.setTextSize(16.0f);
        $this$buildContent_u24lambda_u2417.setTextColor(Color.rgb(196, 178, 255));
        $this$buildContent_u24lambda_u2417.setGravity(GravityCompat.START);
        $this$buildContent_u24lambda_u2417.setBackground(roundedBackground(Color.rgb(30, 25, 57), Color.rgb(94, 70, 162), 20.0f));
        $this$buildContent_u24lambda_u2417.setPadding(dp(16), dp(9), dp(16), dp(9));
        this.readinessLabel = $this$buildContent_u24lambda_u2417;
        TextView textView2 = this.readinessLabel;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("readinessLabel");
            textView = null;
        } else {
            textView = textView2;
        }
        $this$buildContent_u24lambda_u2415.addView(textView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView $this$buildContent_u24lambda_u2418 = new TextView(this);
        $this$buildContent_u24lambda_u2418.setText("جاهزية النظام");
        $this$buildContent_u24lambda_u2418.setTextSize(25.0f);
        $this$buildContent_u24lambda_u2418.setTextColor(-1);
        $this$buildContent_u24lambda_u2418.setTypeface(Typeface.DEFAULT_BOLD);
        $this$buildContent_u24lambda_u2418.setGravity(GravityCompat.END);
        $this$buildContent_u24lambda_u2415.addView($this$buildContent_u24lambda_u2418, new LinearLayout.LayoutParams(0, -2, 1.0f));
        this.overlayStatus = addRequirement($this$buildContent_u24lambda_u243, "01", "الظهور فوق التطبيقات", "لعرض نقطتي الاستشعار والهدف", new Function0<Unit>() { // from class: com.pixeltrigger.app.MainActivity.buildContent.13
            {
                super(0);
            }

            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                MainActivity.this.requestOverlayPermission();
            }
        });
        this.accessibilityStatus = addRequirement($this$buildContent_u24lambda_u243, "02", "خدمة تنفيذ الضغطة", "لتنفيذ الأمر لحظة اكتشاف التغيّر", new Function0<Unit>() { // from class: com.pixeltrigger.app.MainActivity.buildContent.14
            {
                super(0);
            }

            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                MainActivity.this.startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
            }
        });
        this.batteryStatus = addRequirement($this$buildContent_u24lambda_u243, "03", "عمل غير مقيّد", "لاستمرار الجلسة لساعات بثبات", new Function0<Unit>() { // from class: com.pixeltrigger.app.MainActivity.buildContent.15
            {
                super(0);
            }

            @Override // kotlin.jvm.functions.Function0
            public /* bridge */ /* synthetic */ Unit invoke() {
                invoke2();
                return Unit.INSTANCE;
            }

            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2() {
                MainActivity.this.requestBatteryExemption();
            }
        });
        TextView $this$buildContent_u24lambda_u2419 = new TextView(this);
        $this$buildContent_u24lambda_u2419.setText("تتطلب مراقبة الشاشة موافقتك من نافذة Android الرسمية عند كل جلسة جديدة.");
        $this$buildContent_u24lambda_u2419.setTextSize(13.0f);
        $this$buildContent_u24lambda_u2419.setTextColor(Color.rgb(112, 121, 148));
        $this$buildContent_u24lambda_u2419.setGravity(17);
        LinearLayout.LayoutParams $this$buildContent_u24lambda_u2420 = matchWrap();
        $this$buildContent_u24lambda_u2420.topMargin = dp(18);
        Unit unit6 = Unit.INSTANCE;
        $this$buildContent_u24lambda_u243.addView($this$buildContent_u24lambda_u2419, $this$buildContent_u24lambda_u2420);
        return $this$buildContent_u24lambda_u242;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void buildContent$lambda$14$lambda$13(MainActivity this$0, View it) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        this$0.beginMonitoringOrOpenMissingSetting();
    }

    private final TextView addRequirement(LinearLayout parent, String number, String title, String subtitle, final Function0<Unit> onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(0);
        card.setGravity(16);
        card.setPadding(dp(14), dp(16), dp(14), dp(16));
        card.setBackground(roundedBackground(Color.rgb(17, 25, 40), Color.rgb(42, 55, 78), 22.0f));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() { // from class: com.pixeltrigger.app.MainActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.addRequirement$lambda$22$lambda$21(onClick, view);
            }
        });
        LinearLayout.LayoutParams $this$addRequirement_u24lambda_u2423 = matchWrap();
        $this$addRequirement_u24lambda_u2423.bottomMargin = dp(12);
        Unit unit = Unit.INSTANCE;
        parent.addView(card, $this$addRequirement_u24lambda_u2423);
        TextView $this$addRequirement_u24lambda_u2424 = new TextView(this);
        $this$addRequirement_u24lambda_u2424.setText(number);
        $this$addRequirement_u24lambda_u2424.setTextSize(17.0f);
        $this$addRequirement_u24lambda_u2424.setTextColor(Color.rgb(196, 178, 255));
        $this$addRequirement_u24lambda_u2424.setGravity(17);
        $this$addRequirement_u24lambda_u2424.setTypeface(Typeface.DEFAULT_BOLD);
        $this$addRequirement_u24lambda_u2424.setBackground(roundedBackground(Color.rgb(28, 25, 55), Color.rgb(94, 70, 162), 18.0f));
        card.addView($this$addRequirement_u24lambda_u2424, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(1);
        texts.setGravity(GravityCompat.END);
        texts.setPadding(dp(14), 0, dp(14), 0);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView $this$addRequirement_u24lambda_u2426 = new TextView(this);
        $this$addRequirement_u24lambda_u2426.setText(title);
        $this$addRequirement_u24lambda_u2426.setTextSize(20.0f);
        $this$addRequirement_u24lambda_u2426.setTextColor(-1);
        $this$addRequirement_u24lambda_u2426.setTypeface(Typeface.DEFAULT_BOLD);
        $this$addRequirement_u24lambda_u2426.setGravity(GravityCompat.END);
        texts.addView($this$addRequirement_u24lambda_u2426, matchWrap());
        TextView $this$addRequirement_u24lambda_u2427 = new TextView(this);
        $this$addRequirement_u24lambda_u2427.setText(subtitle);
        $this$addRequirement_u24lambda_u2427.setTextSize(14.0f);
        $this$addRequirement_u24lambda_u2427.setTextColor(Color.rgb(WorkQueueKt.MASK, 138, 165));
        $this$addRequirement_u24lambda_u2427.setGravity(GravityCompat.END);
        texts.addView($this$addRequirement_u24lambda_u2427, matchWrap());
        TextView status = new TextView(this);
        status.setTextSize(15.0f);
        status.setGravity(17);
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.addView(status, new LinearLayout.LayoutParams(dp(78), -2));
        return status;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void addRequirement$lambda$22$lambda$21(Function0 onClick, View it) {
        Intrinsics.checkNotNullParameter(onClick, "$onClick");
        onClick.invoke();
    }

    private final void updateReadiness() {
        boolean overlayReady = Settings.canDrawOverlays(this);
        boolean accessibilityReady = TriggerAccessibilityService.INSTANCE.isEnabled(this);
        Object systemService = getSystemService("power");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.os.PowerManager");
        PowerManager powerManager = (PowerManager) systemService;
        boolean batteryReady = powerManager.isIgnoringBatteryOptimizations(getPackageName());
        TextView textView = this.overlayStatus;
        Button button = null;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("overlayStatus");
            textView = null;
        }
        setStatus(textView, overlayReady);
        TextView textView2 = this.accessibilityStatus;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("accessibilityStatus");
            textView2 = null;
        }
        setStatus(textView2, accessibilityReady);
        TextView textView3 = this.batteryStatus;
        if (textView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("batteryStatus");
            textView3 = null;
        }
        setStatus(textView3, batteryReady);
        int count$iv = 0;
        Iterable $this$count$iv = CollectionsKt.listOf((Object[]) new Boolean[]{Boolean.valueOf(overlayReady), Boolean.valueOf(accessibilityReady), Boolean.valueOf(batteryReady)});
        if (!($this$count$iv instanceof Collection) || !((Collection) $this$count$iv).isEmpty()) {
            count$iv = 0;
            for (Object element$iv : $this$count$iv) {
                boolean it = ((Boolean) element$iv).booleanValue();
                if (it && (count$iv = count$iv + 1) < 0) {
                    CollectionsKt.throwCountOverflow();
                }
            }
        }
        int readyCount = count$iv;
        TextView textView4 = this.readinessLabel;
        if (textView4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("readinessLabel");
            textView4 = null;
        }
        textView4.setText(readyCount + " / 3 جاهز");
        Button button2 = this.startButton;
        if (button2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("startButton");
        } else {
            button = button2;
        }
        button.setAlpha(readyCount == 3 ? 1.0f : 0.68f);
    }

    private final void setStatus(TextView view, boolean ready) {
        int i;
        int i2;
        int i3;
        int i4;
        int i5;
        int i6;
        int i7;
        int i8;
        int i9;
        view.setText(ready ? "جاهز" : "مطلوب");
        if (ready) {
            i = 229;
            i2 = 179;
            i3 = 82;
        } else {
            i = 187;
            i2 = 92;
            i3 = 255;
        }
        view.setTextColor(Color.rgb(i3, i, i2));
        if (ready) {
            i4 = 61;
            i5 = 49;
            i6 = 10;
        } else {
            i4 = 47;
            i5 = 20;
            i6 = 65;
        }
        int iRgb = Color.rgb(i6, i4, i5);
        if (ready) {
            i7 = 131;
            i8 = 97;
            i9 = 23;
        } else {
            i7 = 98;
            i8 = 31;
            i9 = 145;
        }
        view.setBackground(roundedBackground(iRgb, Color.rgb(i9, i7, i8), 18.0f));
    }

    private final void beginMonitoringOrOpenMissingSetting() {
        if (Settings.canDrawOverlays(this)) {
            if (!TriggerAccessibilityService.INSTANCE.isEnabled(this)) {
                startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
                return;
            }
            Object systemService = getSystemService("power");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.os.PowerManager");
            if (((PowerManager) systemService).isIgnoringBatteryOptimizations(getPackageName())) {
                ActivityResultLauncher<Intent> activityResultLauncher = this.projectionLauncher;
                Intent intentCreateScreenCaptureIntent = getProjectionManager().createScreenCaptureIntent();
                Intrinsics.checkNotNullExpressionValue(intentCreateScreenCaptureIntent, "createScreenCaptureIntent(...)");
                activityResultLauncher.launch(intentCreateScreenCaptureIntent);
                return;
            }
            requestBatteryExemption();
            return;
        }
        requestOverlayPermission();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void requestOverlayPermission() {
        startActivity(new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName())));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void requestBatteryExemption() {
        Object objM98constructorimpl;
        Intent intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            Result.Companion companion = Result.INSTANCE;
            MainActivity $this$requestBatteryExemption_u24lambda_u2431 = this;
            $this$requestBatteryExemption_u24lambda_u2431.startActivity(intent);
            objM98constructorimpl = Result.m98constructorimpl(Unit.INSTANCE);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            objM98constructorimpl = Result.m98constructorimpl(ResultKt.createFailure(th));
        }
        if (Result.m101exceptionOrNullimpl(objM98constructorimpl) != null) {
            startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
        }
    }

    private final void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != 0) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, 201);
        }
    }

    private final GradientDrawable roundedBackground(int fill, int stroke, float radiusDp) {
        GradientDrawable $this$roundedBackground_u24lambda_u2433 = new GradientDrawable();
        $this$roundedBackground_u24lambda_u2433.setShape(0);
        $this$roundedBackground_u24lambda_u2433.setColor(fill);
        $this$roundedBackground_u24lambda_u2433.setCornerRadius(dp((int) radiusDp));
        $this$roundedBackground_u24lambda_u2433.setStroke(dp(1), stroke);
        return $this$roundedBackground_u24lambda_u2433;
    }

    private final LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private final int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
