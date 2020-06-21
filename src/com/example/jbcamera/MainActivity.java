package com.example.jbcamera;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.gms.instantapps.InstantApps;

public class MainActivity extends Activity {

  private static final String LOG_TAG = "JBCamera";
	private static final int REQUEST_CAMERA_PERMISSION = 21;
	private int cameraId = 1;
	private Camera camera = null;
	private boolean waitForPermission = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		boolean isInstantApp = InstantApps.getPackageManagerCompat(this).isInstantApp();
		Log.d(LOG_TAG, "are we instant? " + isInstantApp);
		findViewById(R.id.preview_surface).setOnClickListener(clickListener);
		findViewById(R.id.capture_button).setOnClickListener(clickListener);
		findViewById(R.id.switch_button).setOnClickListener(clickListener);
	}

	private View.OnClickListener clickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {

			if (camera == null) {
				return;
			} else if (v.getId() == R.id.preview_surface) {
				try {
					camera.takePicture(null, null, pictureCallback);
				} catch (RuntimeException e) {
					Log.e(LOG_TAG, "preview_surface", e);
				}
			} else if (v.getId() == R.id.capture_button) {
				try {
					camera.takePicture(null, null, pictureCallback);
				} catch (RuntimeException e) {
					Log.e(LOG_TAG, "capture_button", e);
				}
			} else if (v.getId() == R.id.switch_button) {
				switchCamera();
			}
		}
	};

	public void switchCamera() {
		startCamera(1 - cameraId);
	}

	@Override
	public void onResume() {
		super.onResume();
		setSurface();
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
				waitForPermission = true;
				requestCameraPermission();
			}
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	private void requestCameraPermission() {
		if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
			AlertDialog confirmationDialog = new AlertDialog.Builder(this)
					.setMessage(R.string.request_permission)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							requestPermissions(new String[]{Manifest.permission.CAMERA},
								REQUEST_CAMERA_PERMISSION);
						}
					})
					.setNegativeButton(android.R.string.cancel,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									finish();
								}
							})
					.create();
			confirmationDialog.show();
		} else {
			requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		if (requestCode == REQUEST_CAMERA_PERMISSION) {
			if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
				AlertDialog errorDialog = new AlertDialog.Builder(this)
					.setMessage(R.string.request_permission).create();
				errorDialog.show();
				finish();
			} else {
				waitForPermission = false;
				startCamera(cameraId);
			}
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera cam) {
			try {
 				String jpgPath = getCacheDir() + "/JBCameraCapture.jpg";
				FileOutputStream jpg = new FileOutputStream(jpgPath);
				jpg.write(data);
				jpg.close();

				Log.i(LOG_TAG, "written " + data.length + " bytes to " + jpgPath);

				final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);

				Log.i(LOG_TAG, "bmp dimensions " + bmp.getWidth() + "x" + bmp.getHeight());

				final ImageView bmpView = (ImageView)findViewById(R.id.bitmap_view);
				bmpView.post(new Runnable() {
					@Override
					public void run() {
						bmpView.setImageBitmap(bmp);
						bmpView.setVisibility(View.VISIBLE);
						camera.startPreview();
					}
				});
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

		if (cameraHandler == null) {
			HandlerThread handlerThread = new HandlerThread("CameraHandlerThread");
			handlerThread.start();
			cameraHandler = new Handler(handlerThread.getLooper());
		}
		Log.d(LOG_TAG, "startCamera(" + id + "): " + (waitForPermission ? "waiting" : "proceeding"));
		if (waitForPermission) {
			return;
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
		choosePictureResolution();

		camera.startPreview();
	}

	private void choosePictureResolution() {

		List<Camera.Size> supportedSizes;
		Camera.Parameters params = camera.getParameters();

		supportedSizes = params.getSupportedPreviewSizes();
		for (Camera.Size sz : supportedSizes) {
			Log.d(LOG_TAG, "supportedPreviewSizes " + sz.width + "x" + sz.height);
		}
		params.setPreviewSize(supportedSizes.get(0).width, supportedSizes.get(0).height);

		supportedSizes = params.getSupportedVideoSizes();
		for (Camera.Size sz : supportedSizes) {
			Log.d(LOG_TAG, "supportedVideoSizes " + sz.width + "x" + sz.height);
		}

		supportedSizes = params.getSupportedPictureSizes();
		for (Camera.Size sz : supportedSizes) {
			Log.d(LOG_TAG, "supportedPictureSizes " + sz.width + "x" + sz.height);
		}
		params.setPictureSize(supportedSizes.get(0).width, supportedSizes.get(0).height);

		Log.d(LOG_TAG, "current preview size " + params.getPreviewSize().width + "x" + params.getPreviewSize().height);
		Log.d(LOG_TAG, "current picture size " + params.getPictureSize().width + "x" + params.getPictureSize().height);
		// TODO: choose the best preview & picture size, and also fit the surface aspect ratio to preview aspect ratio
		camera.setParameters(params);
	}

	private static Camera openCamera(int id) {
		Log.d(LOG_TAG, "opening camera " + id);
		Camera camera = null;
		try {
			camera = Camera.open(id);
			Log.d(LOG_TAG, "opened camera " + id);
		} catch (Exception e) {
			e.printStackTrace();
			if (camera != null) {
				camera.release();
			}
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
