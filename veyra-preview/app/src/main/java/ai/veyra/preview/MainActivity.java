package ai.veyra.preview;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(17, 17, 15);
    private static final int SURFACE = Color.rgb(27, 26, 23);
    private static final int SURFACE_2 = Color.rgb(37, 35, 31);
    private static final int GOLD = Color.rgb(200, 168, 107);
    private static final int CREAM = Color.rgb(244, 239, 227);
    private static final int MUTED = Color.rgb(169, 164, 155);
    private static final int GREEN = Color.rgb(116, 180, 122);

    private String selectedStyle = "Textured Crop";
    private int selectedVariation = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        showHome();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), strokeColor);
        return d;
    }

    private LinearLayout page() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(26));
        root.setBackgroundColor(BG);
        root.setGravity(Gravity.TOP);
        return root;
    }

    private ScrollView scroll(LinearLayout child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(child, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private TextView text(String value, float size, int color, int gravity) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(gravity);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private TextView label(String value) {
        TextView t = text(value, 12, MUTED, Gravity.START);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private Button button(String value, boolean primary) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setLetterSpacing(0.08f);
        b.setTextColor(primary ? Color.BLACK : GOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(primary ? GOLD : Color.TRANSPARENT, 12, GOLD, primary ? 0 : 1));
        b.setMinHeight(dp(54));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private View spacer(int height) {
        Space v = new Space(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
    }

    private LinearLayout surface() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(SURFACE, 16, Color.rgb(52, 50, 45), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private void addTopBar(LinearLayout root, String title, Runnable backAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(12));

        if (backAction != null) {
            TextView back = text("‹", 36, CREAM, Gravity.CENTER);
            back.setPadding(dp(6), 0, dp(18), 0);
            back.setOnClickListener(v -> backAction.run());
            row.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));
        }

        TextView ttl = text(title, 20, CREAM, Gravity.CENTER);
        LinearLayout.LayoutParams ttlLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        row.addView(ttl, ttlLp);

        if (backAction != null) {
            Space right = new Space(this);
            row.addView(right, new LinearLayout.LayoutParams(dp(52), dp(48)));
        }

        root.addView(row);
    }

    private void showHome() {
        LinearLayout root = page();
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView monogram = text("V", 74, GOLD, Gravity.CENTER);
        monogram.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(monogram, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(106)));

        TextView brand = text("V E Y R A   A I", 25, GOLD, Gravity.CENTER);
        brand.setLetterSpacing(0.18f);
        root.addView(brand);
        root.addView(spacer(6));
        TextView sub = text("AI HAIR CONSULTATION", 12, GOLD, Gravity.CENTER);
        sub.setLetterSpacing(0.18f);
        root.addView(sub);

        root.addView(spacer(54));
        TextView hero1 = text("Discover", 34, CREAM, Gravity.CENTER);
        hero1.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(hero1);
        TextView hero2 = text("Your Best Look", 34, GOLD, Gravity.CENTER);
        hero2.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(hero2);
        root.addView(spacer(14));
        TextView copy = text("Preview modern hairstyles before the cut.\nDesigned for premium salon consultations.", 16, MUTED, Gravity.CENTER);
        root.addView(copy);

        root.addView(spacer(48));
        Button start = button("START HAIR CONSULTATION  →", true);
        start.setOnClickListener(v -> showCamera());
        root.addView(start);

        Button admin = button("ADMIN PREVIEW", false);
        admin.setOnClickListener(v -> showAdmin());
        root.addView(admin);

        root.addView(spacer(24));
        TextView preview = text("UI PREVIEW BUILD  •  CAMERA / AI BACKEND NOT CONNECTED", 10, MUTED, Gravity.CENTER);
        preview.setLetterSpacing(0.08f);
        root.addView(preview);

        setContentView(scroll(root));
    }

    private void showCamera() {
        LinearLayout root = page();
        addTopBar(root, "Take a Photo", this::showHome);
        root.addView(text("Position your face in the frame", 14, MUTED, Gravity.CENTER));
        root.addView(spacer(14));

        FaceGuideView guide = new FaceGuideView();
        guide.setBackground(rounded(Color.rgb(41, 39, 35), 18, Color.rgb(72, 67, 58), 1));
        root.addView(guide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(390)));

        LinearLayout tips = surface();
        tips.addView(text("☀  Good lighting", 14, CREAM, Gravity.START));
        tips.addView(spacer(8));
        tips.addView(text("◎  Face forward", 14, CREAM, Gravity.START));
        tips.addView(spacer(8));
        tips.addView(text("⊘  Remove hats or accessories", 14, CREAM, Gravity.START));
        root.addView(tips);

        Button capture = button("CAPTURE PREVIEW", true);
        capture.setOnClickListener(v -> showStyles());
        root.addView(capture);
        setContentView(scroll(root));
    }

    private void showStyles() {
        LinearLayout root = page();
        addTopBar(root, "Choose Your Style", this::showCamera);

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        for (String c : Arrays.asList("ALL", "FADE", "SHORT", "CLASSIC", "TEXTURED", "CURLY", "LONG")) {
            TextView chip = text(c, 11, c.equals("ALL") ? GOLD : MUTED, Gravity.CENTER);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));
            categories.addView(chip);
        }
        categoryScroll.addView(categories);
        root.addView(categoryScroll);
        root.addView(spacer(10));

        List<String> styles = Arrays.asList(
                "Textured Crop", "French Crop", "Quiff",
                "Mid Fade", "Slick Back", "Side Part",
                "Buzz Cut", "Curly Top", "Pompadour",
                "Low Fade", "Crew Cut", "Taper Fade");

        for (int i = 0; i < styles.size(); i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(3f);
            for (int j = 0; j < 3 && i + j < styles.size(); j++) {
                final String style = styles.get(i + j);
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(8), dp(16), dp(8), dp(12));
                card.setBackground(rounded(SURFACE, 13, Color.rgb(53, 50, 44), 1));

                TextView hairIcon = text(hairGlyph(style), 31, GOLD, Gravity.CENTER);
                card.addView(hairIcon, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
                TextView name = text(style, 12, CREAM, Gravity.CENTER);
                card.addView(name, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
                card.setOnClickListener(v -> {
                    selectedStyle = style;
                    showGenerating();
                });

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(116), 1f);
                cp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(card, cp);
            }
            root.addView(row);
        }

        root.addView(spacer(8));
        TextView hint = text("Tap any hairstyle to generate a preview", 12, MUTED, Gravity.CENTER);
        root.addView(hint);
        setContentView(scroll(root));
    }

    private String hairGlyph(String style) {
        if (style.contains("Buzz")) return "▰";
        if (style.contains("Curly")) return "◉◉";
        if (style.contains("Fade")) return "◢";
        if (style.contains("Pompadour") || style.contains("Quiff")) return "⌁";
        return "〰";
    }

    private void showGenerating() {
        LinearLayout root = page();
        addTopBar(root, "Creating Your Look", this::showStyles);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(spacer(55));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(dp(86), dp(86)));
        root.addView(spacer(24));
        TextView title = text("Creating your new look…", 28, CREAM, Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        root.addView(spacer(8));
        root.addView(text(selectedStyle, 17, GOLD, Gravity.CENTER));
        root.addView(spacer(28));

        LinearLayout stages = surface();
        stages.addView(text("✓  Photo ready", 14, GREEN, Gravity.START));
        stages.addView(spacer(12));
        stages.addView(text("✓  Face analyzed", 14, GREEN, Gravity.START));
        stages.addView(spacer(12));
        stages.addView(text("●  Applying hairstyle", 14, GOLD, Gravity.START));
        stages.addView(spacer(12));
        stages.addView(text("○  Finalizing variations", 14, MUTED, Gravity.START));
        root.addView(stages);
        root.addView(spacer(20));
        root.addView(text("Preview mode automatically advances to results.", 11, MUTED, Gravity.CENTER));

        setContentView(root);
        new Handler(Looper.getMainLooper()).postDelayed(this::showResults, 1800);
    }

    private void showResults() {
        LinearLayout root = page();
        addTopBar(root, "Your Results", this::showStyles);

        TextView style = text(selectedStyle, 14, GOLD, Gravity.CENTER);
        style.setLetterSpacing(0.08f);
        root.addView(style);
        root.addView(spacer(10));

        ResultPreviewView preview = new ResultPreviewView();
        preview.setBackground(rounded(Color.rgb(224, 219, 206), 18, Color.TRANSPARENT, 0));
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(405)));

        LinearLayout variants = new LinearLayout(this);
        variants.setOrientation(LinearLayout.HORIZONTAL);
        variants.setWeightSum(3f);
        for (int i = 1; i <= 3; i++) {
            final int variant = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText("Variation " + i);
            b.setTextSize(11);
            b.setTextColor(i == selectedVariation ? Color.BLACK : CREAM);
            b.setBackground(rounded(i == selectedVariation ? GOLD : SURFACE_2, 10, Color.rgb(70, 66, 58), 1));
            b.setOnClickListener(v -> {
                selectedVariation = variant;
                showResults();
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            bp.setMargins(dp(4), dp(10), dp(4), 0);
            variants.addView(b, bp);
        }
        root.addView(variants);

        Button compare = button("COMPARE BEFORE / AFTER", true);
        compare.setOnClickListener(v -> showCompare());
        root.addView(compare);
        Button newConsult = button("START NEW CONSULTATION", false);
        newConsult.setOnClickListener(v -> showHome());
        root.addView(newConsult);

        setContentView(scroll(root));
    }

    private void showCompare() {
        LinearLayout root = page();
        addTopBar(root, "Compare", this::showResults);
        root.addView(text("Before  ↔  After", 13, MUTED, Gravity.CENTER));
        root.addView(spacer(12));

        CompareView compare = new CompareView();
        compare.setBackground(rounded(Color.rgb(222, 217, 204), 18, Color.TRANSPARENT, 0));
        root.addView(compare, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(470)));
        root.addView(spacer(16));

        LinearLayout actions = surface();
        actions.addView(text("◫  Side by side      ↔  Flip      ⤢  Fullscreen", 13, CREAM, Gravity.CENTER));
        root.addView(actions);
        Button done = button("BACK TO RESULTS", true);
        done.setOnClickListener(v -> showResults());
        root.addView(done);
        setContentView(scroll(root));
    }

    private void showAdmin() {
        LinearLayout root = page();
        addTopBar(root, "Admin Dashboard", this::showHome);

        LinearLayout today = surface();
        today.addView(label("TODAY'S GENERATIONS"));
        today.addView(text("34", 38, CREAM, Gravity.START));
        today.addView(text("↑ 12% vs yesterday", 12, GREEN, Gravity.START));
        root.addView(today);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);
        row.addView(statCard("THIS MONTH", "684"), new LinearLayout.LayoutParams(0, dp(118), 1f));
        row.addView(statCard("TOTAL CUSTOMERS", "211"), new LinearLayout.LayoutParams(0, dp(118), 1f));
        root.addView(row);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setWeightSum(2f);
        row2.addView(statCard("STYLES ENABLED", "15"), new LinearLayout.LayoutParams(0, dp(118), 1f));
        row2.addView(statCard("IMAGES / STYLE", "3"), new LinearLayout.LayoutParams(0, dp(118), 1f));
        root.addView(row2);

        LinearLayout status = surface();
        status.addView(text("Salon Settings                                      ›", 14, CREAM, Gravity.START));
        status.addView(spacer(15));
        status.addView(text("Device & AI Service              ● READY", 14, GREEN, Gravity.START));
        status.addView(spacer(15));
        status.addView(text("Subscription                              Pro Plan  ›", 14, CREAM, Gravity.START));
        status.addView(spacer(15));
        status.addView(text("Usage History                                      ›", 14, CREAM, Gravity.START));
        root.addView(status);

        Button back = button("RETURN TO CONSULTATION", true);
        back.setOnClickListener(v -> showHome());
        root.addView(back);
        setContentView(scroll(root));
    }

    private LinearLayout statCard(String name, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(14), dp(13), dp(12));
        card.setBackground(rounded(SURFACE, 14, Color.rgb(52, 50, 45), 1));
        card.addView(label(name));
        card.addView(spacer(8));
        card.addView(text(value, 27, CREAM, Gravity.START));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(118), 1f);
        lp.setMargins(dp(4), dp(8), dp(4), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private class FaceGuideView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        FaceGuideView() {
            super(MainActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(GOLD);
            p.setAlpha(190);
            c.drawOval(new RectF(w * .22f, h * .10f, w * .78f, h * .80f), p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(CREAM);
            p.setAlpha(40);
            c.drawCircle(w * .5f, h * .39f, w * .19f, p);
            c.drawRoundRect(new RectF(w * .34f, h * .60f, w * .66f, h * .91f), dp(35), dp(35), p);
            p.setAlpha(255);
            p.setColor(CREAM);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(13));
            c.drawText("FACE GUIDE", w * .5f, h * .95f, p);
        }
    }

    private class ResultPreviewView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        ResultPreviewView() {
            super(MainActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            drawPortrait(c, getWidth() / 2f, getHeight() * .48f, true);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.rgb(50, 46, 40));
            p.setTextSize(dp(12));
            c.drawText(selectedStyle + "  •  Variation " + selectedVariation,
                    getWidth() / 2f, getHeight() - dp(20), p);
        }
    }

    private class CompareView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        CompareView() {
            super(MainActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float mid = getWidth() / 2f;
            c.save();
            c.clipRect(0, 0, mid, getHeight());
            drawPortrait(c, mid, getHeight() * .48f, false);
            c.restore();
            c.save();
            c.clipRect(mid, 0, getWidth(), getHeight());
            drawPortrait(c, mid, getHeight() * .48f, true);
            c.restore();

            p.setColor(Color.WHITE);
            p.setStrokeWidth(dp(2));
            c.drawLine(mid, 0, mid, getHeight(), p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(mid, getHeight() * .55f, dp(20), p);
            p.setColor(Color.BLACK);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(17));
            c.drawText("↔", mid, getHeight() * .55f + dp(6), p);
            p.setTextSize(dp(11));
            p.setColor(Color.WHITE);
            c.drawText("BEFORE", dp(42), dp(30), p);
            c.drawText("AFTER", getWidth() - dp(42), dp(30), p);
        }
    }

    private void drawPortrait(Canvas c, float cx, float cy, boolean styled) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = Math.min(getWidth(), getHeight()) / 410f;

        p.setColor(Color.rgb(58, 56, 53));
        c.drawRoundRect(new RectF(cx - 105 * scale, cy + 120 * scale,
                cx + 105 * scale, cy + 250 * scale), 35 * scale, 35 * scale, p);

        p.setColor(Color.rgb(190, 146, 111));
        c.drawOval(new RectF(cx - 82 * scale, cy - 105 * scale,
                cx + 82 * scale, cy + 132 * scale), p);

        p.setColor(Color.rgb(42, 32, 27));
        p.setStyle(Paint.Style.FILL);
        float hairLift = styled ? 35 * scale : 8 * scale;
        RectF hair = new RectF(cx - 88 * scale, cy - 120 * scale - hairLift,
                cx + 88 * scale, cy - 15 * scale);
        c.drawOval(hair, p);
        if (styled) {
            p.setStrokeWidth(8 * scale);
            for (int i = -6; i <= 6; i++) {
                float x = cx + i * 12 * scale;
                c.drawLine(x, cy - 70 * scale, x + (i % 2 == 0 ? 12 : -6) * scale,
                        cy - 135 * scale - (Math.abs(i) % 3) * 8 * scale, p);
            }
        }

        p.setColor(Color.rgb(45, 34, 29));
        c.drawOval(new RectF(cx - 55 * scale, cy + 50 * scale,
                cx + 55 * scale, cy + 132 * scale), p);
        p.setColor(Color.rgb(190, 146, 111));
        c.drawRect(cx - 47 * scale, cy + 45 * scale,
                cx + 47 * scale, cy + 88 * scale, p);

        p.setColor(Color.rgb(40, 32, 29));
        c.drawOval(new RectF(cx - 45 * scale, cy - 15 * scale,
                cx - 15 * scale, cy + 2 * scale), p);
        c.drawOval(new RectF(cx + 15 * scale, cy - 15 * scale,
                cx + 45 * scale, cy + 2 * scale), p);
        p.setColor(Color.rgb(240, 236, 225));
        c.drawCircle(cx - 29 * scale, cy - 6 * scale, 5 * scale, p);
        c.drawCircle(cx + 29 * scale, cy - 6 * scale, 5 * scale, p);
    }
}
