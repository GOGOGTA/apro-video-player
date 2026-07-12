package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

public class PrecisionSeekBarView extends View {

    public interface OnSeekGestureListener {
        void onSeekInteractionStart(long anchorPositionMs);

        void onSeekPreview(long positionMs, boolean fineMode, boolean frameStepMode);

        void onSeekCommit(long positionMs, boolean wasTap, boolean fineMode, boolean frameStepMode);

        void onSeekInteractionEnd();
    }

    private static final float INACTIVE_TRACK_HEIGHT_DP = 4f;
    private static final float ACTIVE_TRACK_HEIGHT_DP = 6f;
    private static final float THUMB_RADIUS_DP = 5f;
    private static final float ACTIVE_TOUCH_HEIGHT_DP = 44f;
    private static final float SIDE_PADDING_DP = 4f;

    private static final float FINE_ENTER_VELOCITY_PX_PER_SEC = 320f;
    private static final float FINE_EXIT_VELOCITY_PX_PER_SEC = 480f;
    private static final float FRAME_STEP_VELOCITY_PX_PER_SEC = 120f;
    private static final float INERTIA_TRIGGER_VELOCITY_PX_PER_SEC = 1400f;
    private static final float NORMAL_DRAG_SENSITIVITY = 1.8f;
    private static final float FINE_DRAG_SENSITIVITY = 1.35f;
    private static final long FINE_HOLD_MS = 100L;
    private static final long EDGE_FEEDBACK_WINDOW_MS = 300L;
    private static final long INERTIA_ZONE_MS = 3000L;
    private static final long INERTIA_CLAMP_MS = 1000L;

    private final Paint inactiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bufferedTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF bufferedRect = new RectF();
    private final RectF activeRect = new RectF();
    private final float density;
    private final int touchSlop;

    private OnSeekGestureListener listener;

    private long durationMs;
    private long positionMs;
    private long bufferedPositionMs;
    private long previewPositionMs;
    private long frameDurationMs = 33L;

    private boolean scrubbing;
    private boolean fineMode;
    private boolean frameStepMode;
    private boolean frameStepEnabled;
    private boolean buffering;

    private float downX;
    private float anchorTouchX;
    private long anchorPositionMs;
    private long candidateFineSinceMs = -1L;
    private VelocityTracker velocityTracker;
    private boolean movedEnough;
    private boolean startEdgeFeedbackSent;
    private boolean endEdgeFeedbackSent;

    public PrecisionSeekBarView(@NonNull Context context) {
        this(context, null);
    }

