package com.alarmapp.alarmy.game

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

class MemoryGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface GameListener {
        fun onGameComplete()
        fun onWrongAttempt(attemptCount: Int)
        fun onSequenceShowStart()
        fun onSequenceShowEnd()
    }

    var listener: GameListener? = null
    var difficulty: Int = 3 // number of blocks in sequence

    private val gridSize = 3
    private val blockRects = Array(9) { RectF() }
    private val blockStates = IntArray(9) { STATE_IDLE }
    private val sequence = mutableListOf<Int>()
    private var userInputIndex = 0
    private var attemptCount = 0
    private var isShowingSequence = false
    private var isAcceptingInput = false
    private val handler = Handler(Looper.getMainLooper())

    // Block highlight animation values
    private val blockAlpha = FloatArray(9) { 1f }
    private val blockScale = FloatArray(9) { 1f }

    // Shake animation
    private var shakeOffset = 0f

    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2D3E")
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C63FF")
        style = Paint.Style.FILL
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    private val wrongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D4157")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6C63FF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    companion object {
        const val STATE_IDLE = 0
        const val STATE_HIGHLIGHT = 1
        const val STATE_SELECTED = 2
        const val STATE_WRONG = 3
    }

    fun startGame() {
        attemptCount = 0
        generateSequence()
        showSequence()
    }

    fun reset() {
        for (i in blockStates.indices) {
            blockStates[i] = STATE_IDLE
            blockAlpha[i] = 1f
            blockScale[i] = 1f
        }
        sequence.clear()
        userInputIndex = 0
        isShowingSequence = false
        isAcceptingInput = false
        shakeOffset = 0f
        handler.removeCallbacksAndMessages(null)
        invalidate()
    }

    private fun generateSequence() {
        sequence.clear()
        val available = (0 until 9).toMutableList()
        available.shuffle()
        for (i in 0 until difficulty.coerceAtMost(9)) {
            sequence.add(available[i])
        }
        userInputIndex = 0
        for (i in blockStates.indices) {
            blockStates[i] = STATE_IDLE
        }
    }

    private fun showSequence() {
        isShowingSequence = true
        isAcceptingInput = false
        listener?.onSequenceShowStart()

        for (i in blockStates.indices) {
            blockStates[i] = STATE_IDLE
        }
        invalidate()

        sequence.forEachIndexed { index, blockIndex ->
            handler.postDelayed({
                blockStates[blockIndex] = STATE_HIGHLIGHT
                animateBlockPulse(blockIndex)
                invalidate()

                handler.postDelayed({
                    blockStates[blockIndex] = STATE_IDLE
                    invalidate()

                    if (index == sequence.size - 1) {
                        handler.postDelayed({
                            isShowingSequence = false
                            isAcceptingInput = true
                            listener?.onSequenceShowEnd()
                        }, 300)
                    }
                }, 500)
            }, (index * 800L) + 500L)
        }
    }

    private fun animateBlockPulse(index: Int) {
        val scaleUp = ValueAnimator.ofFloat(1f, 1.08f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator()
            addUpdateListener {
                blockScale[index] = it.animatedValue as Float
                invalidate()
            }
        }
        scaleUp.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 16f
        val totalGap = padding * (gridSize + 1)
        val blockSize = (w.coerceAtMost(h) - totalGap) / gridSize
        val startX = (w - (blockSize * gridSize + padding * (gridSize - 1))) / 2
        val startY = (h - (blockSize * gridSize + padding * (gridSize - 1))) / 2

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val index = row * gridSize + col
                val left = startX + col * (blockSize + padding)
                val top = startY + row * (blockSize + padding)
                blockRects[index] = RectF(left, top, left + blockSize, top + blockSize)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(shakeOffset, 0f)

        for (i in 0 until 9) {
            val rect = blockRects[i]
            val scale = blockScale[i]

            canvas.save()
            val cx = rect.centerX()
            val cy = rect.centerY()
            canvas.scale(scale, scale, cx, cy)

            val paint = when (blockStates[i]) {
                STATE_HIGHLIGHT -> highlightPaint
                STATE_SELECTED -> selectedPaint
                STATE_WRONG -> wrongPaint
                else -> idlePaint
            }

            val cornerRadius = 16f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            if (blockStates[i] == STATE_HIGHLIGHT) {
                glowPaint.alpha = 120
                val glowRect = RectF(rect).apply { inset(-4f, -4f) }
                canvas.drawRoundRect(glowRect, cornerRadius + 4, cornerRadius + 4, glowPaint)
            }

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            canvas.restore()
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isAcceptingInput || isShowingSequence) return true

        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x - shakeOffset
            val y = event.y

            for (i in 0 until 9) {
                if (blockRects[i].contains(x, y)) {
                    handleBlockTap(i)
                    break
                }
            }
        }
        return true
    }

    private fun handleBlockTap(index: Int) {
        if (index == sequence[userInputIndex]) {
            // Correct tap
            blockStates[index] = STATE_SELECTED
            animateBlockPulse(index)
            invalidate()

            userInputIndex++
            if (userInputIndex >= sequence.size) {
                // Game complete!
                isAcceptingInput = false
                handler.postDelayed({
                    listener?.onGameComplete()
                }, 500)
            }
        } else {
            // Wrong tap
            attemptCount++
            isAcceptingInput = false
            blockStates[index] = STATE_WRONG
            invalidate()

            // Shake animation
            playShakeAnimation()

            listener?.onWrongAttempt(attemptCount)

            handler.postDelayed({
                // Reset all blocks to idle
                for (i in blockStates.indices) {
                    blockStates[i] = STATE_IDLE
                }
                invalidate()

                // Generate new sequence and show it
                handler.postDelayed({
                    generateSequence()
                    showSequence()
                }, 500)
            }, 800)
        }
    }

    private fun playShakeAnimation() {
        val shakeAnim = ObjectAnimator.ofFloat(this, "shakeOffsetValue", 0f, 20f, -20f, 15f, -15f, 10f, -10f, 0f).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
        }
        shakeAnim.start()
    }

    // Property for shake animation
    @Suppress("unused")
    fun setShakeOffsetValue(value: Float) {
        shakeOffset = value
        invalidate()
    }

    @Suppress("unused")
    fun getShakeOffsetValue(): Float = shakeOffset

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }
}
