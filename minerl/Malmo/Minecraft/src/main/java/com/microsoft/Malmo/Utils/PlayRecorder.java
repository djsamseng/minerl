package com.microsoft.Malmo.Utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.Malmo.Client.FakeKeyboard;
import com.microsoft.Malmo.Client.FakeMouse;
import com.microsoft.Malmo.MissionHandlers.VideoProducerImplementation;
import com.microsoft.Malmo.Schemas.VideoProducer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import nu.pattern.OpenCV;
import org.lwjgl.BufferUtils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.opencv.core.CvType.CV_8UC3;


public class PlayRecorder {
    private String prefix;
    private VideoProducerImplementation videoProducer = new VideoProducerImplementation();
    private FileWriter actionsWriter;
    private int tickCounter;
    private VideoWriter videoWriter;
    private int width = 640;
    private int height = 360;
    private int fps = 20;
    private boolean recording = false;
    private boolean markTicks = false;


    public PlayRecorder(String prefix) {
        this.prefix = prefix;
    }

    public void start() {
        OpenCV.loadShared();
        VideoProducer videoParams = new VideoProducer();
        videoParams.setWidth(width);
        videoParams.setHeight(height);
        videoParams.setWantDepth(false);
        videoProducer.parseParameters(videoParams);
        videoProducer.prepare(null);
        tickCounter = 0;
        String filename = this.prefix + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        try {
            videoWriter = new VideoWriter();
            videoWriter.open(filename + ".mp4", VideoWriter.fourcc('m','p', '4', 'v'), fps, new Size(width, height), true) ;
            if (!videoWriter.isOpened()) {
                throw new IllegalArgumentException("VideoWriter was not opened");
            }
            videoWriter.write(new Mat(height, width, CV_8UC3));
            actionsWriter = new FileWriter(filename + ".jsonl", true);
            recording = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void recordTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || !player.isEntityAlive()) {
            if (recording) {
                finish();
            }
            return;
        }
        if (!recording) {
            start();
        }
        if (!mc.isGamePaused) {
            recordTickImpl();
        } else {
            FakeKeyboard.getState();
            FakeMouse.getState();
        }
    }

    private void recordTickImpl() {
        ByteBuffer imgBuffer = BufferUtils.createByteBuffer(this.videoProducer.getRequiredBufferSize());
        videoProducer.getFrame(null, imgBuffer);
        try {
            Mat frame = new Mat(height, width, CV_8UC3, imgBuffer);
            Mat flipped = new Mat(height, width, CV_8UC3);
            Core.flip(frame, flipped, 0);
            Imgproc.cvtColor(flipped, frame, Imgproc.COLOR_RGB2BGR);
            if (markTicks) {
                Imgproc.putText(frame, "tick " + tickCounter, new Point(10, 30), Core.FONT_HERSHEY_COMPLEX, 1, new Scalar(255, 255, 255), 1);
            }
            videoWriter.write(frame);

            JsonElement mouseState = FakeMouse.getState();
            JsonElement keyboardState = FakeKeyboard.getState();
            JsonObject actions = new JsonObject();
            actions.add("mouse", mouseState);
            actions.add("keyboard", keyboardState);
            actions.addProperty("tick", tickCounter + 1);
            actionsWriter.write(actions.toString());
            actionsWriter.write("\n");
            tickCounter += 1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void finish() {
        try {
            recording = false;
            actionsWriter.close();
            videoWriter.release();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}