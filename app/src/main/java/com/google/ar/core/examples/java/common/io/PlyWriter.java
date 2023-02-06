package com.google.ar.core.examples.java.common.io;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.ar.core.examples.java.rawdepth.Particle;

public class PlyWriter extends AsyncTask<Void, Void, Void> {

    private Context context;
    private ArrayList<Particle> particleData;

    public PlyWriter(Context context, ArrayList<Particle> particleData) {
        this.context = context;
        this.particleData = particleData;
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            int vertexCount = particleData.size();
            File dir = context.getFilesDir();

            Calendar cal = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmSS");
            String time = dateFormat.format(cal.getTime());

            File plyFile = new File(dir, "pointcloud"+time+".ply");
            FileOutputStream fileOutputStream = new FileOutputStream(plyFile);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);

            // Write header
            outputStreamWriter.write("ply\n");
            outputStreamWriter.write("format ascii 1.0\n");
            outputStreamWriter.write("element vertex " + vertexCount + "\n");
            outputStreamWriter.write("property float x\n");
            outputStreamWriter.write("property float y\n");
            outputStreamWriter.write("property float z\n");
            outputStreamWriter.write("property uchar red\n");
            outputStreamWriter.write("property uchar green\n");
            outputStreamWriter.write("property uchar blue\n");
            outputStreamWriter.write("property uchar alpha\n");
            outputStreamWriter.write("element face 0\n");
            outputStreamWriter.write("property list uchar int vertex_indices\n");
            outputStreamWriter.write("end_header\n");

            // Write vertex data
            for (int i = 0; i < vertexCount; i++) {
                Particle particle = particleData.get(i);
                float x = particle.getX();
                float y = particle.getY();
                float z = particle.getZ();
                int red = particle.getR();
                int green = particle.getG();
                int blue = particle.getB();
                int alpha = 255;
                outputStreamWriter.write(x + " " + y + " " + z + " " + red + " " + green + " " + blue + " " + alpha + "\n");
            }

            outputStreamWriter.flush();
            outputStreamWriter.close();
            fileOutputStream.flush();
            fileOutputStream.close();
            Log.e("TEST", "파일 생성 끝");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}