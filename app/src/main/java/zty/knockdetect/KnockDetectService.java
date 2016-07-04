package zty.knockdetect;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.Tool.Function.LogFunction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by 郑童宇 on 2016/06/29.
 */
public class KnockDetectService extends Service implements SensorEventListener {
    private boolean stable;
    private boolean calibrateLinearAcceleration;

    private int calibrateLinearAccelerationIndex;
    private int sensorDataShowIndex;
    private int knockNumber;
    private int currentForecastNumber;

    private final int sensorDataShowNumber = 50;
    private final int sensorDataShowDurationNumber = 5;
    private final int accelerometerSensorType = Sensor.TYPE_ACCELEROMETER;

    private final int forecastNumber = 2;

    private final int unstableListLength = 10;

    private final int knockRecognitionDuration = 1000;

    private final int calibrateLinearAccelerationSectionNumber = 30;
    private final int stableSectionNumber = 50;
    private final int maxExceptionNumber = 5;

    private float recognitionKnockRatio = 20;
    private float recognitionOffsetRatio = 10;

    private float smoothOffsetMaxRatio = 10f;

    private final float alpha = 0.8f;

    private float gravityX;
    private float gravityY;
    private float gravityZ;
    private float linearAccelerationZ;
    private float linearAccelerationZStableSection;

    private final float maxStableOffset = 0.1f;

    private SensorManager sensorManager;
    private Sensor accelerationSensor;

    private LinkedList<Float> linearAccelerationZList;
    private LinkedList<Float> uniqueLinearAccelerationZList;

    private ArrayList<Float> linearAccelerationZShowList;

    @Override
    public void onCreate() {
        super.onCreate();

        stable = false;
        calibrateLinearAcceleration = true;

        calibrateLinearAccelerationIndex = 0;
        sensorDataShowIndex = 0;

        linearAccelerationZStableSection = 0;

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerationSensor = sensorManager.getDefaultSensor(accelerometerSensorType);

        if (null == sensorManager) {
            LogFunction.log("KnockDetectService", "device not support SensorManager");
            return;
        }

        sensorManager.registerListener(this, accelerationSensor, SensorManager.SENSOR_DELAY_GAME);

        linearAccelerationZList = new LinkedList<Float>();
        uniqueLinearAccelerationZList = new LinkedList<Float>();

        linearAccelerationZShowList = new ArrayList<Float>();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        linearAccelerationZStableSection = 0;

        if (null != sensorManager) {
            sensorManager.unregisterListener(this);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // 获取传感器数据
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor == null) {
            return;
        }

        if (sensorEvent.sensor.getType() == accelerometerSensorType) {
            float accelerationX = sensorEvent.values[0];
            float accelerationY = sensorEvent.values[1];
            float accelerationZ = sensorEvent.values[2];

            if (accelerationZ > 0) {
                recognitionKnockRatio = 20;
                recognitionOffsetRatio = 10;

                smoothOffsetMaxRatio = 5f;
            } else {
                recognitionKnockRatio = 7.5f;
                recognitionOffsetRatio = 6;

                smoothOffsetMaxRatio = 2.5f;
            }

            gravityX = alpha * gravityX + (1 - alpha) * accelerationX;
            gravityY = alpha * gravityY + (1 - alpha) * accelerationY;
            gravityZ = alpha * gravityZ + (1 - alpha) * accelerationZ;

            linearAccelerationZ = accelerationZ - gravityZ;

            if (calibrateLinearAcceleration) {
                calibrateLinearAccelerationIndex++;

                if (calibrateLinearAccelerationIndex <= calibrateLinearAccelerationSectionNumber) {
                    return;
                }

                calibrateLinearAcceleration = false;
            }

            if (sensorDataShowIndex >= sensorDataShowNumber) {
                sensorDataShowIndex = sensorDataShowNumber - sensorDataShowDurationNumber;

                Iterator<?> it = linearAccelerationZShowList.listIterator(0);
                for (int i = 0; i < sensorDataShowDurationNumber; i++) {
                    it.next();
                    it.remove();
                }

                MainActivity.UpdateSensorData(linearAccelerationZShowList);
            }

            linearAccelerationZShowList.add(linearAccelerationZ);

            sensorDataShowIndex++;

            if (!stable) {
                linearAccelerationZList.add(linearAccelerationZ);

                if (linearAccelerationZList.size() >= stableSectionNumber) {
                    stableRecognition();

                    linearAccelerationZList.clear();
                }

                return;
            }

            knockRecognition(linearAccelerationZ);
        }
    }

    // 稳态识别，用来判断手机目前是否处于稳状，判断方式:采样50个点，然后计算每个点的绝对值，如果
    // 大于最大偏差就视为异常点，异常点数目大于最大异常点数目就视为非稳态，反之视为稳态，如果识别
    // 结果为稳态则将剔除异常点后最大Z轴加速度和最小Z轴加速差值的1/2视为波动区间
    private void stableRecognition() {
        int exceptionNumber = 0;

        float accelerationZValue;
        float minAccelerationZValue = Integer.MAX_VALUE;
        float maxAccelerationZValue = Integer.MIN_VALUE;

        for (int i = stableSectionNumber - 1; i >= 0; i--) {
            accelerationZValue = linearAccelerationZList.get(i);

            if (Math.abs(accelerationZValue) > maxStableOffset) {
                exceptionNumber++;
            } else {
                if (accelerationZValue > maxAccelerationZValue) {
                    maxAccelerationZValue = accelerationZValue;
                } else {
                    if (accelerationZValue < minAccelerationZValue) {
                        minAccelerationZValue = accelerationZValue;
                    }
                }
            }
        }

        stable = exceptionNumber <= maxExceptionNumber;

        if (stable) {
            if (linearAccelerationZStableSection == 0) {
                linearAccelerationZStableSection =
                        (maxAccelerationZValue - minAccelerationZValue) / 2;
            }

            if (linearAccelerationZStableSection > maxStableOffset) {
                linearAccelerationZStableSection = maxStableOffset;
            }
        }

        MainActivity.UpdateStable(stable);

        LogFunction.log("stable", "" + stable);
        LogFunction.log("exceptionNumber", "" + exceptionNumber);
        LogFunction.log("linearAccelerationZStableSection", "" + linearAccelerationZStableSection);
    }

