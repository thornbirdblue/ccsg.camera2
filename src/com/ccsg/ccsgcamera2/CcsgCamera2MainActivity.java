package com.ccsg.ccsgcamera2;

import java.util.Arrays;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.InputConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.util.Size;
import android.graphics.ImageFormat;
import android.media.ImageReader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.media.Image;
import android.media.ImageWriter;

import android.os.SystemClock;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ListIterator;

import android.os.Handler;
import android.os.HandlerThread;

import android.view.MotionEvent;
import android.view.View;
import android.view.KeyEvent;


public class CcsgCamera2MainActivity extends Activity {
	private final String TAG = "CcsgCamera2";
	private CameraManager CM;
	private CDStateCallback CDStateCB;
	private CameraDevice	mCameraDevice;
	private SurfaceTexture	mSurfaceTexture;
	private TextureView		mTextureView;
	private CSStateCallback CSStateCB;
	private CameraCaptureSession mCameraCaptureSession;
	private CaptureRequest.Builder Builder,bl;
	private Surface mSurface;

	private CameraCharacteristics mCameraCharacteristics;

	private Size mLargestYuvSize;
	private Size mLargestJpegSize;
	private Size mRawSize;
	private Integer mRawFormat;

    private ImageReader mYuvImageReader;
    private ImageReader mJpegImageReader;
    private ImageReader mRawImageReader;

    private Image mYuvLastReceivedImage = null;
    private Image mRawLastReceivedImage = null;

    private Boolean mSessionYuvReprocessSupport = false;
    private TotalCaptureResult mLastTotalCaptureResult;

    private YUVStateCallback mCaptureCallback;

    // Used for saving JPEGs.
    private HandlerThread mUtilityThread;
    private Handler mUtilityHandler;

    //Reprocess
    ImageWriter mImageWriter;

    ImageReader.OnImageAvailableListener mYuvImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned YUV");
                        return;
                    }
                    Image.Plane[] plane = img.getPlanes();
                    final byte[][] DataBuf = new byte[3][];
                    for(int i=0;i<plane.length;i++) {
                        final ByteBuffer buffer = plane[i].getBuffer();
                        Log.d(TAG,"ByteBuffer: "+buffer.capacity());
                        if (buffer.hasArray()) {
                            DataBuf[i] = buffer.array();
                        } else {
                            DataBuf[i] = new byte[buffer.capacity()];
                            buffer.get(DataBuf[i]);
                        }
                    }
