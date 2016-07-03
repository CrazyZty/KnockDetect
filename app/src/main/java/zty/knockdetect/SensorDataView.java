package zty.knockdetect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by 郑童宇 on 2016/06/29.
 */
public class SensorDataView extends View {
    private int width;
    private int height;
    private int sensorReadDataSize;

    private final static int maxAcceleration = 20;

    private float linePerLength;
    private float linePerHeight;

    private ArrayList<Float> linearAccelerationZShowList;

    private Paint paint;

    public SensorDataView(Context context) {
        super(context);
    }

    public SensorDataView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SensorDataView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setData(int width, int height) {
        this.width = width;
        this.height = height;

        initPaint();
    }

    private void initPaint() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(2);
    }

    public void updateView(ArrayList<Float> linearAccelerationZShowList) {
        if (linearAccelerationZShowList == null) {
            sensorReadDataSize = 0;
            return;
        }

        this.linearAccelerationZShowList = linearAccelerationZShowList;
        sensorReadDataSize = linearAccelerationZShowList.size();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        linePerLength = (float) width / sensorReadDataSize;
        linePerHeight = (float) (height) / (maxAcceleration * 2);

        if (sensorReadDataSize > 0) {
            float[] points = new float[4 * sensorReadDataSize];

            Iterator<Float> linearAccelerationZShowIterator =
                    linearAccelerationZShowList.iterator();

            float firstPointHeight;
            float secondPointHeight = linearAccelerationZShowIterator.next();

            for (int i = 0; i < sensorReadDataSize - 1; i++) {
                firstPointHeight = secondPointHeight;
                secondPointHeight = linearAccelerationZShowIterator.next();

                points[i * 4] = linePerLength * i;
                points[i * 4 + 1] = (firstPointHeight + maxAcceleration) * linePerHeight;
                points[i * 4 + 2] = linePerLength * (i + 1);
                points[i * 4 + 3] = (secondPointHeight + maxAcceleration) * linePerHeight;
            }

            canvas.drawLines(points, paint);
        }
    }
}