    // 处理偏移数据列表，如果独特加速度列表长度超过识别非稳态独特数据列表长度，则认为现在手机状态
    // 变化为非稳态，反之，如果发现加速度偏移数据列表中最大偏移值超过波动区间一定倍数则识别为敲击
    private void handleUniqueLinearAccelerationZ() {
        LogFunction.log("linearAccelerationZStableSection", "" + linearAccelerationZStableSection);

        int recognitionKnockNumber = 1;

        int uniqueLinearAccelerationZListLength = uniqueLinearAccelerationZList.size();

        float accelerationZOffsetAbsolute;
        float maxAccelerationZOffsetAbsolute = 0;

        for (int i = 0; i < uniqueLinearAccelerationZListLength; i++) {
            accelerationZOffsetAbsolute = Math.abs(uniqueLinearAccelerationZList.get(i));

            if (maxAccelerationZOffsetAbsolute < accelerationZOffsetAbsolute) {
                maxAccelerationZOffsetAbsolute = accelerationZOffsetAbsolute;
            }

            LogFunction.log("uniqueLinearAccelerationZList index" + i,
                    "" + uniqueLinearAccelerationZList.get(i));
        }

        uniqueLinearAccelerationZList.clear();

        LogFunction.log("uniqueLinearAccelerationZListLength",
                "" + uniqueLinearAccelerationZListLength);

        if (uniqueLinearAccelerationZListLength > unstableListLength) {
            stable = false;
            MainActivity.UpdateStable(stable);
            return;
        }

        LogFunction.log("maxAccelerationZOffsetAbsolute / linearAccelerationZStableSection",
                "" + (maxAccelerationZOffsetAbsolute / linearAccelerationZStableSection));

        if (maxAccelerationZOffsetAbsolute >
                linearAccelerationZStableSection * recognitionKnockRatio) {
            LogFunction.log("recognitionKnockRatio", "" + recognitionKnockRatio);
            LogFunction.log("recognitionOffsetRatio", "" + recognitionOffsetRatio);

            knockRecognitionSuccess(recognitionKnockNumber);
        }
    }

    // 敲击识别，获取此时线性加速度绝对值，如果线性加速度绝对值大于波动区间一定倍数，则加入到独特线性加速度列表中，
    // 在此时如果此时独特加速度列表长度大于0，如果currentForecastNumber大于0，则currentForecastNumber减一，
    // 反之，则开始处理独特加速度列表。同时如果线性加速度绝对值在一定范围则用现在的线性加速度绝对值来平滑波动区间。
    private void knockRecognition(float linearAccelerationZ) {
        float linearAccelerationZAbsolute = Math.abs(linearAccelerationZ);

        float linearAccelerationZAbsoluteRadio =
                linearAccelerationZAbsolute / linearAccelerationZStableSection;

        if (linearAccelerationZAbsoluteRadio > recognitionOffsetRatio) {
            uniqueLinearAccelerationZList.add(linearAccelerationZ);

            currentForecastNumber = forecastNumber;
        } else {
            if (uniqueLinearAccelerationZList.size() > 0) {
                if (currentForecastNumber > 0) {
                    currentForecastNumber--;
                } else {
                    handleUniqueLinearAccelerationZ();
                }
            }
        }

        if (linearAccelerationZAbsoluteRadio < smoothOffsetMaxRatio) {
            float offsetWeight = 0.001f;

            linearAccelerationZStableSection =
                    weightedMean(offsetWeight, linearAccelerationZAbsolute,
                            linearAccelerationZStableSection);
        }
    }

    // 敲击识别成功，当敲击次数为0时，识别第一次敲击
    private void knockRecognitionSuccess(int recognitionKnockNumber) {
        if (knockNumber == 0) {
            beginRecordKnockNumber();
        }

        knockNumber += recognitionKnockNumber;
    }

    // 识别第一次敲击，从此时开始计数
    private void beginRecordKnockNumber() {
        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                try {
                    Thread.sleep(knockRecognitionDuration);
                } catch (Exception e) {
                }

                subscriber.onCompleted();
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        LogFunction.log("事件", "敲击识别通过,敲击次数:" + knockNumber);

                        MainActivity.UpdateKnockNumber(knockNumber);

                        knockNumber = 0;
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(String string) {
                    }
                });
    }

    public float weightedMean(float weight, float currentValue, float lastWeightedMeanValue) {
        return currentValue * weight + lastWeightedMeanValue * (1 - weight);
    }

    //    private float[] accelerometerValues = new float[3];
    //    private float[] magneticFieldValues = new float[3];
    //
    //    magneticFieldValues = sensorEvent.values;
    //    accelerometerValues = sensorEvent.values;
    //
    //    private void calculateOrientation() {
    //        float[] values = new float[3];
    //        float[] R = new float[9];
    //
    //        SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticFieldValues);
    //        SensorManager.getOrientation(R, values);
    //
    //        // 要经过一次数据格式的转换，转换为角度度
    //        values[0] = (float) Math.toDegrees(values[0]);
    //        values[1] = (float) Math.toDegrees(values[1]);
    //        values[2] = (float) Math.toDegrees(values[2]);
    //    }
}