//                    saveFile(DataBuf[0],img.getWidth(), img.getHeight(),1);
//                    saveFile(DataBuf[1],img.getWidth(), img.getHeight(),1);
/*
                    final byte[] saveData = new byte[DataBuf[0].length+DataBuf[1].length];
                    System.arraycopy(DataBuf[0],0,saveData,0,DataBuf[0].length);
                    System.arraycopy(DataBuf[1],0,saveData,DataBuf[0].length,DataBuf[1].length);
                    Log.d(TAG,"Date len:"+DataBuf[0].length+" "+DataBuf[1].length);

                    mUtilityHandler.post(new Runnable() {
                                             @Override
                                             public void run() {
                                                 saveFile(saveData, mLargestYuvSize.getWidth(), mLargestYuvSize.getHeight(), 1);
                                             }
                                         });
*/
                    Log.d(TAG,"YuvImageListener RECIEVE img!!!");

                    if (mYuvLastReceivedImage != null) {
                        mYuvLastReceivedImage.close();
                    }
                    mYuvLastReceivedImage = img;
                   }
            };

    ImageReader.OnImageAvailableListener mJpegImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.d(TAG,"mJpegImageListener RECIEVE img!!!");
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned JPEG");
                        return;
                    }
                    Image.Plane plane0 = img.getPlanes()[0];
                    final ByteBuffer buffer = plane0.getBuffer();

                    final byte[] jpegBuf;
                    if (buffer.hasArray()) {
                        jpegBuf = buffer.array();
                    } else {
                        jpegBuf = new byte[buffer.capacity()];
                        buffer.get(jpegBuf);
                    }
                    saveFile(jpegBuf,img.getWidth(), img.getHeight(),0);
                    img.close();
                    }
            };

    ImageReader.OnImageAvailableListener mRawImageListener =
                new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned YUV1");
                        return;
                    }

                    if (mRawLastReceivedImage != null) {
                        mRawLastReceivedImage.close();
                    }

                    Image.Plane plane0 = img.getPlanes()[0];
                    final ByteBuffer buffer = plane0.getBuffer();

                    final byte[] DateBuf;
                    if (buffer.hasArray()) {
                        DateBuf = buffer.array();
                    } else {
                        DateBuf = new byte[buffer.capacity()];
                        buffer.get(DateBuf);
                    }

                    saveFile(DateBuf,img.getWidth(), img.getHeight(),2);

                    mRawLastReceivedImage = img;
                    Log.d(TAG,"mRawImageListener RECIEVE img!!!");
                }
        };

    private void saveFile(byte[] Data,int w,int h,int type){
        String filename = "";
        String filetype = "";
        try {
            switch(type)
            {
                case 0:
                    filetype="JPG";
                    break;
                case 1:
                    filetype="yuv";
                    break;
                case 2:
                    filetype="raw";
                    break;
                default:
                    Log.w(TAG,"unknow file type");
            }

            filename = String.format("/sdcard/DCIM/Camera/SNAP_%dx%d_%d.%s", w,h,System.currentTimeMillis(),filetype);
            File file;
            while (true) {
                file = new File(filename);
                if (file.createNewFile()) {
                    break;
                }
            }

            long t0 = SystemClock.uptimeMillis();
            OutputStream os = new FileOutputStream(file);
            os.write(Data);
            os.flush();
            os.close();
            long t1 = SystemClock.uptimeMillis();

            Log.d(TAG, String.format("Wrote data(%d) %d bytes as %s in %.3f seconds;%s",type,
                    Data.length, file, (t1 - t0) * 0.001,filename));
        } catch (IOException e) {
            Log.e(TAG, "Error creating new file: ", e);
        }
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CM = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
		CDStateCB = new CDStateCallback();
        mCaptureCallback = new YUVStateCallback();

		try {
			Log.d(TAG,"openCamera begin!!!");
			CM.openCamera("0", CDStateCB, null);
			mCameraCharacteristics = CM.getCameraCharacteristics("0");
            printCameraCharacteristics();
            InitializeAllTheThings();
            Log.d(TAG,"openCamera end!!!");
		} catch (CameraAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Log.d(TAG,"new SurfaceTexture begin!!!");
		mSurfaceTexture = new SurfaceTexture(10);
		mSurfaceTexture.setDefaultBufferSize(630,480);
		Log.d(TAG,"new SurfaceTexture end!!!");
		
		CSStateCB = new CSStateCallback();

		Log.d(TAG,"new TextureView begin!!!");
		mTextureView = new TextureView(this);
		Log.d(TAG,"new TextureView end!!!");
		
		mSurfaceTexture.detachFromGLContext();

		mTextureView.setSurfaceTexture(mSurfaceTexture);

        mUtilityThread = new HandlerThread("UtilityThread");
        mUtilityThread.start();
        mUtilityHandler = new Handler(mUtilityThread.getLooper());

        setContentView(mTextureView);
	}

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if(event.getAction() == KeyEvent.ACTION_DOWN)
        {
            Log.d(TAG,"lcj onKeyDown: "+keyCode);
            if(keyCode == KeyEvent.KEYCODE_VOLUME_UP ) {
                takeYUVPicture();
                return true;
            }
            else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                ReprocessPicture();
                return true;
            }

//            takeMFicture();
//            takeRAWPicture();
//            takeJpegPicture();
        }
        return false;
    }


    private void startPreview(){
        try {
            Builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Builder.addTarget(mSurface);

        try {
            mCameraCaptureSession.setRepeatingRequest(Builder.build(),null,null);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void InitializeAllTheThings() {
        mJpegImageReader = ImageReader.newInstance(
                mLargestYuvSize.getWidth(),
                mLargestYuvSize.getHeight(),
                ImageFormat.JPEG,
                2);
        mJpegImageReader.setOnImageAvailableListener(mJpegImageListener,null);

        mYuvImageReader = ImageReader.newInstance(
                mLargestYuvSize.getWidth(),
                mLargestYuvSize.getHeight(),
                ImageFormat.YUV_420_888,
                8);

        mYuvImageReader.setOnImageAvailableListener(mYuvImageListener,null);

        mRawImageReader = ImageReader.newInstance(
                mRawSize.getWidth(),
                mRawSize.getHeight(),
                mRawFormat,                     //ImageFormat.RAW10
                8);
        mRawImageReader.setOnImageAvailableListener(mRawImageListener,null);

    }

	private void takeYUVPicture()
	{
		Log.d(TAG,"takeYUVPicture");
		try {
			bl = mCameraDevice.createCaptureRequest(mCameraDevice.TEMPLATE_STILL_CAPTURE);
		} catch (CameraAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        bl.addTarget(mYuvImageReader.getSurface());
        try {
			mCameraCaptureSession.capture(bl.build(),mCaptureCallback,null);
		} catch (CameraAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        Log.d(TAG,"takeYUVPicture------------------------END");
	}

     private void takeRAWPicture()
	{
        Log.d(TAG,"takeRAWPicture");
        try {
            bl = mCameraDevice.createCaptureRequest(mCameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        bl.addTarget(mRawImageReader.getSurface());
        try {
            mCameraCaptureSession.capture(bl.build(),null,null);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Log.d(TAG,"takeRAWPicture------------------------END");
	}
    private void takeJpegPicture()
    {
        Log.d(TAG,"takeJpegPicture");
        try {
            bl = mCameraDevice.createCaptureRequest(mCameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        bl.addTarget(mJpegImageReader.getSurface());
        try {
            mCameraCaptureSession.capture(bl.build(),null,null);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Log.d(TAG,"takeJpegPicture------------------------END");
    }
	private void takeMFicture()
	{
        Log.d(TAG,"takeMFicture");

        List<CaptureRequest> requests = new ArrayList<CaptureRequest>(2);
        for(int i=0;i<2;i++) {
            try {
                bl = mCameraDevice.createCaptureRequest(mCameraDevice.TEMPLATE_STILL_CAPTURE);
            } catch (CameraAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            bl.addTarget(mYuvImageReader.getSurface());
            requests.add(bl.build());
        }

        Log.d(TAG,"MF list len: "+requests.size());
        try {
            mCameraCaptureSession.captureBurst(requests,mCaptureCallback,null);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Log.d(TAG,"takeMFicture------------------------END");
	}

	private void ReprocessPicture()
	{
        Log.d(TAG,"ReprocessPicture");
        if(mYuvLastReceivedImage == null) {
            Log.w(TAG,"No Last YUV Image: Can't need to Reprocess!!!");
            return;
        }
        mImageWriter.queueInputImage(mYuvLastReceivedImage);

        try {
            bl = mCameraDevice.createReprocessCaptureRequest(mLastTotalCaptureResult);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        bl.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
        bl.addTarget(mJpegImageReader.getSurface());
        try {
            mCameraCaptureSession.capture(bl.build(),null,null);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Log.d(TAG,"ReprocessPicture------------------------END");
	}
	private Size returnLargestSize(Size[] sizes) {
		Size largestSize = null;
		int area = 0;
		for (int j = 0; j < sizes.length; j++) {
			if (sizes[j].getHeight() * sizes[j].getWidth() > area) {
				area = sizes[j].getHeight() * sizes[j].getWidth();
				largestSize = sizes[j];
			}
		}
		return largestSize;
	}

	private void printCameraCharacteristics()
	{
		StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
		int[] formats = map.getOutputFormats();
		long lowestStall = Long.MAX_VALUE;
		for (int i = 0; i < formats.length; i++) {
			Log.d(TAG,"format: "+formats[i]);
			if (formats[i] == ImageFormat.YUV_420_888) {
				mLargestYuvSize = returnLargestSize(map.getOutputSizes(formats[i]));
				Log.d(TAG,"YUV420: "+mLargestYuvSize.getWidth()+"x"+mLargestYuvSize.getHeight());
			}
			if (formats[i] == ImageFormat.JPEG) {
				mLargestJpegSize = returnLargestSize(map.getOutputSizes(formats[i]));
				Log.d(TAG,"JPEG: "+mLargestJpegSize.getWidth()+"x"+mLargestJpegSize.getHeight());
			}
			if (formats[i] == ImageFormat.RAW10 || formats[i] == ImageFormat.RAW_SENSOR) { // TODO: Add RAW12
				Size size = returnLargestSize(map.getOutputSizes(formats[i]));
				long stall = map.getOutputStallDuration(formats[i], size);
				if (stall < lowestStall) {
					mRawFormat = formats[i];
					mRawSize = size;
					lowestStall = stall;
					Log.d(TAG,"RAW("+mRawFormat+"): "+mRawSize.getWidth()+"x"+mRawSize.getHeight());
				}
			}
		}
        int[] caps = mCameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        for (int c: caps) {
            if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) mSessionYuvReprocessSupport=true;
        }
        Log.d(TAG,"YUV reprocess support: "+mSessionYuvReprocessSupport);

        int inputNum = mCameraCharacteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS);
        int outputNum = mCameraCharacteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC);
        Log.d(TAG,"Max output stream: "+outputNum+"\nMax input stream: "+inputNum);
	}

	public class CDStateCallback extends CameraDevice.StateCallback{
		public void onOpened(CameraDevice camera)
		{
			Log.d(TAG,"onOpened");
			mCameraDevice = camera;
			Log.d(TAG,"new mSurface begin!!!");
			mSurface = new Surface(mSurfaceTexture);
			Log.d(TAG,"new mSurface end!!!");

            List<Surface> outputSurfaces = new ArrayList<Surface>(3);
            outputSurfaces.add(mSurface);
            outputSurfaces.add(mYuvImageReader.getSurface());
            outputSurfaces.add(mRawImageReader.getSurface());
            outputSurfaces.add(mJpegImageReader.getSurface());

			try {
				Log.d(TAG,"createCaptureSession begin!!!");
                if(mSessionYuvReprocessSupport){
                    Log.d(TAG,"->createReprocessableCaptureSession");
                    InputConfiguration inputConfig = new InputConfiguration(mLargestYuvSize.getWidth(),
                            mLargestYuvSize.getHeight(), ImageFormat.YUV_420_888);
                    mCameraDevice.createReprocessableCaptureSession(inputConfig, outputSurfaces,
                            CSStateCB, null);
                }
                else {
                    Log.d(TAG,"->createCaptureSession");
                    mCameraDevice.createCaptureSession(outputSurfaces, CSStateCB, null);
                }
				Log.d(TAG,"createCaptureSession end!!!");
			} catch (CameraAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		public void onDisconnected(CameraDevice camera)
		{
			Log.d(TAG,"onDisconnected");
		}
		public void onError(CameraDevice camera, int error)
		{
			Log.d(TAG,"onError"+"error val is"+error);
		}
	}
	public class CSStateCallback extends CameraCaptureSession.StateCallback
	{
		public void onConfigured(CameraCaptureSession session)
		{			
			Log.d(TAG,"onConfigured");
			mCameraCaptureSession = session;
            startPreview();
		}
        public void onReady(CameraCaptureSession session) {
            Log.d(TAG,"CaptureSession onReady!!!");
            mImageWriter = ImageWriter.newInstance(session.getInputSurface(), 2);
        }
        public void onConfigureFailed(CameraCaptureSession session)
		{
			Log.d(TAG,"onConfigureFailed");
		}
	}

    public class YUVStateCallback extends CameraCaptureSession.CaptureCallback {
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            Log.d(TAG, "YUV Capture Complete!!!");
            mLastTotalCaptureResult = result;
        }
    }
}
