package com.camera_manager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.content.ContentValues.TAG;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class MainActivity extends AppCompatActivity  {

    private CameraManager mCameraManager;
    private ViewfinderView m_viewfinderView;
    private boolean m_bSafeCapture = true;

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            camera.startPreview();
            File pictureFile = Global.getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if ( pictureFile == null || data == null || data.length < 1 ){
                Log.d(TAG, "Error creating media file, check storage permissions");
                m_bSafeCapture = true;
                return;
            }

            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap finalBitmap = Global.getSubimage(bitmap, mCameraManager.getImageRect());
                FileOutputStream fos = new FileOutputStream(pictureFile);
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);

                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
            m_bSafeCapture = true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if( !checkCameraHardware(MainActivity.this) )
        {
            Toast.makeText(MainActivity.this, "There is no camera.", Toast.LENGTH_SHORT).show();
        };

        // Create our Preview view and set it as the content of our activity.
        mCameraManager = new CameraManager(this);
        FrameLayout preview = findViewById(R.id.layout_capture);
        preview.addView(mCameraManager);

        // Add a listener to the Capture button
        Button captureButton = findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        if( mCameraManager.getCamera() == null || !m_bSafeCapture)
                            return;
                        m_bSafeCapture = false;
                        // get an image from the camera
                        mCameraManager.getCamera().takePicture(null, null, mPicture);
                    }
                }
        );

        m_viewfinderView = findViewById(R.id.viewfinder_view);
        m_viewfinderView.setCameraManager(mCameraManager);
        m_viewfinderView.bringToFront();
        // Set listener to change the size of the viewfinder rectangle.
        m_viewfinderView.setOnTouchListener(new View.OnTouchListener() {
            int lastX = -1;
            int lastY = -1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = -1;
                        lastY = -1;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int currentX = (int) event.getX();
                        int currentY = (int) event.getY();

                        try {
                            Rect rect = mCameraManager.getFramingRect();

                            final int BUFFER = 50;
                            final int BIG_BUFFER = 60;
                            if (lastX >= 0) {
                                // Adjust the size of the viewfinder rectangle. Check if the touch event occurs in the corner areas first, because the regions overlap.
                                if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                                        && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                                    // Top left corner: adjust both top and left sides
                                    mCameraManager.adjustFramingRect(2 * (lastX - currentX), 2 * (lastY - currentY));
                                    m_viewfinderView.drawViewfinder();
                                } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER))
                                        && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                                    // Top right corner: adjust both top and right sides
                                    mCameraManager.adjustFramingRect(2 * (currentX - lastX), 2 * (lastY - currentY));
                                    m_viewfinderView.drawViewfinder();
                                } else if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                                        && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                                    // Bottom left corner: adjust both bottom and left sides
                                    mCameraManager.adjustFramingRect(2 * (lastX - currentX), 2 * (currentY - lastY));
                                    m_viewfinderView.drawViewfinder();
                                } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER))
                                        && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                                    // Bottom right corner: adjust both bottom and right sides
                                    mCameraManager.adjustFramingRect(2 * (currentX - lastX), 2 * (currentY - lastY));
                                    m_viewfinderView.drawViewfinder();
                                } else if (((currentX >= rect.left - BUFFER && currentX <= rect.left + BUFFER) || (lastX >= rect.left - BUFFER && lastX <= rect.left + BUFFER))
                                        && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                                    // Adjusting left side: event falls within BUFFER pixels of left side, and between top and bottom side limits
                                    mCameraManager.adjustFramingRect(2 * (lastX - currentX), 0);
                                    m_viewfinderView.drawViewfinder();
                                } else if (((currentX >= rect.right - BUFFER && currentX <= rect.right + BUFFER) || (lastX >= rect.right - BUFFER && lastX <= rect.right + BUFFER))
                                        && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                                    // Adjusting right side: event falls within BUFFER pixels of right side, and between top and bottom side limits
                                    mCameraManager.adjustFramingRect(2 * (currentX - lastX), 0);
                                    m_viewfinderView.drawViewfinder();
                                } else if (((currentY <= rect.top + BUFFER && currentY >= rect.top - BUFFER) || (lastY <= rect.top + BUFFER && lastY >= rect.top - BUFFER))
                                        && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                                    // Adjusting top side: event falls within BUFFER pixels of top side, and between left and right side limits
                                    mCameraManager.adjustFramingRect(0, 2 * (lastY - currentY));
                                    m_viewfinderView.drawViewfinder();
                                } else if (((currentY <= rect.bottom + BUFFER && currentY >= rect.bottom - BUFFER) || (lastY <= rect.bottom + BUFFER && lastY >= rect.bottom - BUFFER))
                                        && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                                    // Adjusting bottom side: event falls within BUFFER pixels of bottom side, and between left and right side limits
                                    mCameraManager.adjustFramingRect(0, 2 * (currentY - lastY));
                                    m_viewfinderView.drawViewfinder();
                                }
                            }
                        } catch (NullPointerException e) {
                        }
                        v.invalidate();
                        lastX = currentX;
                        lastY = currentY;
                        return true;
                    case MotionEvent.ACTION_UP:
                        lastX = -1;
                        lastY = -1;
                        return true;
                }
                return false;
            }
        });
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

}
