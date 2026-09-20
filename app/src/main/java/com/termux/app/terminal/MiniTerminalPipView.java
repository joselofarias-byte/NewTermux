package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalRenderer;

/**
 * A tiny live-preview pip of a TerminalSession. Scales the full terminal screen down to fit
 * its view bounds using Canvas.scale(), preserving all colors and cursor state.
 * Call notifyUpdate() from onTextChanged() to keep it live.
 */
public class MiniTerminalPipView extends View {

    private TerminalSession mSession;
    private final TerminalRenderer mRenderer;
    private final Paint mBorderPaint = new Paint();
    private final Paint mBgPaint = new Paint();
    private final Paint mOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mIsActive = false;
    private boolean mIsDead = false;
    private boolean mIsFailsafe = false;

    // Border colors
    private static final int COLOR_BORDER_ACTIVE  = 0xFFBB86FC;
    private static final int COLOR_BORDER_INACTIVE = 0xFF444444;
    private static final int COLOR_BORDER_DEAD = 0xFFCF6679;
    private static final int COLOR_BORDER_FAILSAFE = 0xFFFFB74D;
    private static final float BORDER_WIDTH = 2.5f;

    public MiniTerminalPipView(Context context) {
        super(context);
        // Font size just needs to give reasonable glyph proportions before scaling.
        mRenderer = new TerminalRenderer(12, Typeface.MONOSPACE);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(BORDER_WIDTH);
        mBorderPaint.setAntiAlias(false);
        mBgPaint.setStyle(Paint.Style.FILL);
        mOverlayPaint.setColor(0xCCCF6679);
        mOverlayPaint.setTextSize(18f);
        mOverlayPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mOverlayPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setSession(TerminalSession session) {
        mSession = session;
        mIsDead = session != null && !session.isRunning();
        invalidate();
    }

    public TerminalSession getSession() {
        return mSession;
    }

    public void setActive(boolean active) {
        if (mIsActive == active) return;
        mIsActive = active;
        invalidate();
    }

    public void setDead(boolean dead) {
        if (mIsDead == dead) return;
        mIsDead = dead;
        invalidate();
    }

    public void setFailsafe(boolean failsafe) {
        if (mIsFailsafe == failsafe) return;
        mIsFailsafe = failsafe;
        invalidate();
    }

    /** Call from onTextChanged() — safe from any thread. */
    public void notifyUpdate() {
        if (mSession != null) {
            mIsDead = !mSession.isRunning();
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TerminalEmulator emulator = (mSession != null) ? mSession.getEmulator() : null;

        if (emulator == null) {
            // Draw placeholder
            mBgPaint.setColor(0xFF1A1A1A);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBgPaint);
        } else {
            // Fill background with terminal's background color
            int bgColor = emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND];
            mBgPaint.setColor(bgColor);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBgPaint);

            float rendererW = mRenderer.getFontWidth() * emulator.mColumns;
            float rendererH = mRenderer.getFontLineSpacing() * emulator.mRows;

            if (rendererW > 0 && rendererH > 0) {
                float scaleX = getWidth()  / rendererW;
                float scaleY = getHeight() / rendererH;
                canvas.save();
                canvas.scale(scaleX, scaleY);
                mRenderer.render(emulator, canvas, 0, -1, -1, -1, -1);
                canvas.restore();
            }
        }

        if (mIsDead) {
            canvas.drawText("EXIT", getWidth() / 2f, getHeight() / 2f + 6f, mOverlayPaint);
        }

        int borderColor;
        if (mIsDead) {
            borderColor = COLOR_BORDER_DEAD;
        } else if (mIsFailsafe) {
            borderColor = COLOR_BORDER_FAILSAFE;
        } else if (mIsActive) {
            borderColor = COLOR_BORDER_ACTIVE;
        } else {
            borderColor = COLOR_BORDER_INACTIVE;
        }
        mBorderPaint.setColor(borderColor);
        float half = BORDER_WIDTH / 2f;
        canvas.drawRect(half, half, getWidth() - half, getHeight() - half, mBorderPaint);
    }
}