    public PrecisionSeekBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PrecisionSeekBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        initPaints();
    }

    private void initPaints() {
        int colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary);
        int colorSecondaryContainer = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSecondaryContainer
        );
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);

        inactiveTrackPaint.setColor(ColorUtils.setAlphaComponent(colorOnSurface, 56));
        bufferedTrackPaint.setColor(ColorUtils.blendARGB(colorSecondaryContainer, colorOnSurface, 0.18f));
        activeTrackPaint.setColor(colorPrimary);
        thumbPaint.setColor(colorPrimary);
    }

    public void setOnSeekGestureListener(@Nullable OnSeekGestureListener listener) {
        this.listener = listener;
    }

    public void setDurationMs(long durationMs) {
        long nextDuration = Math.max(0L, durationMs);
        if (this.durationMs == nextDuration) {
            return;
        }
        this.durationMs = nextDuration;
        positionMs = clamp(positionMs, 0L, nextDuration);
        bufferedPositionMs = clamp(bufferedPositionMs, 0L, nextDuration);
        previewPositionMs = clamp(previewPositionMs, 0L, nextDuration);
        invalidate();
    }

    public void setPositionMs(long positionMs) {
        long clamped = clamp(positionMs, 0L, durationMs);
        if (this.positionMs == clamped) {
            return;
        }
        this.positionMs = clamped;
        if (!scrubbing) {
            previewPositionMs = clamped;
        }
        invalidate();
    }

    public void setBufferedPositionMs(long bufferedPositionMs) {
        long clamped = clamp(bufferedPositionMs, 0L, durationMs);
        if (this.bufferedPositionMs == clamped) {
            return;
        }
        this.bufferedPositionMs = clamped;
        invalidate();
    }

    public void setFrameStepEnabled(boolean enabled) {
        if (frameStepEnabled == enabled) {
            return;
        }
        frameStepEnabled = enabled;
        if (!enabled) {
            frameStepMode = false;
        }
    }

    public void setFrameDurationMs(long frameDurationMs) {
        this.frameDurationMs = Math.max(1L, frameDurationMs);
    }

    public void setBuffering(boolean buffering) {
        if (this.buffering == buffering) {
            return;
        }
        this.buffering = buffering;
        int colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary);
        int colorPrimaryContainer = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorPrimaryContainer
        );
        thumbPaint.setColor(buffering ? colorPrimaryContainer : colorPrimary);
        invalidate();
    }

    public long getDisplayedPositionMs() {
        return scrubbing ? previewPositionMs : positionMs;
    }

    public boolean isScrubbing() {
        return scrubbing;
    }

    public boolean isFineMode() {
        return fineMode;
    }

    public boolean isFrameStepMode() {
        return frameStepMode;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) dp(ACTIVE_TOUCH_HEIGHT_DP);
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec), resolvedHeight);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float left = getPaddingLeft() + dp(SIDE_PADDING_DP);
        float right = getWidth() - getPaddingRight() - dp(SIDE_PADDING_DP);
        float centerY = getHeight() * 0.5f;
        float trackHeight = scrubbing ? dp(ACTIVE_TRACK_HEIGHT_DP) : dp(INACTIVE_TRACK_HEIGHT_DP);
        float halfTrackHeight = trackHeight * 0.5f;

        trackRect.set(left, centerY - halfTrackHeight, right, centerY + halfTrackHeight);
        float radius = trackHeight * 0.5f;
        canvas.drawRoundRect(trackRect, radius, radius, inactiveTrackPaint);

        if (durationMs > 0L) {
            float bufferedRight = positionToX(bufferedPositionMs);
            if (bufferedRight > left) {
                bufferedRect.set(left, trackRect.top, bufferedRight, trackRect.bottom);
                canvas.drawRoundRect(bufferedRect, radius, radius, bufferedTrackPaint);
            }

            long displayedPositionMs = getDisplayedPositionMs();
            float activeRight = positionToX(displayedPositionMs);
            if (activeRight > left) {
                activeRect.set(left, trackRect.top, activeRight, trackRect.bottom);
                canvas.drawRoundRect(activeRect, radius, radius, activeTrackPaint);
            }

            if (scrubbing) {
                float thumbRadius = dp(THUMB_RADIUS_DP);
                canvas.drawCircle(activeRight, centerY, thumbRadius, thumbPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (durationMs <= 0L) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isTouchOnTrack(event.getY())) {
                    return false;
                }
                beginTracking(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scrubbing) {
                    return false;
                }
                updateTracking(event);
                return true;
            case MotionEvent.ACTION_UP:
                if (!scrubbing) {
                    return false;
                }
                boolean wasTap = !movedEnough;
                finishTracking(event, false);
                if (wasTap) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!scrubbing) {
                    return false;
                }
                finishTracking(event, true);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void beginTracking(@NonNull MotionEvent event) {
        scrubbing = true;
        fineMode = false;
        frameStepMode = false;
        movedEnough = false;
        candidateFineSinceMs = -1L;
        startEdgeFeedbackSent = false;
        endEdgeFeedbackSent = false;
        downX = event.getX();
        anchorTouchX = downX;
        anchorPositionMs = positionMs;
        previewPositionMs = anchorPositionMs;
        recycleVelocityTracker();
        velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
        getParent().requestDisallowInterceptTouchEvent(true);
        if (listener != null) {
            listener.onSeekInteractionStart(previewPositionMs);
        }
        invalidate();
    }

    private void updateTracking(@NonNull MotionEvent event) {
        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
            velocityTracker.computeCurrentVelocity(1000);
        }

        float deltaFromDown = event.getX() - downX;
        if (!movedEnough && Math.abs(deltaFromDown) < touchSlop) {
            return;
        }
        movedEnough = true;

        float velocityX = velocityTracker != null ? velocityTracker.getXVelocity() : 0f;
        updatePrecisionMode(velocityX, event.getEventTime(), event.getX());

        long targetPosition = clamp(
                anchorPositionMs + Math.round((event.getX() - anchorTouchX) * getCurrentMsPerPx()),
                0L,
                durationMs
        );

        frameStepMode = frameStepEnabled && Math.abs(velocityX) < FRAME_STEP_VELOCITY_PX_PER_SEC;
        if (frameStepMode) {
            targetPosition = quantizeToFrame(targetPosition);
        }

        previewPositionMs = targetPosition;
        dispatchEdgeFeedback(targetPosition);
        if (listener != null) {
            listener.onSeekPreview(previewPositionMs, fineMode, frameStepMode);
        }
        invalidate();
    }

    private void finishTracking(@NonNull MotionEvent event, boolean cancelled) {
        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
            velocityTracker.computeCurrentVelocity(1000);
        }

        long commitPositionMs = previewPositionMs;
        boolean wasTap = !movedEnough && !cancelled;

        if (wasTap) {
            commitPositionMs = positionMs;
            cancelled = true;
        } else if (!cancelled) {
            float velocityX = velocityTracker != null ? velocityTracker.getXVelocity() : 0f;
            commitPositionMs = applyReleaseInertia(commitPositionMs, velocityX);
        }

        if (!cancelled && listener != null) {
            listener.onSeekCommit(commitPositionMs, wasTap, fineMode, frameStepMode);
        }

        scrubbing = false;
        fineMode = false;
        frameStepMode = false;
        movedEnough = false;
        previewPositionMs = positionMs;
        recycleVelocityTracker();
        getParent().requestDisallowInterceptTouchEvent(false);
        if (listener != null) {
            listener.onSeekInteractionEnd();
        }
        invalidate();
    }

    private void updatePrecisionMode(float velocityX, long eventTimeMs, float eventX) {
        float absVelocity = Math.abs(velocityX);
        boolean nextFineMode = fineMode;
        if (!fineMode) {
            if (absVelocity < FINE_ENTER_VELOCITY_PX_PER_SEC) {
                if (candidateFineSinceMs < 0L) {
                    candidateFineSinceMs = eventTimeMs;
                } else if (eventTimeMs - candidateFineSinceMs >= FINE_HOLD_MS) {
                    nextFineMode = true;
                }
            } else {
                candidateFineSinceMs = -1L;
            }
        } else if (absVelocity > FINE_EXIT_VELOCITY_PX_PER_SEC) {
            nextFineMode = false;
            candidateFineSinceMs = -1L;
        }

        if (nextFineMode != fineMode) {
            fineMode = nextFineMode;
            anchorPositionMs = previewPositionMs;
            anchorTouchX = eventX;
        }
    }

    private long applyReleaseInertia(long basePositionMs, float velocityX) {
        float absVelocity = Math.abs(velocityX);
        if (fineMode || frameStepMode || absVelocity < INERTIA_TRIGGER_VELOCITY_PX_PER_SEC) {
            return basePositionMs;
        }

        long remainingToEdge = velocityX >= 0f ? durationMs - basePositionMs : basePositionMs;
        float damping = 1f;
        if (remainingToEdge <= INERTIA_ZONE_MS) {
            damping *= 0.35f;
        }
        if (remainingToEdge <= INERTIA_CLAMP_MS) {
            damping *= 0.15f;
        }

        long maxOffsetMs = Math.max(1500L, durationMs / 10L);
        long offsetMs = Math.round(velocityX * getNormalMsPerPx() * 0.05f * damping);
        offsetMs = clamp(offsetMs, -maxOffsetMs, maxOffsetMs);
        return clamp(basePositionMs + offsetMs, 0L, durationMs);
    }

    private void dispatchEdgeFeedback(long positionMs) {
        boolean atStart = positionMs <= EDGE_FEEDBACK_WINDOW_MS;
        boolean atEnd = durationMs > 0L && durationMs - positionMs <= EDGE_FEEDBACK_WINDOW_MS;

        if (atStart && !startEdgeFeedbackSent) {
            performEdgeHapticFeedback();
            startEdgeFeedbackSent = true;
        } else if (!atStart) {
            startEdgeFeedbackSent = false;
        }

        if (atEnd && !endEdgeFeedbackSent) {
            performEdgeHapticFeedback();
            endEdgeFeedbackSent = true;
        } else if (!atEnd) {
            endEdgeFeedbackSent = false;
        }
    }

    private void performEdgeHapticFeedback() {
        int feedbackConstant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                ? HapticFeedbackConstants.TEXT_HANDLE_MOVE
                : HapticFeedbackConstants.CLOCK_TICK;
        performHapticFeedback(feedbackConstant);
    }

    private boolean isTouchOnTrack(float y) {
        float centerY = getHeight() * 0.5f;
        float halfTouch = dp(ACTIVE_TOUCH_HEIGHT_DP) * 0.5f;
        return y >= centerY - halfTouch && y <= centerY + halfTouch;
    }

    private float getCurrentMsPerPx() {
        return fineMode ? getFineMsPerPx() : getNormalMsPerPx();
    }

    private float getNormalMsPerPx() {
        float width = getTrackWidth();
        if (width <= 0f || durationMs <= 0L) {
            return 0f;
        }
        return ((float) durationMs / width) * NORMAL_DRAG_SENSITIVITY;
    }

    private float getFineMsPerPx() {
        float normalMsPerPx = getNormalMsPerPx();
        if (durationMs <= 60_000L) {
            return Math.min(normalMsPerPx, 35f * FINE_DRAG_SENSITIVITY);
        }
        if (durationMs <= 10 * 60_000L) {
            return Math.min(normalMsPerPx, 90f * FINE_DRAG_SENSITIVITY);
        }
        return Math.min(normalMsPerPx, 150f * FINE_DRAG_SENSITIVITY);
    }

    private float positionToX(long positionMs) {
        float ratio = durationMs <= 0L ? 0f : (float) positionMs / (float) durationMs;
        return getTrackLeft() + clamp(ratio, 0f, 1f) * getTrackWidth();
    }

    private float getTrackLeft() {
        return getPaddingLeft() + dp(SIDE_PADDING_DP);
    }

    private float getTrackWidth() {
        return Math.max(1f, getWidth() - getPaddingLeft() - getPaddingRight() - dp(SIDE_PADDING_DP * 2f));
    }

    private long quantizeToFrame(long positionMs) {
        return Math.round((double) positionMs / (double) frameDurationMs) * frameDurationMs;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
