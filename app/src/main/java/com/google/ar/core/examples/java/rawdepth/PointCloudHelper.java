/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.rawdepth;

import static com.google.ar.core.examples.java.rawdepth.Renderer.frameData;
import static com.google.ar.core.examples.java.rawdepth.Renderer.particleData;

import android.media.Image;
import android.media.Image.Plane;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

/**
 * Static utilities for depth data transformations.
 */
public final class PointCloudHelper {

    private static float minConfidence = 0.1f;

    private PointCloudHelper() {}

    /**
     * Creates a linear buffer of 3D point positions in the world space and the corresponding
     * confidence values and RGB color values corresponding to the values in the depth image.
     * Pixels with the depth value equal to zero are not included in the output.
     */
    public static void convertImageToDepthAndColors(Image cameraImage, Image depthImage, Image confidenceImage,
                                                    Frame frame, int pointLimit) {
        /**
         *  Common Property
         *
         *  To transform 2D depth pixels into 3D points we retrieve the intrinsic camera parameters
         *  corresponding to the depth image. See more information about the depth values at
         *  https://developers.google.com/ar/develop/java/depth/overview#understand-depth-values.
         */
        CameraIntrinsics intrinsics = frame.getCamera().getTextureIntrinsics();

        Plane depthImagePlane = depthImage.getPlanes()[0];
        ShortBuffer depthBuffer =
                depthImagePlane.getBuffer().order(ByteOrder.nativeOrder()).asShortBuffer();

        Plane confidenceImagePlane = confidenceImage.getPlanes()[0];
        ByteBuffer confidenceBuffer = confidenceImagePlane.getBuffer().order(ByteOrder.nativeOrder());

        /**
         *  Position property
         *
         *  To transform 2D depth pixels into 3D points we retrieve the intrinsic camera parameters
         *  corresponding to the depth image. See more information about the depth values at
         *  https://developers.google.com/ar/develop/java/depth/overview#understand-depth-values.
         */
        int[] intrinsicsDimensions = intrinsics.getImageDimensions();
        int depthWidth = depthImage.getWidth();
        int depthHeight = depthImage.getHeight();
        float fx = intrinsics.getFocalLength()[0] * depthWidth / intrinsicsDimensions[0];
        float fy = intrinsics.getFocalLength()[1] * depthHeight / intrinsicsDimensions[1];
        float cx =
                intrinsics.getPrincipalPoint()[0] * depthWidth / intrinsicsDimensions[0];
        float cy =
                intrinsics.getPrincipalPoint()[1] * depthHeight / intrinsicsDimensions[1];

        /**
         *   Color property
         */
        int colorWidth = cameraImage.getWidth();
        int colorHeight = cameraImage.getHeight();
        Plane imagePlaneY = cameraImage.getPlanes()[0];
        Plane imagePlaneU = cameraImage.getPlanes()[1];
        Plane imagePlaneV = cameraImage.getPlanes()[2];
        int rowStrideY = imagePlaneY.getRowStride();
        int rowStrideU = imagePlaneU.getRowStride();
        int rowStrideV = imagePlaneV.getRowStride();
        int pixelStrideY = imagePlaneY.getPixelStride();
        int pixelStrideU = imagePlaneU.getPixelStride();
        int pixelStrideV = imagePlaneV.getPixelStride();
        ByteBuffer colorBufferY = imagePlaneY.getBuffer();
        ByteBuffer colorBufferU = imagePlaneU.getBuffer();
        ByteBuffer colorBufferV = imagePlaneV.getBuffer();

        FloatBuffer imageCoords = getImageCoordinatesForFullTexture(frame);

        // The first CPU image row overlapping with the depth image region.
        int colorMinY = Math.round(imageCoords.get(1));
        // The last CPU image row overlapping with the depth image region.
        int colorMaxY = Math.round(imageCoords.get(3));
        int colorRegionHeight = colorMaxY - colorMinY;

        /**
         *  Position & color -> Buffer
         *
         *  Allocate the destination point buffer. If the number of depth pixels is larger than
         *  `pointLimit` we do uniform image subsampling. Alternatively we could reduce the number of
         *  points based on depth confidence at this stage.
         */
        int step = calculateImageSubsamplingStep(depthWidth, depthHeight, pointLimit);

        // allocate points & color FloatBuffer
        FloatBuffer points =
                FloatBuffer.allocate(
                        depthWidth / step * depthHeight / step * Renderer.POSITION_FLOATS_PER_POINT);

        FloatBuffer colors =
                FloatBuffer.allocate(
                        depthWidth / step * depthHeight / step * Renderer.COLOR_FLOATS_PER_POINT);

        float rgb[] = new float[3];

        for (int y = 0; y < depthHeight; y += step) {
            for (int x = 0; x < depthWidth; x += step) {
                // Depth images are tightly packed, so it's OK to not use row and pixel strides.
                int depthMillimeters = depthBuffer.get(y * depthWidth + x); // Depth image pixels are in mm.

                // Depth confidence value for this pixel, stored as an unsigned byte in range [0, 255].
                byte confidencePixelValue =
                        confidenceBuffer.get(
                                y * confidenceImagePlane.getRowStride()
                                        + x * confidenceImagePlane.getPixelStride());
                // Normalize depth confidence to [0.0, 1.0] float range.
                float confidenceNormalized = ((float) (confidencePixelValue & 0xff)) / 255.0f;

                if (depthMillimeters == 0 || confidenceNormalized < minConfidence) {
                    // A pixel that has a value of zero has a missing depth estimate at this location.
                    continue;
                }

                float depthMeters = depthMillimeters / 1000.0f;

                float _x = depthMeters * (x - cx) / fx;
                float _y = depthMeters * (cy - y) / fy;
                float _z = -depthMeters;

                float[] worldCoordinates = new float[4];
                worldCoordinates[0] = _x;
                worldCoordinates[1] = _y;
                worldCoordinates[2] = _z;
                worldCoordinates[3] = 1;

                Pose cameraPose = frame.getCamera().getPose();
                /**
                 *   Camera Matrix
                 *   0   4   8   12
                 *   1   5   9   13
                 *   2   6  10   14
                 *   3   7  11   15
                 *
                 *   cameraMatrix[0], cameraMatrix[4], cameraMatrix[8]
                 *   Represent the x-axis of the camera's coordinate system in the world coordinate system.
                 *
                 *   cameraMatrix[1], cameraMatrix[5], cameraMatrix[9]
                 *   Represent the y-axis of the camera's coordinate system in the world coordinate system.
                 *
                 *   cameraMatrix[2], cameraMatrix[6], cameraMatrix[10]
                 *   Represent the z-axis of the camera's coordinate system in the world coordinate system.
                 *
                 *   cameraMatrix[3], cameraMatrix[7], cameraMatrix[11]
                 *   Represent the position of the camera in the world coordinate system.
                 *
                 *   cameraMatrix[12], cameraMatrix[13], cameraMatrix[14], cameraMatrix[15]
                 *   Represent a homogeneous transformation that is applied to the camera coordinate system. Typically, these values are (0, 0, 0, 1).
                 */
                float[] cameraMatrix = new float[16];
                cameraPose.toMatrix(cameraMatrix, 0);

                float[] worldCoordinatesInCameraSpace = new float[4];
                Matrix.multiplyMV(worldCoordinatesInCameraSpace, 0, cameraMatrix, 0, worldCoordinates, 0);

                float[] worldCoordinatesInWorldSpace = new float[3];
                worldCoordinatesInWorldSpace[0] = worldCoordinatesInCameraSpace[0] / worldCoordinatesInCameraSpace[3];
                worldCoordinatesInWorldSpace[1] = worldCoordinatesInCameraSpace[1] / worldCoordinatesInCameraSpace[3];
                worldCoordinatesInWorldSpace[2] = worldCoordinatesInCameraSpace[2] / worldCoordinatesInCameraSpace[3];

                points.put(_x); points.put(_y); points.put(_z);
                points.put(confidenceNormalized); // Confidence

                // Retrieve the color at this point.
                int colorX = x * colorWidth / depthWidth;
                int colorY = colorMinY + y * colorRegionHeight / depthHeight;
                int colorHalfX = colorX / 2;
                int colorHalfY = colorY / 2;

                // Each channel value is an unsigned byte, so we need to apply `0xff` to convert the sign.
                int channelValueY = colorBufferY.get(colorY * rowStrideY + colorX * pixelStrideY) & 0xff;
                int channelValueU =
                        colorBufferU.get(colorHalfY * rowStrideU + colorHalfX * pixelStrideU) & 0xff;
                int channelValueV =
                        colorBufferV.get(colorHalfY * rowStrideV + colorHalfX * pixelStrideV) & 0xff;

                convertYuvToRgb(channelValueY, channelValueU, channelValueV, rgb);
                colors.put(rgb[0]); colors.put(rgb[1]); colors.put(rgb[2]);

                int rIntValue = floatToUnsignedInt(rgb[0]);
                int gIntValue = floatToUnsignedInt(rgb[1]);
                int bIntValue = floatToUnsignedInt(rgb[2]);

                //particleData.add(new Particle(_x, _y, _z, rIntValue, gIntValue, bIntValue));
                particleData.add(new Particle(worldCoordinatesInWorldSpace[0], worldCoordinatesInWorldSpace[1], worldCoordinatesInWorldSpace[2], rIntValue, gIntValue, bIntValue));
            }
        }

        points.rewind();
        colors.rewind();
        frameData.add(new FrameData(points, colors));
    }

