package com.google.ar.core.examples.java.rawdepth;

import java.nio.FloatBuffer;

public class FrameData {
    FloatBuffer points;
    FloatBuffer colors;

    FrameData(FloatBuffer points, FloatBuffer colors){
        this.points = points;
        this.colors = colors;
    }
}
