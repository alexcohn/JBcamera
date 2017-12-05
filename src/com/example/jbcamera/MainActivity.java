package com.example.jbcamera;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import android.R.color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {

  private static final String LOG_TAG = "JBCamera";
	private int cameraId = 1;
	private Camera camera = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}

	@Override
	public void onClick(View v) {

		if (findViewById(R.id.bitmap_view).getVisibility() == View.VISIBLE) {
			v.setVisibility(View.INVISIBLE);
			camera.startPreview();
			return;
		}

		if (v.getId() == R.id.preview_surface) {
			camera.takePicture(null, null, pictureCallback);
		}
		else if (v.getId() == R.id.capture_button) {
			camera.takePicture(null, null, pictureCallback);
		}
		else if (v.getId() == R.id.switch_button) {
			switchCamera();
		}
		else if (v.getTag() != null && v.getTag().getClass() == Camera.Size.class) {
			Camera.Size sz = (Camera.Size)v.getTag();
			Log.d(LOG_TAG, "setPictureSize " + sz.width + "x" + sz.height);
			setPictureSize(sz.width, sz.height);
			((View) v.getParent()).setVisibility(View.INVISIBLE);
		}
	}

	public void switchCamera() {
		startCamera(1 - cameraId);
	}

	@Override
	public void onResume()
	{
		Log.d(LOG_TAG, "onResume");
		super.onResume();
		setSurface();
	}

	private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			try {
				FileOutputStream jpg = new FileOutputStream("/sdcard/JBCameraCapture.jpg");
				jpg.write(data);
				jpg.close();

				Log.i(LOG_TAG, "written " + data.length + " bytes to /sdcard/JBCameraCapture.jpg");

				Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);

				Log.i(LOG_TAG, "bmp dimensions " + bmp.getWidth() + "x" + bmp.getHeight());

				ImageView bmpView = (ImageView)findViewById(R.id.bitmap_view);
				bmpView.setImageBitmap(bmp);
				bmpView.setVisibility(View.VISIBLE);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	};

	private SurfaceHolder.Callback shCallback = new SurfaceHolder.Callback() {

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.i(LOG_TAG, "surfaceDestroyed callback");
			if (camera != null) {
				camera.stopPreview();
				camera.release();
			}
			camera = null;
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.i(LOG_TAG, "surfaceCreated callback");
			startCamera(cameraId);
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			Log.i(LOG_TAG, "surfaceChanged callback " + width + "x" + height);
			restartPreview();
		}
	};

	private void setSurface() {
		SurfaceView previewSurfaceView = (SurfaceView)findViewById(R.id.preview_surface);
		previewSurfaceView.getHolder().addCallback(shCallback);
	}

	Handler cameraHandler;

	protected void startCamera(final int id) {

//		startPreview(id, openCamera(id));

		if (cameraHandler == null) {
			HandlerThread handlerThread = new HandlerThread("CameraHandlerThread");
			handlerThread.start();
			cameraHandler = new Handler(handlerThread.getLooper());
		}
		releaseCamera();
		cameraHandler.post(new Runnable() {
		   @Override
		   public void run() {
			   final Camera camera = openCamera(id);
			   runOnUiThread(new Runnable() {
				   @Override
				   public void run() {
					   startPreview(id, camera);
				   }
			   });
		   }
	   });
	}

	private void releaseCamera() {
		if (camera != null) {
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}

	private void restartPreview() {
		if (camera == null) {
			return;
		}
		int degrees = 0;
		switch (getWindowManager().getDefaultDisplay().getRotation()) {
		case Surface.ROTATION_0: 
			degrees = 0;
			break;
		case Surface.ROTATION_90: 
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}
		Camera.CameraInfo ci = new Camera.CameraInfo();
		Camera.getCameraInfo(cameraId, ci);
		if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			degrees += ci.orientation;
			degrees %= 360;
			degrees = 360 - degrees;
		}
		else {
			degrees = 360 - degrees;
			degrees += ci.orientation;
		}
		camera.setDisplayOrientation(degrees%360);
		camera.startPreview();

		openChoosePictureResolution();
	}

	private void openChoosePictureResolution() {

		List<Camera.Size> supportedSizes;
		Camera.Parameters params = camera.getParameters();

//		supportedSizes = params.getSupportedPreviewSizes();
//		for (Camera.Size sz : supportedSizes) {
//			Log.d(LOG_TAG, "supportedPreviewSizes " + sz.width + "x" + sz.height);
//		}
//		supportedSizes = params.getSupportedVideoSizes();
//		for (Camera.Size sz : supportedSizes) {
//			Log.d(LOG_TAG, "supportedVideoSizes " + sz.width + "x" + sz.height);
//		}

		supportedSizes = params.getSupportedPictureSizes();

		LinearLayout lv = (LinearLayout)findViewById(R.id.sizes_view);
		lv.removeAllViews();

		for (Camera.Size sz : supportedSizes) {
			Log.d(LOG_TAG, "supportedPictureSizes " + sz.width + "x" + sz.height);
			TextView item = new TextView(this);
			item.setOnClickListener(this);
			item.setText("    " + sz.width + "x" + sz.height);
			item.setTag(sz);
			lv.addView(item);
		}

		lv.setVisibility(View.VISIBLE);
	}

	private static Camera openCamera(int id) {
		Log.d(LOG_TAG, "opening camera " + id);
		Camera camera = null;
		try {
			camera = Camera.open(id);
			Log.d(LOG_TAG, "opened camera " + id);
		} catch (Exception e) {
			e.printStackTrace();
			camera.release();
			camera = null;
		}
		return camera;
	}

	private void setPictureSize(int width, int height) {
		Camera.Parameters params = camera.getParameters();
		params.setPictureSize(width, height);
		camera.setParameters(params);
	}

	private void startPreview(int id, Camera c) {
		if (c != null) {
			try {
				SurfaceView previewSurfaceView = (SurfaceView)findViewById(R.id.preview_surface);
				SurfaceHolder holder = previewSurfaceView.getHolder();
				c.setPreviewDisplay(holder);
				camera = c;
				cameraId = id;
				restartPreview();
			} catch (IOException e) {
				e.printStackTrace();
				c.release();
			}
		}
	}
}
