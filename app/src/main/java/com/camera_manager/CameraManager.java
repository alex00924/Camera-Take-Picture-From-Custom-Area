package com.camera_manager;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.content.ContentValues.TAG;

/** A basic Camera preview class */
public class CameraManager extends SurfaceView  implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;

    public Camera getCamera() {
        return mCamera;
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    public Camera getCameraInstance(int nIdx) {
        Camera c = null;
        try {
            c = Camera.open(nIdx); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            Toast.makeText(getContext(), "You have not camera or your camera is used in other app.", Toast.LENGTH_SHORT).show();
        }
        return c; // returns null if camera is unavailable
    }

    private void openCamera() {
        // Create an instance of Camera
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mCamera = getCameraInstance(i);
                }
            }
        }
        if (mCamera == null)
            return;
        Camera.Parameters parameters = mCamera.getParameters();
        Point screenResolution = new Point(this.getWidth(), this.getHeight());
        Point cameraResolution = findBestPreviewSizeValue(parameters, screenResolution);
        parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
        mCamera.setParameters(parameters);
        mCamera.setDisplayOrientation(90);
    }
    private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    private static final int MAX_PREVIEW_PIXELS = 800 * 600; // more than large/HD screen

    private Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {

        // Sort by size, descending
        List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(parameters.getSupportedPreviewSizes());
        Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        if (Log.isLoggable(TAG, Log.INFO)) {
            StringBuilder previewSizesString = new StringBuilder();
            for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
                previewSizesString.append(supportedPreviewSize.width).append('x')
                        .append(supportedPreviewSize.height).append(' ');
            }
            Log.i(TAG, "Supported preview sizes: " + previewSizesString);
        }

        Point bestSize = null;
        float screenAspectRatio = (float) screenResolution.x / (float) screenResolution.y;

        float diff = Float.POSITIVE_INFINITY;
        for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;
            int pixels = realWidth * realHeight;
            if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
                continue;
            }
            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                Point exactPoint = new Point(realWidth, realHeight);
                Log.i(TAG, "Found preview size exactly matching screen size: " + exactPoint);
                return exactPoint;
            }
            float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
            float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            if (newDiff < diff) {
                bestSize = new Point(realWidth, realHeight);
                diff = newDiff;
            }
        }

        if (bestSize == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            bestSize = new Point(defaultSize.width, defaultSize.height);
            Log.i(TAG, "No suitable preview sizes, using default: " + bestSize);
        }

        Log.i(TAG, "Found best approximate preview size: " + bestSize);
        return bestSize;
    }


    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    public CameraManager(Context context) {
        super(context);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            openCamera();
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
        releaseCamera();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null || mCamera == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    private Rect framingRect;
    private static final int MIN_FRAME_WIDTH = 20; // originally 240
    private static final int MIN_FRAME_HEIGHT = 50; // originally 240
    private static final int MAX_FRAME_WIDTH = 800; // originally 480
    private static final int MAX_FRAME_HEIGHT = 1200; // originally 360

    public Rect getImageRect()
    {
        Point screenResolution = new Point(this.getWidth(), this.getHeight());
        Camera.Size totalSize = mCamera.getParameters().getPictureSize();
        float fRatioX = (float) totalSize.height / (float) screenResolution.x;
        float fRatioY = (float) totalSize.width / (float) screenResolution.y;

        float fRatio = Math.max(fRatioX, fRatioY);

        Rect rcRes = new Rect(
                (int)(framingRect.left * fRatioX),
                (int)(framingRect.top * fRatioY),
                (int)(framingRect.right * fRatioX),
                (int)(framingRect.bottom * fRatioY) );
        return rcRes;
    }

    /**
     * Calculates the framing rect which the UI should draw to show the user where to place the
     * barcode. This target helps with alignment as well as forces the user to hold the device
     * far enough away to ensure the image will be in focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    public synchronized Rect getFramingRect() {
        if (framingRect == null) {
            if (mCamera == null) {
                return null;
            }
            Point screenResolution = new Point(this.getWidth(), this.getHeight());

            if (screenResolution == null) {
                // Called early, before init even finished
                return null;
            }
            int width = screenResolution.x * 3/5;
            if (width < MIN_FRAME_WIDTH) {
                width = MIN_FRAME_WIDTH;
            } else if (width > MAX_FRAME_WIDTH) {
                width = MAX_FRAME_WIDTH;
            }
            int height = screenResolution.y * 1/5;
            if (height < MIN_FRAME_HEIGHT) {
                height = MIN_FRAME_HEIGHT;
            } else if (height > MAX_FRAME_HEIGHT) {
                height = MAX_FRAME_HEIGHT;
            }
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
        }
        return framingRect;
    }

    /**
     * Changes the size of the framing rect.
     *
     * @param deltaWidth Number of pixels to adjust the width
     * @param deltaHeight Number of pixels to adjust the height
     */
    public synchronized void adjustFramingRect(int deltaWidth, int deltaHeight) {
        Point screenResolution = new Point(this.getWidth(), this.getHeight());

        // Set maximum and minimum sizes
        if ((framingRect.width() + deltaWidth > screenResolution.x - 4) || (framingRect.width() + deltaWidth < 50)) {
            deltaWidth = 0;
        }
        if ((framingRect.height() + deltaHeight > screenResolution.y - 4) || (framingRect.height() + deltaHeight < 50)) {
            deltaHeight = 0;
        }

        int newWidth = framingRect.width() + deltaWidth;
        int newHeight = framingRect.height() + deltaHeight;
        int leftOffset = (screenResolution.x - newWidth) / 2;
        int topOffset = (screenResolution.y - newHeight) / 2;
        framingRect = new Rect(leftOffset, topOffset, leftOffset + newWidth, topOffset + newHeight);
    }
}