    /**
     * Calculates the CPU image region that corresponds to the area covered by the depth image.
     */
    public static FloatBuffer getImageCoordinatesForFullTexture(Frame frame) {
        FloatBuffer textureCoords =
                ByteBuffer.allocateDirect(TEXTURE_COORDS.length * Float.SIZE)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(TEXTURE_COORDS);
        textureCoords.position(0);
        FloatBuffer imageCoords =
                ByteBuffer.allocateDirect(TEXTURE_COORDS.length * Float.SIZE)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        frame.transformCoordinates2d(
                Coordinates2d.TEXTURE_NORMALIZED, textureCoords, Coordinates2d.IMAGE_PIXELS, imageCoords);
        return imageCoords;
    }

    /**
     * Returs the increment in rows and columns to sample the image n times.
     */
    private static int calculateImageSubsamplingStep(int imageWidth, int imageHeight, int n) {
        return (int) Math.ceil(Math.sqrt((float) imageWidth * imageHeight / n));
    }

    /**
     * Converts a YUV color value into RGB. Input YUV values are expected in the range [0, 255].
     * Output RGB values are in the range [0.0, 1.0].
     */
    private static void convertYuvToRgb(int yInt, int uInt, int vInt, float[] rgb) {
        // See https://en.wikipedia.org/wiki/YUV.
        float yFloat = yInt / 255.0f; // Range [0.0, 1.0].
        float uFloat = uInt * 0.872f / 255.0f - 0.436f; // Range [-0.436, 0.436].
        float vFloat = vInt * 1.230f / 255.0f - 0.615f; // Range [-0.615, 0.615].
        rgb[0] = clamp(yFloat + 1.13983f * vFloat);
        rgb[1] = clamp(yFloat - 0.39465f * uFloat - 0.58060f * vFloat);
        rgb[2] = clamp(yFloat + 2.03211f * uFloat);
    }

    /**
     * Clamps the value to [0, 1] range (inclusive).
     *
     * <p>If the value passed in is between 0 and 1, then it is returned unchanged.
     *
     * <p>If the value passed in is less than 0, 0 is returned.
     *
     * <p>If the value passed in is greater than 1, 1 is returned.
     */
    private static float clamp(float val) {
        return Math.max(0.0f, Math.min(1.0f, val));
    }

    private static final float[] TEXTURE_COORDS =
            new float[]{
                    0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
            };

    private static int floatToUnsignedInt(float floatValue) {
        int intValue = (int) (floatValue * 255);
        intValue = intValue < 0 ? 0 : intValue;
        intValue = intValue > 255 ? 255 : intValue;
        return intValue;
    }
}
