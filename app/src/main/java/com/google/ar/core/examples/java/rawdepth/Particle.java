package com.google.ar.core.examples.java.rawdepth;

public class Particle {
    private float x;
    private float y;
    private float z;
    private int r;
    private int g;
    private int b;

    Particle(float x, float y, float z, int r, int g, int b){
        this.x = x;
        this.y = y;
        this.z = z;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public float getX(){
        return x;
    }
    public float getY(){
        return y;
    }
    public float getZ(){
        return z;
    }

    public int getR(){
        return r;
    }
    public int getG(){
        return g;
    }
    public int getB(){
        return b;
    }

}
