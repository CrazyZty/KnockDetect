package zty.knockdetect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.widget.TextView;

import com.Tool.Function.CommonFunction;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static int knockNumber = 0;

    public final static int UpdateKnockNumber = 0;
    public final static int UpdateSensorData = 1;

    private ArrayList<Float> linearAccelerationZShowList;

    private TextView knockNumberView;
    private SensorDataView sensorDataView;

    private Handler handler;

    private Intent knockDetectIntent;

    private static MainActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        init(R.layout.activity_main);

        instance = this;
    }

    protected synchronized void init(int layoutResourceId) {
        setContentView(layoutResourceId);

        bindView();
        initView();
        initData();
    }

    private void bindView() {
        knockNumberView = (TextView) findViewById(R.id.knockNumberView);

        sensorDataView = (SensorDataView) findViewById(R.id.sensorDataView);
    }

    private void initView() {
        sensorDataView.setData(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
    }

    private void initData() {
        handler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case UpdateKnockNumber:
                        knockNumberView
                                .setText(CommonFunction.GetDate() + "：敲击" + knockNumber + "次");
                        break;
                    case UpdateSensorData:
                        sensorDataView.updateView(linearAccelerationZShowList);
                        break;
                }
            }
        };

        knockDetectIntent = new Intent(this, KnockDetectService.class);

        startService(knockDetectIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        instance = null;

        if (knockDetectIntent != null) {
            stopService(knockDetectIntent);
        }
    }

    public static void UpdateKnockNumber(int knockNumber) {
        if (CommonFunction.isActivityEnable(instance)) {
            instance.knockNumber = knockNumber;

            Message.obtain(instance.handler, UpdateKnockNumber).sendToTarget();
        }
    }

    public static void UpdateSensorData(ArrayList<Float> linearAccelerationZShowList) {
        if (CommonFunction.isActivityEnable(instance)) {
            instance.linearAccelerationZShowList =
                    (ArrayList<Float>) linearAccelerationZShowList.clone();

            Message.obtain(instance.handler, UpdateSensorData).sendToTarget();
        }
    }
}
