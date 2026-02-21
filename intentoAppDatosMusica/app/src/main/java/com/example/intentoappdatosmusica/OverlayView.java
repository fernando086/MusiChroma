package com.example.intentoappdatosmusica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class OverlayView extends View {

    private Paint paintCircle;
    private Paint paintLine;
    private int centerX, centerY;

    public OverlayView(Context context) {
        super(context);
        init();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintCircle = new Paint();
        paintCircle.setColor(Color.RED);
        paintCircle.setStyle(Paint.Style.STROKE);
        paintCircle.setStrokeWidth(2);

        paintLine = new Paint();
        paintLine.setColor(Color.BLUE);
        paintLine.setStyle(Paint.Style.STROKE);
        paintLine.setStrokeWidth(2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        centerX = getWidth() / 2;
        centerY = getHeight() / 2;

        // Dibujar 3 círculos (intensidad 1, 2, 3)
        canvas.drawCircle(centerX, centerY, 100, paintCircle);
        canvas.drawCircle(centerX, centerY, 200, paintCircle);
        canvas.drawCircle(centerX, centerY, 300, paintCircle);

        // Dibujar 8 líneas cada 45 grados
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            float endX = (float) (centerX + 300 * Math.cos(angle));
            float endY = (float) (centerY + 300 * Math.sin(angle));
            canvas.drawLine(centerX, centerY, endX, endY, paintLine);
        }
    }
}
